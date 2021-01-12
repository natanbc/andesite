package andesite.player.filter;

import com.github.natanbc.lavadsp.channelmix.ChannelMixPcmAudioFilter;
import com.sedmelluq.discord.lavaplayer.filter.AudioFilter;
import com.sedmelluq.discord.lavaplayer.filter.FloatPcmAudioFilter;
import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat;
import io.vertx.core.json.JsonObject;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ChannelMixConfig implements Config {
    private float leftToLeft = 1f;
    private float leftToRight = 0f;
    private float rightToLeft = 0f;
    private float rightToRight = 1f;
    
    public float leftToLeft() {
        return leftToLeft;
    }
    
    public void setLeftToLeft(float leftToLeft) {
        this.leftToLeft = leftToLeft;
    }
    
    public float leftToRight() {
        return leftToRight;
    }
    
    public void setLeftToRight(float leftToRight) {
        this.leftToRight = leftToRight;
    }
    
    public float rightToLeft() {
        return rightToLeft;
    }
    
    public void setRightToLeft(float rightToLeft) {
        this.rightToLeft = rightToLeft;
    }
    
    public float rightToRight() {
        return rightToRight;
    }
    
    public void setRightToRight(float rightToRight) {
        this.rightToRight = rightToRight;
    }
    
    @CheckReturnValue
    @Nonnull
    @Override
    public String name() {
        return "channelmix";
    }
    
    @CheckReturnValue
    @Override
    public boolean enabled() {
        return Config.isSet(leftToLeft,  1.0f) || Config.isSet(leftToRight,  0.0f) ||
               Config.isSet(rightToLeft, 0.0f) || Config.isSet(rightToRight, 1.0f);
    }
    
    @CheckReturnValue
    @Nullable
    @Override
    public AudioFilter create(AudioDataFormat format, FloatPcmAudioFilter output) {
        return new ChannelMixPcmAudioFilter(output)
                .setLeftToLeft(leftToLeft)
                .setLeftToRight(leftToRight)
                .setRightToLeft(rightToLeft)
                .setRightToRight(rightToRight);
    }
    
    @CheckReturnValue
    @Nonnull
    @Override
    public JsonObject encode() {
        return new JsonObject()
                .put("leftToLeft",   leftToLeft)
                .put("leftToRight",  leftToRight)
                .put("rightToLeft",  rightToLeft)
                .put("rightToRight", rightToRight);
    }
}
