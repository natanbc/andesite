package andesite.node.player;

import andesite.node.send.AudioProvider;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import java.util.Map;

public interface AndesiteTrackMixer extends AudioProvider {
    /**
     * Returns a map of player id to mixer player.
     *
     * @return The players of the mixer.
     */
    @Nonnull
    @CheckReturnValue
    Map<String, MixerPlayer> players();

    /**
     * Gets or creates a new player with the provided key. This key will be
     * used to store this player in the map.
     *
     * @param key Key for the player.
     *
     * @return The current player for that key, or a new instance.
     */
    @Nonnull
    @CheckReturnValue
    MixerPlayer getPlayer(@Nonnull String key);

    /**
     * Returns the current player for the provided key.
     *
     * @param key Key of the player.
     *
     * @return The current player for the provided key. May be null.
     */
    @CheckReturnValue
    MixerPlayer getExistingPlayer(@Nonnull String key);

    /**
     * Removes a player with the provided id.
     *
     * @param key Id of the player.
     */
    void removePlayer(@Nonnull String key);
}
