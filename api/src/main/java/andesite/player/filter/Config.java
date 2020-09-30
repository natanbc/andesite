package andesite.player.filter;

import com.sedmelluq.discord.lavaplayer.filter.AudioFilter;
import com.sedmelluq.discord.lavaplayer.filter.FloatPcmAudioFilter;
import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat;
import io.vertx.core.json.JsonObject;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface Config {
    /**
     * Minimum absolute difference for floating point values. Values whose difference to the default
     * value are smaller than this are considered equal to the default.
     */
    float MINIMUM_FP_DIFF = 0.01f;
    
    /**
     * Returns the name of this filter, to be used in {@link FilterChainConfiguration#encode()}.
     *
     * @return A non null string representing this filter.
     */
    @CheckReturnValue
    @Nonnull
    String name();
    
    /**
     * Returns whether or not this filter should be enabled.
     *
     * <br>For filters that are enabled, but unavailable, this method
     * should return false.
     *
     * @return Whether or not the filter is enabled.
     */
    @CheckReturnValue
    boolean enabled();
    
    /**
     * Creates a new audio filter with the current settings.
     *
     * @param format Format of the audio.
     * @param output Filter to write data to.
     *
     * @return A new audio filter.
     */
    @Nullable
    @CheckReturnValue
    AudioFilter create(AudioDataFormat format, FloatPcmAudioFilter output);
    
    /**
     * Encodes the state of this configuration to send to clients.
     *
     * @return A json object containing the values of this configuration.
     */
    @Nonnull
    @CheckReturnValue
    JsonObject encode();
    
    /**
     * Returns true if the difference between {@code value} and {@code defaultValue}
     * is greater or equal to {@link #MINIMUM_FP_DIFF}.
     *
     * @param value        Value to check.
     * @param defaultValue Default value.
     *
     * @return True if the difference is greater or equal to the minimum.
     */
    static boolean isSet(float value, float defaultValue) {
        return Math.abs(value - defaultValue) >= MINIMUM_FP_DIFF;
    }
}
