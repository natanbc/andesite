package andesite.node;

import andesite.node.config.Config;
import andesite.node.event.EventBuffer;
import andesite.node.event.EventDispatcher;
import andesite.node.event.EventDispatcherImpl;
import andesite.node.handler.RequestHandler;
import andesite.node.handler.RestHandler;
import andesite.node.handler.SingyeongHandler;
import andesite.node.player.Player;
import andesite.node.plugin.PluginManager;
import andesite.node.provider.AsyncPacketProviderFactory;
import andesite.node.send.jdaa.JDASendFactory;
import andesite.node.send.nio.NioSendFactory;
import andesite.node.util.FilterUtil;
import andesite.node.util.Init;
import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.npstr.magma.MagmaApi;
import space.npstr.magma.events.api.WebSocketClosed;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.lang.ref.Cleaner;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Andesite implements NodeState {
    public static final Cleaner CLEANER = Cleaner.create(r -> {
        var t = new Thread(r, "Andesite-Cleaner");
        t.setDaemon(true);
        return t;
    });

    private static final Logger log = LoggerFactory.getLogger(Andesite.class);
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
    private static final Set<String> DISABLED_BY_DEFAULT = Set.of("http", "local");

    private final PluginManager pluginManager = new PluginManager(this);
    private final AtomicLong nextBufferId = new AtomicLong();
    private final Map<Long, EventBuffer> buffers = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Player>> players = new ConcurrentHashMap<>();
    private final AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
    private final AudioPlayerManager pcmPlayerManager = new DefaultAudioPlayerManager();
    private final EventDispatcherImpl dispatcher = new EventDispatcherImpl(this);
    private final Vertx vertx;
    private final Config config;
    private final IAudioSendFactory factory;
    private final MagmaApi magma;
    private final RequestHandler handler;
    private final Set<String> enabledSources;

    private Andesite(@Nonnull Vertx vertx, @Nonnull Config config) throws IOException {
        var plugins = new File("plugins").listFiles();
        if(plugins != null) {
            for(var f : plugins) {
                log.info("Loading plugins from {}", f);
                pluginManager.load(f);
            }
        }
        var extraPluginLocations = config.get("extra-plugins");
        if(extraPluginLocations != null) {
            for(var f : extraPluginLocations.split(",")) {
                log.info("Loading plugins from {}", f);
                pluginManager.load(new File(f));
            }
        }
        this.vertx = vertx;
        this.config = config;
        this.factory = createFactory(config);
        this.magma = MagmaApi.of(__ -> factory);
        this.handler = new RequestHandler(this);
        pluginManager.configurePlayerManager(playerManager);
        pluginManager.configurePlayerManager(pcmPlayerManager);
        pluginManager.registerListeners(dispatcher);
        this.enabledSources = SOURCE_MANAGERS.keySet().stream()
                .filter(key -> config.getBoolean("source." + key, !DISABLED_BY_DEFAULT.contains(key)))
                .peek(key -> playerManager.registerSourceManager(SOURCE_MANAGERS.get(key).get()))
                .peek(key -> pcmPlayerManager.registerSourceManager(SOURCE_MANAGERS.get(key).get()))
                .collect(Collectors.toSet());
        log.info("Enabled default sources: {}", enabledSources);
        //we need to set the cleanup to basically never run so mixer players aren't destroyed without need.
        playerManager.setPlayerCleanupThreshold(Long.MAX_VALUE);
        playerManager.getConfiguration().setFilterHotSwapEnabled(true);
        if(config.getBoolean("send-system.non-allocating", false)) {
            playerManager.getConfiguration().setFrameBufferFactory(NonAllocatingAudioFrameBuffer::new);
        }
        pcmPlayerManager.setPlayerCleanupThreshold(Long.MAX_VALUE);
        pcmPlayerManager.getConfiguration().setOutputFormat(StandardAudioDataFormats.DISCORD_PCM_S16_BE);
        pcmPlayerManager.getConfiguration().setFilterHotSwapEnabled(true);
        pcmPlayerManager.getConfiguration().setFrameBufferFactory(NonAllocatingAudioFrameBuffer::new);
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
    public PluginManager pluginManager() {
        return pluginManager;
    }

    @Nonnull
    @CheckReturnValue
    public MagmaApi magma() {
        return magma;
    }

    @Nonnull
    @CheckReturnValue
    public RequestHandler requestHandler() {
        return handler;
    }

    @Nonnull
    @CheckReturnValue
    public Set<String> enabledSources() {
        return enabledSources;
    }

    @CheckReturnValue
    public long nextConnectionId() {
        return nextBufferId.incrementAndGet();
    }

    @Nonnull
    @CheckReturnValue
    public EventBuffer createEventBuffer(long id) {
        var buffer = new EventBuffer();
        buffers.put(id, buffer);
        return buffer;
    }

    @Nullable
    public EventBuffer removeEventBuffer(long id) {
        return buffers.remove(id);
    }

    @Nonnull
    @CheckReturnValue
    @Override
    public Config config() {
        return config;
    }

    @Nonnull
    @CheckReturnValue
    @Override
    public Vertx vertx() {
        return vertx;
    }

    @Nonnull
    @CheckReturnValue
    @Override
    public AudioPlayerManager audioPlayerManager() {
        return playerManager;
    }

    @Nonnull
    @CheckReturnValue
    @Override
    public AudioPlayerManager pcmAudioPlayerManager() {
        return pcmPlayerManager;
    }

    @Nonnull
    @CheckReturnValue
    @Override
    public EventDispatcher dispatcher() {
        return dispatcher;
    }

    @Nonnull
    @CheckReturnValue
    @Override
    public Map<String, Player> playerMap(@Nonnull String userId) {
        return players.computeIfAbsent(userId, __ -> new ConcurrentHashMap<>());
    }

    @Nonnull
    @CheckReturnValue
    @Override
    public Player getPlayer(@Nonnull String userId, @Nonnull String guildId) {
        return playerMap(userId).computeIfAbsent(guildId, __ -> {
            var player = new Player(this, guildId, userId);
            dispatcher.onPlayerCreated(userId, guildId, player);
            return player;
        });
    }

    @Nullable
    @CheckReturnValue
    @Override
    public Player getExistingPlayer(@Nonnull String userId, @Nonnull String guildId) {
        var map = players.get(userId);
        return map == null ? null : map.get(guildId);
    }

    @Nullable
    @Override
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

    @Nonnull
    @CheckReturnValue
    @Override
    public Stream<Player> allPlayers() {
        return players.values().stream().flatMap(m -> m.values().stream());
    }

    public static void main(String[] args) throws IOException {
        log.info("Starting andesite version {}, commit {}", Version.VERSION, Version.COMMIT);
        var config = Config.load();
        Init.handleInit(config);
        var andesite = new Andesite(Vertx.vertx(), config);
        //NOTE: use the bitwise or operator, as it forces evaluation of all elements
        if(!(RestHandler.setup(andesite) | SingyeongHandler.setup(andesite))) {
            log.error("No handlers enabled, aborting");
            System.exit(-1);
        }
        log.info("Handlers: REST {}, WebSocket {}, Singyeong {}",
                config.getBoolean("transport.http.rest", true) ? "enabled" : "disabled",
                config.getBoolean("transport.http.ws", true) ? "enabled" : "disabled",
                config.getBoolean("transport.singyeong.enabled", false) ? "enabled" : "disabled"
        );
        log.info("Filters: Karaoke {}, Timescale {}, Tremolo {}, Vibrato {}, Volume {}",
                FilterUtil.KARAOKE_AVAILABLE ? "available" : "unavailable",
                FilterUtil.TIMESCALE_AVAILABLE ? "available" : "unavailable",
                FilterUtil.TREMOLO_AVAILABLE ? "available" : "unavailable",
                FilterUtil.VIBRATO_AVAILABLE ? "available" : "unavailable",
                FilterUtil.VOLUME_AVAILABLE ? "available" : "unavailable"
        );
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
        if(config.getBoolean("send-system.async", true)) {
            factory = new AsyncPacketProviderFactory(factory);
        }
        log.info("Send system: {}, async provider {}",
                config.get("send-system.type", "nas"),
                config.getBoolean("send-system.async", true) ? "enabled" : "disabled"
        );
        return factory;
    }
}
