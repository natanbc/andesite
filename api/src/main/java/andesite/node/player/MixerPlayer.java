package andesite.node.player;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;

public interface MixerPlayer extends BasePlayer {
    /**
     * Returns the player that owns the mixer this player belongs to.
     *
     * @return The owner of this player.
     */
    @Nonnull
    @CheckReturnValue
    AndesitePlayer parentPlayer();

    /**
     * Returns the key used for registering this player in the mixer.
     *
     * @return The key of this player.
     */
    @Nonnull
    @CheckReturnValue
    String key();

    /**
     * Returns the mixer this player belongs to.
     *
     * @return The mixer this player belongs to.
     */
    @Nonnull
    @CheckReturnValue
    default AndesiteTrackMixer mixer() {
        return parentPlayer().mixer();
    }
}
