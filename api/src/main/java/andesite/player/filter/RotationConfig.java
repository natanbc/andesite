package andesite.player.filter;

import com.github.natanbc.lavadsp.rotation.RotationPcmAudioFilter;
import com.sedmelluq.discord.lavaplayer.filter.AudioFilter;
import com.sedmelluq.discord.lavaplayer.filter.FloatPcmAudioFilter;
import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat;
import io.vertx.core.json.JsonObject;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class RotationConfig implements Config {
    private float rotationHz = 5f;
    
    public float rotationHz() {
        return rotationHz;
    }
    
    public void setRotationHz(float rotationHz) {
        this.rotationHz = rotationHz;
    }
    
    @CheckReturnValue
    @Nonnull
    @Override
    public String name() {
        return "rotation";
    }
    
    @CheckReturnValue
    @Override
    public boolean enabled() {
        return Config.isSet(rotationHz, 5f);
    }
    
    @CheckReturnValue
    @Nullable
    @Override
    public AudioFilter create(AudioDataFormat format, FloatPcmAudioFilter output) {
        return new RotationPcmAudioFilter(output, format.sampleRate)
                .setRotationSpeed(rotationHz);
    }
    
    @CheckReturnValue
    @Nonnull
    @Override
    public JsonObject encode() {
        return new JsonObject().put("rotationHz", rotationHz);
    }
}
