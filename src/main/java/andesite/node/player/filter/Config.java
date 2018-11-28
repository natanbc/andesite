package andesite.node.player.filter;

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

    @CheckReturnValue
    boolean configured();

    @Nullable
    @CheckReturnValue
    AudioFilter create(AudioDataFormat format, FloatPcmAudioFilter output);

    @Nonnull
    @CheckReturnValue
    JsonObject encode();

    static boolean isSet(float value, float defaultValue) {
        return Math.abs(value - defaultValue) >= MINIMUM_FP_DIFF;
    }
}
