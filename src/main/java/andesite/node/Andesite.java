package andesite.node;

import andesite.node.config.Config;
import andesite.node.event.EventDispatcher;
import andesite.node.handler.RequestHandler;
import andesite.node.handler.RestHandler;
import andesite.node.handler.SingyeongHandler;
import andesite.node.player.Player;
import andesite.node.send.jdaa.JDASendFactory;
import andesite.node.send.nio.NioSendFactory;
import com.github.shredder121.asyncaudio.jda.AsyncPacketProviderFactory;
import com.sedmelluq.discord.lavaplayer.jdaudp.NativeAudioSendFactory;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.bandcamp.BandcampAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.beam.BeamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.twitch.TwitchStreamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.vimeo.VimeoAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.playback.NonAllocatingAudioFrameBuffer;
import io.vertx.core.Vertx;
import net.dv8tion.jda.core.audio.factory.IAudioSendFactory;
import org.slf4j.LoggerFactory;
import space.npstr.magma.MagmaApi;
import space.npstr.magma.events.api.WebSocketClosed;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class Andesite {
    private static final Map<String, Supplier<AudioSourceManager>> SOURCE_MANAGERS = Map.of(
            "bandcamp", BandcampAudioSourceManager::new,
            "beam", BeamAudioSourceManager::new,
            "http", HttpAudioSourceManager::new,
            "local", LocalAudioSourceManager::new,
            "soundcloud", SoundCloudAudioSourceManager::new,
            "twitch", TwitchStreamAudioSourceManager::new,
            "vimeo", VimeoAudioSourceManager::new,
            "youtube", YoutubeAudioSourceManager::new
    );

    private final Map<String, Map<String, Player>> players = new ConcurrentHashMap<>();
    private final AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
    private final EventDispatcher dispatcher = new EventDispatcher();
    private final Vertx vertx;
    private final Config config;
    private final IAudioSendFactory factory;
    private final MagmaApi magma;
    private final RequestHandler handler;

    private Andesite(@Nonnull Vertx vertx, @Nonnull Config config) {
        this.vertx = vertx;
        this.config = config;
        this.factory = createFactory(config);
        this.magma = MagmaApi.of(__ -> factory);
        SOURCE_MANAGERS.forEach((k, v) -> {
            if(config.getBoolean("source." + k, false)) {
                playerManager.registerSourceManager(v.get());
            }
        });
        this.handler = new RequestHandler(this);
        var audioConfig = playerManager.getConfiguration();
        audioConfig.setFilterHotSwapEnabled(true);
        if(config.getBoolean("send-system.non-allocating", false)) {
            audioConfig.setFrameBufferFactory(NonAllocatingAudioFrameBuffer::new);
        }
        magma.getEventStream().subscribe(event -> {
            if(event instanceof WebSocketClosed) {
                var e = (WebSocketClosed)event;
                dispatcher.onWebSocketClosed(
                        e.getMember().getUserId(),
                        e.getMember().getGuildId(),
                        e.getCloseCode(),
                        e.getReason(),
                        e.isByRemote()
                );
            }
        });
    }

    @Nonnull
    @CheckReturnValue
    public Vertx vertx() {
        return vertx;
    }

    @Nonnull
    @CheckReturnValue
    public Config config() {
        return config;
    }

    @Nonnull
    @CheckReturnValue
    public AudioPlayerManager audioPlayerManager() {
        return playerManager;
    }

    @Nonnull
    @CheckReturnValue
    public EventDispatcher dispatcher() {
        return dispatcher;
    }

    @Nonnull
    @CheckReturnValue
    public MagmaApi magma() {
        return magma;
    }

    @Nonnull
    @CheckReturnValue
    public Map<String, Player> playerMap(String userId) {
        return players.computeIfAbsent(userId, __ -> new ConcurrentHashMap<>());
    }

    @Nonnull
    @CheckReturnValue
    public RequestHandler requestHandler() {
        return handler;
    }

    @Nonnull
    @CheckReturnValue
    public Player getPlayer(@Nonnull String userId, @Nonnull String guildId) {
        return playerMap(userId).computeIfAbsent(guildId, __ -> {
            var player = new Player(this, guildId);
            dispatcher.onPlayerCreated(userId, guildId, player);
            return player;
        });
    }

    @Nullable
    @CheckReturnValue
    public Player getExistingPlayer(@Nonnull String userId, @Nonnull String guildId) {
        var map = players.get(userId);
        return map == null ? null : map.get(guildId);
    }

    @Nullable
    public Player removePlayer(@Nonnull String userId, @Nonnull String guildId) {
        var map = players.get(userId);
        if(map == null) return null;
        var player = map.remove(guildId);
        if(player != null) {
            dispatcher.onPlayerDestroyed(userId, guildId, player);
        }
        if(map.isEmpty()) {
            players.remove(userId, map);
        }
        return player;
    }

    public Stream<Player> allPlayers() {
        return players.values().stream().flatMap(m -> m.values().stream());
    }

    public static void main(String[] args) throws IOException {
        var andesite = new Andesite(Vertx.vertx(), Config.load());
        //NOTE: use the bitwise or operator, as it forces evaluation of all elements
        if(!(RestHandler.setup(andesite) | SingyeongHandler.setup(andesite))) {
            LoggerFactory.getLogger(Andesite.class).error("No handlers enabled, aborting");
            System.exit(-1);
        }
    }

    @Nonnull
    @CheckReturnValue
    private IAudioSendFactory createFactory(@Nonnull Config config) {
        IAudioSendFactory factory;
        switch(config.get("send-system.type", "nas")) {
            case "nas":
                factory = new NativeAudioSendFactory(config.getInt("send-system.nas-buffer", 400));
                break;
            case "jda":
                factory = new JDASendFactory();
                break;
            case "nio":
                factory = new NioSendFactory(vertx);
                break;
            default:
                throw new IllegalArgumentException("No send system with type " + config.get("send-system.type"));
        }
        if(config.getBoolean("send-system.async", false)) {
            factory = AsyncPacketProviderFactory.adapt(factory);
        }
        return factory;
    }
}
