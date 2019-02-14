package andesite.node.player;

import andesite.node.player.filter.FilterChainConfiguration;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;

public interface MixerPlayer {
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
}
