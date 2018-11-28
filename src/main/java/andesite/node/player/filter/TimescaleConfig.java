package andesite.node.player.filter;

import com.github.natanbc.lavadsp.timescale.TimescalePcmAudioFilter;
import com.sedmelluq.discord.lavaplayer.filter.AudioFilter;
import com.sedmelluq.discord.lavaplayer.filter.FloatPcmAudioFilter;
import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat;
import io.vertx.core.json.JsonObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class TimescaleConfig implements Config {
    private float speed = 1f;
    private float pitch = 1f;
    private float rate = 1f;

    public float speed() {
        return speed;
    }

    public void setSpeed(float speed) {
        if(speed <= 0) {
            throw new IllegalArgumentException("speed <= 0");
        }
        this.speed = speed;
    }

    public float pitch() {
        return pitch;
    }

    public void setPitch(float pitch) {
        if(pitch <= 0) {
            throw new IllegalArgumentException("pitch <= 0");
        }
        this.pitch = pitch;
    }

    public float rate() {
        return rate;
    }

    public void setRate(float rate) {
        if(rate <= 0) {
            throw new IllegalArgumentException("rate <= 0");
        }
        this.rate = rate;
    }

    @Override
    public boolean configured() {
        return Config.isSet(speed, 1f) || Config.isSet(pitch, 1f) || Config.isSet(rate, 1f);
    }

    @Nullable
    @Override
    public AudioFilter create(AudioDataFormat format, FloatPcmAudioFilter output) {
        return new TimescalePcmAudioFilter(output, format.channelCount, format.sampleRate)
                .setSpeed(speed)
                .setPitch(pitch)
                .setRate(rate);
    }

    @Nonnull
    @Override
    public JsonObject encode() {
        return new JsonObject()
                .put("speed", speed)
                .put("pitch", pitch)
                .put("rate", rate);
    }
}
