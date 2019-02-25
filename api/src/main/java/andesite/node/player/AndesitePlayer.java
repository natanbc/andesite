package andesite.node.player;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;

public interface AndesitePlayer extends BasePlayer {
    /**
     * Returns the track mixer for this player.
     *
     * @return The track mixer for this player.
     */
    @Nonnull
    @CheckReturnValue
    AndesiteTrackMixer mixer();
    
    /**
     * Returns the state of the mixer.
     *
     * @return The state of the mixer.
     */
    @Nonnull
    @CheckReturnValue
    MixerState mixerState();
    
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
    
    enum MixerState {
        /**
         * The mixer is disabled.
         */
        DISABLED(false),
        /**
         * The mixer is being enabled, but is currently disabled.
         */
        ENABLING(false),
        /**
         * The mixer is enabled.
         */
        ENABLED(true),
        /**
         * The mixer is being disabled, but is currently enabled.
         */
        DISABLING(true);
        
        private final boolean isUsingMixer;
        
        MixerState(boolean isUsingMixer) {
            this.isUsingMixer = isUsingMixer;
        }
        
        @CheckReturnValue
        public boolean isUsingMixer() {
            return isUsingMixer;
        }
    }
}
