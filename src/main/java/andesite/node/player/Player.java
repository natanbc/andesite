package andesite.node.player;

import andesite.node.Andesite;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import io.vertx.core.json.JsonObject;
import net.dv8tion.jda.core.audio.AudioSendHandler;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class Player implements AudioSendHandler {
    private final Andesite andesite;
    private final AudioPlayerManager audioPlayerManager;
    private final String guildId;
    private final String userId;
    private final AudioPlayer audioPlayer;
    private final AudioSendHandler realSendHandler;
    private final Map<String, EventEmitter> listeners = new HashMap<>();
    private final long timerId;

    public Player(@Nonnull Andesite andesite, @Nonnull String guildId, @Nonnull String userId) {
        this.andesite = andesite;
        this.audioPlayerManager = andesite.audioPlayerManager();
        this.guildId = guildId;
        this.userId = userId;
        this.audioPlayer = audioPlayerManager.createPlayer();
        this.audioPlayer.addListener(event -> listeners.values().forEach(listener -> listener.onEvent(event)));
        this.realSendHandler = andesite.config().getBoolean("send-system.non-allocating", false) ?
                new NonAllocatingSendHandler(audioPlayer) :
                new AllocatingSendHandler(audioPlayer);
        this.timerId = andesite.vertx().setPeriodic(5000, __ -> {
            if(audioPlayer.getPlayingTrack() == null) return;
            listeners.values().forEach(EventEmitter::sendPlayerUpdate);
        });
    }

    @Nonnull
    @CheckReturnValue
    public AudioPlayerManager audioPlayerManager() {
        return audioPlayerManager;
    }

    @Nonnull
    @CheckReturnValue
    public String guildId() {
        return guildId;
    }

    @Nonnull
    @CheckReturnValue
    public String userId() {
        return userId;
    }

    @Nonnull
    @CheckReturnValue
    public AudioPlayer audioPlayer() {
        return audioPlayer;
    }

    @Nonnull
    @CheckReturnValue
    public Map<String, EventEmitter> eventListeners() {
        return listeners;
    }

    @Nonnull
    public EmitterReference setListener(@Nonnull String key, @Nonnull Consumer<JsonObject> sink) {
        var emitter = new EventEmitter(this, sink);
        listeners.put(key, emitter);
        return new EmitterReference(this, key, emitter);
    }

    @CheckReturnValue
    public boolean isPlaying() {
        return audioPlayer.getPlayingTrack() != null && !audioPlayer.isPaused();
    }

    public void destroy() {
        audioPlayer.destroy();
        andesite.vertx().cancelTimer(timerId);
    }

    @Nonnull
    @CheckReturnValue
    public JsonObject encodeState() {
        return new JsonObject()
                .put("time", Instant.now().toEpochMilli())
                .put("position", audioPlayer.getPlayingTrack() == null ? null : audioPlayer.getPlayingTrack().getPosition())
                .put("paused", audioPlayer.isPaused())
                .put("volume", audioPlayer.getVolume());
    }

    @Override
    public boolean canProvide() {
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
