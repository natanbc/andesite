package andesite.node.player.filter;

import com.github.natanbc.lavadsp.vibrato.VibratoPcmAudioFilter;
import com.sedmelluq.discord.lavaplayer.filter.AudioFilter;
import com.sedmelluq.discord.lavaplayer.filter.FloatPcmAudioFilter;
import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat;
import io.vertx.core.json.JsonObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class VibratoConfig implements Config {
    private static final float VIBRATO_FREQUENCY_MAX_HZ = 14;

    private float frequency = 2f;
    private float depth = 0.5f;

    public float frequency() {
        return frequency;
    }

    public void setFrequency(float frequency) {
        if (frequency <= 0) {
            throw new IllegalArgumentException("Frequency <= 0");
        }
        if (frequency > VIBRATO_FREQUENCY_MAX_HZ) {
            throw new IllegalArgumentException("Frequency > max (" + VIBRATO_FREQUENCY_MAX_HZ + ")");
        }
        this.frequency = frequency;
    }

    public float depth() {
        return depth;
    }

    public void setDepth(float depth) {
        if (depth <= 0) {
            throw new IllegalArgumentException("Depth <= 0");
        }
        if (depth > 1) {
            throw new IllegalArgumentException("Depth > 1");
        }
        this.depth = depth;
    }

    @Nonnull
    @Override
    public String name() {
        return "vibrato";
    }

    @Override
    public boolean enabled() {
        return Config.isSet(frequency, 2f) || Config.isSet(depth, 0.5f);
    }

    @Nullable
    @Override
    public AudioFilter create(AudioDataFormat format, FloatPcmAudioFilter output) {
        return new VibratoPcmAudioFilter(output, format.channelCount, format.sampleRate)
                .setFrequency(frequency)
                .setDepth(depth);
    }

    @Nonnull
    @Override
    public JsonObject encode() {
        return new JsonObject()
                .put("frequency", frequency)
                .put("depth", depth);
    }
}
