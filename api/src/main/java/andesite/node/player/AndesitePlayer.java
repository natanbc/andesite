package andesite.node.player;

import andesite.node.NodeState;
import andesite.node.player.filter.FilterChainConfiguration;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import io.vertx.core.json.JsonObject;

public interface AndesitePlayer {
    /**
     * Returns the node that owns this player.
     *
     * @return The node that owns this player.
     */
    NodeState node();

    /**
     * Returns the filter configuration for this player.
     *
     * @return The filter configuration for this player.
     */
    FilterChainConfiguration filterConfig();

    /**
     * Returns the user id of this player.
     *
     * @return The user id for this player.
     */
    String userId();

    /**
     * Returns the guild id of this player.
     *
     * @return The guild id for this player.
     */
    String guildId();

    /**
     * Returns the audio player for this player.
     *
     * @return The audio player for this player.
     */
    AudioPlayer audioPlayer();

    /**
     * Returns the track mixer for this player.
     *
     * @return The track mixer for this player.
     */
    AndesiteTrackMixer mixer();

    /**
     * Requests the player switches to the mixer as soon as possible.
     *
     * <br>The mixer will be polled for new frames until it returns one.
     * While it doesn't return one, the current provider will be used instead.
     */
    void switchToMixer();

    /**
     * Requests the player switches to the default provider as soon as possible.
     *
     * <br>The default provider will be polled for new frames until it returns one.
     * While it doesn't return one, the current provider will be used instead.
     */
    void switchToSingle();

    /**
     * Encodes the state of this player for sending to clients.
     *
     * @return A json object containing the state of this player.
     */
    JsonObject encodeState();
}
