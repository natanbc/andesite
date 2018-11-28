package andesite.node.player.filter;

import com.github.natanbc.lavadsp.volume.VolumePcmAudioFilter;
import com.sedmelluq.discord.lavaplayer.filter.AudioFilter;
import com.sedmelluq.discord.lavaplayer.filter.FloatPcmAudioFilter;
import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat;
import io.vertx.core.json.JsonObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class VolumeConfig implements Config {
    private float volume = 1f;

    public float volume() {
        return volume;
    }

    public void setVolume(float volume) {
        if(volume <= 0) {
            throw new IllegalArgumentException("Volume <= 0.0");
        }
        if(volume > 5) {
            throw new IllegalArgumentException("Volume > 5.0");
        }
        this.volume = volume;
    }

    @Override
    public boolean configured() {
        return Config.isSet(volume, 1f);
    }

    @Nullable
    @Override
    public AudioFilter create(AudioDataFormat format, FloatPcmAudioFilter output) {
        return new VolumePcmAudioFilter(output, format.channelCount)
                .setVolume(volume);
    }

    @Nonnull
    @Override
    public JsonObject encode() {
        return new JsonObject()
                .put("volume", volume);
    }
}
