package andesite.player;

import andesite.Andesite;
import andesite.NodeState;
import andesite.player.filter.FilterChainConfiguration;
import andesite.send.AudioProvider;
import andesite.util.LazyInit;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class Player implements AndesitePlayer {
    private static final Logger log = LoggerFactory.getLogger(Player.class);
    
    private final FrameLossTracker frameLossTracker = new FrameLossTracker();
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
    private final AudioProvider fastProvider;
    private volatile AudioProvider realProvider;
    private volatile AudioProvider switchWhenReady;
    
    private long lastUse;
    /* lavaplayer position is wrong if you change the speed */
    private double realPositionMs;
    
    public Player(@Nonnull Andesite andesite, @Nonnull String guildId, @Nonnull String userId) {
        this.andesite = andesite;
        this.audioPlayerManager = andesite.audioPlayerManager();
        this.mixer = new LazyInit<>(() -> new TrackMixer(andesite.pcmAudioPlayerManager(), this));
        this.guildId = guildId;
        this.userId = userId;
        this.audioPlayer = audioPlayerManager.createPlayer();
        this.audioPlayer.addListener(event -> emitters.values().forEach(e -> e.onEvent(event)));
        this.audioPlayer.addListener(frameLossTracker);
        var audioConfig = audioPlayerManager.getConfiguration();
        this.fastProvider = andesite.config().getBoolean("andesite.lavaplayer.non-allocating") ?
                new NonAllocatingProvider(audioPlayer, audioConfig) :
                new AllocatingProvider(audioPlayer, audioConfig);
        this.realProvider = fastProvider;
        this.updateTimerId = andesite.vertx().setPeriodic(5000, __ -> {
            if(audioPlayer.getPlayingTrack() == null) return;
            emitters.values().forEach(EventEmitter::sendPlayerUpdate);
        });
        this.cleanupTimerId = andesite.vertx().setPeriodic(30_000, __ -> {
            var now = System.nanoTime();
            var regularPlaying = audioPlayer.getPlayingTrack() != null && !audioPlayer.isPaused();
            var mixerPlaying = mixer.isPresent() && anyPlaying(mixer.get().players().values());
            if((regularPlaying || mixerPlaying) && now > lastUse + TimeUnit.SECONDS.toNanos(60)) {
                andesite.requestHandler().destroy(userId, guildId, true);
            }
        });
        audioPlayer.addListener(new AudioEventAdapter() {
            @Override
            public void onTrackStart(AudioPlayer player, AudioTrack track) {
                realPositionMs = track.getPosition();
            }
        });
    }
    
    @Override
    @Nonnull
    @CheckReturnValue
    public NodeState node() {
        return andesite;
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
    public String guildId() {
        return guildId;
    }
    
    @Nonnull
    @Override
    public FrameLossCounter frameLossCounter() {
        return frameLossTracker;
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
    public AudioPlayer audioPlayer() {
        return audioPlayer;
    }
    
    @Override
    @Nonnull
    @CheckReturnValue
    public JsonObject encodeState() {
        var m = mixer.getIfPresent();
        var mixerStats = new JsonObject();
        m.ifPresent(trackMixer -> trackMixer.players().forEach((k, p) -> {
            mixerStats.put(k, p.encodeState());
        }));
        var obj = new JsonObject()
                .put("time", Instant.now().toEpochMilli())
                .put("paused", audioPlayer.isPaused())
                .put("volume", audioPlayer.getVolume())
                .put("filters", filterConfig.encode())
                .put("mixer", mixerStats)
                .put("mixerEnabled", m.isPresent() && m.get() == realProvider)
                .put("frame", new JsonObject()
                        .put("loss", frameLossTracker.lastMinuteLoss().sum())
                        .put("success", frameLossTracker.lastMinuteSuccess().sum())
                        .put("usable", frameLossTracker.isDataUsable())
                );
        var track = audioPlayer.getPlayingTrack();
        if(track != null) {
            obj.put("position", (long)realPositionMs);
        }
    
        return obj;
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
    
    @CheckReturnValue
    @Nonnull
    @Override
    public MixerState mixerState() {
        if(mixer.isPresent()) {
            var m = mixer.get();
            if(switchWhenReady == m) {
                return MixerState.ENABLING;
            }
            if(realProvider == m) {
                return MixerState.ENABLED;
            }
        }
        if(realProvider == fastProvider) {
            return MixerState.DISABLED;
        }
        if(switchWhenReady == fastProvider) {
            return MixerState.DISABLING;
        }
        throw new AssertionError("This state should be impossible");
    }
    
    @Override
    public void switchToMixer() {
        if(realProvider != mixer.get()) {
            switchWhenReady = mixer.get();
        }
    }
    
    @Override
    public void switchToSingle() {
        if(realProvider != fastProvider) {
            switchWhenReady = fastProvider;
        }
    }
    
    @CheckReturnValue
    @Override
    public boolean canProvide() {
        lastUse = System.nanoTime();
        if(switchWhenReady != null && switchWhenReady.canProvide()) {
            log.info("Switching send handler from {} to {} for {}@{}", realProvider, switchWhenReady, userId, guildId);
            realProvider = switchWhenReady;
            switchWhenReady = null;
            emitters.values().forEach(EventEmitter::sendPlayerUpdate);
            frameLossTracker.onSuccess();
            return true;
        }
        var r = realProvider.canProvide();
        if(r) {
            realPositionMs = updatePosition(realPositionMs, filterConfig);
            frameLossTracker.onSuccess();
        } else {
            frameLossTracker.onFail();
        }
        return r;
    }
    
    @CheckReturnValue
    @Nonnull
    @Override
    public ByteBuffer provide() {
        return realProvider.provide();
    }
    
    @Override
    public void close() {
        realProvider = fastProvider; //ensures we won't call the opus encoder in track mixer after releasing
        mixer.getIfPresent()
                .ifPresent(TrackMixer::close);
        audioPlayer.destroy();
        andesite.vertx().cancelTimer(updateTimerId);
        andesite.vertx().cancelTimer(cleanupTimerId);
    }
    
    public void onDestroy(boolean cleanup) {
        emitters.values().forEach(e -> e.onPlayerDestroyed(cleanup));
    }
    
    public void seek(long ms) {
        var track = audioPlayer.getPlayingTrack();
        if(track != null) {
            track.setPosition(ms);
        }
        realPositionMs = ms;
    }
    
    static double updatePosition(double current, FilterChainConfiguration filterConfig) {
        var scale = filterConfig.timescale().enabled() ? filterConfig.timescale().speed() : 1;
        return current + 20 * scale;
    }
    
    private static boolean anyPlaying(Iterable<? extends MixerPlayer> iterable) {
        for(var p : iterable) {
            if(p.audioPlayer().getPlayingTrack() != null && !p.audioPlayer().isPaused()) {
                return true;
            }
        }
        return false;
    }
}
