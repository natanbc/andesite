package andesite.player;

import andesite.NodeState;
import andesite.player.filter.FilterChainConfiguration;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import io.vertx.core.json.JsonObject;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;

public interface BasePlayer {
    /**
     * Returns the node that owns this player.
     *
     * @return The node that owns this player.
     */
    @Nonnull
    @CheckReturnValue
    NodeState node();
    
    /**
     * Returns the user id of this player.
     *
     * @return The user id for this player.
     */
    @Nonnull
    @CheckReturnValue
    String userId();
    
    /**
     * Returns the guild id of this player.
     *
     * @return The guild id for this player.
     */
    @Nonnull
    @CheckReturnValue
    String guildId();
    
    /**
     * Returns the frame loss counter of this player, which tracks how
     * many frames were sent or lost over the past minute.
     *
     * @return The frame loss counter of this player.
     */
    @Nonnull
    @CheckReturnValue
    FrameLossCounter frameLossCounter();
    
    /**
     * Returns the filter configuration for this player.
     *
     * @return The filter configuration for this player.
     */
    @Nonnull
    @CheckReturnValue
    FilterChainConfiguration filterConfig();
    
    /**
     * Returns the audio player for this player.
     *
     * @return The audio player for this player.
     */
    @Nonnull
    @CheckReturnValue
    AudioPlayer audioPlayer();
    
    /**
     * Encodes the state of this player for sending to clients.
     *
     * @return A json object containing the state of this player.
     */
    @Nonnull
    @CheckReturnValue
    JsonObject encodeState();
}
