package andesite.node.player.filter;

import com.github.natanbc.lavadsp.karaoke.KaraokePcmAudioFilter;
import com.sedmelluq.discord.lavaplayer.filter.AudioFilter;
import com.sedmelluq.discord.lavaplayer.filter.FloatPcmAudioFilter;
import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat;
import io.vertx.core.json.JsonObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class KaraokeConfig implements Config {
    private float level = 1f;
    private float monoLevel = 1f;
    private float filterBand = 220f;
    private float filterWidth = 100f;
    
    public float level() {
        return level;
    }
    
    public void setLevel(float level) {
        this.level = level;
    }
    
    public float monoLevel() {
        return monoLevel;
    }
    
    public void setMonoLevel(float monoLevel) {
        this.monoLevel = monoLevel;
    }
    
    public float filterBand() {
        return filterBand;
    }
    
    public void setFilterBand(float filterBand) {
        this.filterBand = filterBand;
    }
    
    public float filterWidth() {
        return filterWidth;
    }
    
    public void setFilterWidth(float filterWidth) {
        this.filterWidth = filterWidth;
    }
    
    @Nonnull
    @Override
    public String name() {
        return "karaoke";
    }
    
    @Override
    public boolean enabled() {
        return Config.isSet(level, 1f) || Config.isSet(monoLevel, 1f) ||
                Config.isSet(filterBand, 220f) || Config.isSet(filterWidth, 100f);
    }
    
    @Nullable
    @Override
    public AudioFilter create(AudioDataFormat format, FloatPcmAudioFilter output) {
        return new KaraokePcmAudioFilter(output, format.channelCount, format.sampleRate)
            .setLevel(level)
            .setMonoLevel(monoLevel)
            .setFilterBand(filterBand)
            .setFilterWidth(filterWidth);
    }
    
    @Nonnull
    @Override
    public JsonObject encode() {
        return new JsonObject()
            .put("level", level)
            .put("monoLevel", monoLevel)
            .put("filterBand", filterBand)
            .put("filterWidth", filterBand);
    }
}
