package andesite.node.player;

import andesite.node.util.RequestUtils;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import io.vertx.core.json.JsonObject;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

public class EventEmitter extends AudioEventAdapter {
    private final Player player;
    private final Consumer<JsonObject> sendEvent;
    
    public EventEmitter(@Nonnull Player player, @Nonnull Consumer<JsonObject> sendEvent) {
        this.player = player;
        this.sendEvent = sendEvent;
    }
    
    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) {
        sendEvent.accept(event("TrackStartEvent", track));
        sendPlayerUpdate();
    }
    
    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        sendEvent.accept(event("TrackEndEvent", track)
            .put("reason", endReason.toString())
            .put("mayStartNext", endReason.mayStartNext));
        sendPlayerUpdate();
    }
    
    // These exceptions are already logged by Lavaplayer
    @Override
    public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
        sendEvent.accept(event("TrackExceptionEvent", track)
            .put("error", exception.getMessage())
            .put("exception", RequestUtils.encodeThrowableShort(exception)));
        sendPlayerUpdate();
    }
    
    @Override
    public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
        sendEvent.accept(event("TrackStuckEvent", track)
            .put("thresholdMs", thresholdMs));
        sendPlayerUpdate();
    }
    
    public Consumer<JsonObject> sink() {
        return sendEvent;
    }
    
    public void sendPlayerUpdate() {
        sendEvent.accept(new JsonObject()
            .put("op", "player-update")
            .put("guildId", player.guildId())
            .put("userId", player.userId())
            .put("state", player.encodeState())
        );
    }
    
    public void send(JsonObject payload) {
        sendEvent.accept(payload);
    }
    
    private JsonObject event(@Nonnull String type, @Nonnull AudioTrack track) {
        return new JsonObject()
            .put("op", "event")
            .put("type", type)
            .put("guildId", player.guildId())
            .put("userId", player.userId())
            .put("track", RequestUtils.trackString(player.audioPlayerManager(), track));
    }
}
