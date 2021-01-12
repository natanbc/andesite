package andesite.player.filter;

import com.github.natanbc.lavadsp.lowpass.LowPassPcmAudioFilter;
import com.sedmelluq.discord.lavaplayer.filter.AudioFilter;
import com.sedmelluq.discord.lavaplayer.filter.FloatPcmAudioFilter;
import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat;
import io.vertx.core.json.JsonObject;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class LowPassConfig implements Config {
    private float smoothing = 20f;
    
    public float smoothing() {
        return smoothing;
    }
    
    public void setSmoothing(float smoothing) {
        this.smoothing = smoothing;
    }
    
    @CheckReturnValue
    @Nonnull
    @Override
    public String name() {
        return "lowpass";
    }
    
    @CheckReturnValue
    @Override
    public boolean enabled() {
        return Config.isSet(smoothing, 20f);
    }
    
    @CheckReturnValue
    @Nullable
    @Override
    public AudioFilter create(AudioDataFormat format, FloatPcmAudioFilter output) {
        return new LowPassPcmAudioFilter(output, format.channelCount, 0)
                .setSmoothing(smoothing);
    }
    
    @CheckReturnValue
    @Nonnull
    @Override
    public JsonObject encode() {
        return new JsonObject().put("smoothing", smoothing);
    }
}
