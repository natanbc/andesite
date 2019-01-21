package andesite.node.player;

import andesite.node.Andesite;
import andesite.node.NodeState;
import andesite.node.player.filter.FilterChainConfiguration;
import andesite.node.util.LazyInit;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import io.vertx.core.json.JsonObject;
import net.dv8tion.jda.core.audio.AudioSendHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class Player implements AudioSendHandler, AndesitePlayer {
    private static final Logger log = LoggerFactory.getLogger(Player.class);

    private final Map<Object, EventEmitter> emitters = new ConcurrentHashMap<>();
    private final FilterChainConfiguration filterConfig = new FilterChainConfiguration();
    private final Andesite andesite;
    private final AudioPlayerManager audioPlayerManager;
    private final LazyInit<TrackMixer> mixer;
    private final String guildId;
    private final String userId;
    private final AudioPlayer audioPlayer;
    private final long updateTimerId;
    private final long cleanupTimerId;

    /**
     * This is called fast because it avoids reencoding audio when the source is opus,
     * unlike TrackMixer.
     */
    private final AudioSendHandler fastSendHandler;
    private volatile AudioSendHandler realSendHandler;
    private volatile AudioSendHandler switchWhenReady;

    private long lastUse;

    public Player(@Nonnull Andesite andesite, @Nonnull String guildId, @Nonnull String userId) {
        this.andesite = andesite;
        this.audioPlayerManager = andesite.audioPlayerManager();
        this.mixer = new LazyInit<>(() -> new TrackMixer(andesite.pcmAudioPlayerManager()));
        this.guildId = guildId;
        this.userId = userId;
        this.audioPlayer = audioPlayerManager.createPlayer();
        this.audioPlayer.addListener(event -> emitters.values().forEach(e -> e.onEvent(event)));
        this.fastSendHandler = andesite.config().getBoolean("send-system.non-allocating", false) ?
                new NonAllocatingSendHandler(audioPlayer) :
                new AllocatingSendHandler(audioPlayer);
        this.realSendHandler = fastSendHandler;
        this.updateTimerId = andesite.vertx().setPeriodic(5000, __ -> {
            if(audioPlayer.getPlayingTrack() == null) return;
            emitters.values().forEach(EventEmitter::sendPlayerUpdate);
        });
        this.cleanupTimerId = andesite.vertx().setPeriodic(30_000, __ -> {
            var now = System.nanoTime();
            if(now > lastUse + TimeUnit.SECONDS.toNanos(60)) {
                andesite.requestHandler().destroy(userId, guildId);
            }
        });
    }

    @Override
    public NodeState node() {
        return andesite;
    }

    @Override
    @Nonnull
    @CheckReturnValue
    public FilterChainConfiguration filterConfig() {
        return filterConfig;
    }

    @Override
    @Nonnull
    @CheckReturnValue
    public String guildId() {
        return guildId;
    }

    @Override
    @Nonnull
    @CheckReturnValue
    public String userId() {
        return userId;
    }

    @Override
    @Nonnull
    @CheckReturnValue
    public AudioPlayer audioPlayer() {
        return audioPlayer;
    }

    @Nonnull
    @CheckReturnValue
    public AudioPlayerManager audioPlayerManager() {
        return audioPlayerManager;
    }

    @Nonnull
    @CheckReturnValue
    public Map<Object, EventEmitter> eventListeners() {
        return emitters;
    }

    public void setListener(@Nonnull Object key, @Nonnull Consumer<JsonObject> sink) {
        emitters.put(key, new EventEmitter(this, sink));
    }

    @CheckReturnValue
    public boolean isPlaying() {
        return audioPlayer.getPlayingTrack() != null && !audioPlayer.isPaused();
    }

    @Override
    @Nonnull
    public TrackMixer mixer() {
        return mixer.get();
    }

    @Override
    public void switchToMixer() {
        switchWhenReady = mixer.get();
    }

    @Override
    public void switchToSingle() {
        switchWhenReady = fastSendHandler;
    }

    @Override
    public void destroy() {
        realSendHandler = fastSendHandler; //ensures we won't call the opus encoder in track mixer after releasing
        mixer.getIfPresent()
            .ifPresent(TrackMixer::close);
        audioPlayer.destroy();
        andesite.vertx().cancelTimer(updateTimerId);
        andesite.vertx().cancelTimer(cleanupTimerId);
    }

    @Override
    @Nonnull
    @CheckReturnValue
    public JsonObject encodeState() {
        var m = mixer.getIfPresent();
        var mixerStats = new JsonObject();
        m.ifPresent(trackMixer -> trackMixer.players().forEach((k, p) -> {
            var audioPlayer = p.audioPlayer();
            mixerStats.put(k, new JsonObject()
                    .put("time", Instant.now().toEpochMilli())
                    .put("position", audioPlayer.getPlayingTrack() == null ? null : audioPlayer.getPlayingTrack().getPosition())
                    .put("paused", audioPlayer.isPaused())
                    .put("volume", audioPlayer.getVolume())
                    .put("filters", p.filterConfig().encode())
            );
        }));
        return new JsonObject()
                .put("time", Instant.now().toEpochMilli())
                .put("position", audioPlayer.getPlayingTrack() == null ? null : audioPlayer.getPlayingTrack().getPosition())
                .put("paused", audioPlayer.isPaused())
                .put("volume", audioPlayer.getVolume())
                .put("filters", filterConfig.encode())
                .put("mixer", mixerStats)
                .put("mixerEnabled", m.isPresent() && m.get() == realSendHandler);
    }

    @Override
    public boolean canProvide() {
        lastUse = System.nanoTime();
        if(switchWhenReady != null && switchWhenReady.canProvide()) {
            log.info("Switching send handler from {} to {} for {}@{}", realSendHandler, switchWhenReady, userId, guildId);
            realSendHandler = switchWhenReady;
            switchWhenReady = null;
            emitters.values().forEach(EventEmitter::sendPlayerUpdate);
            return true;
        }
        return realSendHandler.canProvide();
    }

    @Override
    public byte[] provide20MsAudio() {
        return realSendHandler.provide20MsAudio();
    }

    @Override
    public boolean isOpus() {
        return true;
    }
}
