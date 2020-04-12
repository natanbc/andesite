package andesite.node.player.filter;

import com.sedmelluq.discord.lavaplayer.filter.AudioFilter;
import com.sedmelluq.discord.lavaplayer.filter.FloatPcmAudioFilter;
import com.sedmelluq.discord.lavaplayer.filter.equalizer.Equalizer;
import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class EqualizerConfig implements Config {
    private final float[] equalizerBands = new float[Equalizer.BAND_COUNT];

    public float getBand(int band) {
        return equalizerBands[band];
    }

    public void setBand(int band, float gain) {
        equalizerBands[band] = gain;
    }

    @Nonnull
    @Override
    public String name() {
        return "equalizer";
    }

    @Override
    public boolean enabled() {
        for (var band : equalizerBands) {
            if (Config.isSet(band, 0f)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    @Override
    public AudioFilter create(AudioDataFormat format, FloatPcmAudioFilter output) {
        return Equalizer.isCompatible(format) ? new Equalizer(format.channelCount, output, equalizerBands) : null;
    }

    @Nonnull
    @Override
    public JsonObject encode() {
        var array = new JsonArray();
        for (var band : equalizerBands) {
            array.add(band);
        }
        return new JsonObject().put("bands", array);
    }
}
