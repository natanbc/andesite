package andesite.node.player.filter;

import com.sedmelluq.discord.lavaplayer.filter.AudioFilter;
import com.sedmelluq.discord.lavaplayer.filter.FilterChainBuilder;
import com.sedmelluq.discord.lavaplayer.filter.PcmFilterFactory;
import com.sedmelluq.discord.lavaplayer.filter.UniversalPcmAudioFilter;
import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import io.vertx.core.json.JsonObject;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class FilterChainConfiguration {
    private final EqualizerConfig equalizer = new EqualizerConfig();
    private final KaraokeConfig karaoke = new KaraokeConfig();
    private final TimescaleConfig timescale = new TimescaleConfig();
    private final TremoloConfig tremolo = new TremoloConfig();
    private final VibratoConfig vibrato = new VibratoConfig();
    private final VolumeConfig volume = new VolumeConfig();
    private final Config[] filters = {equalizer, karaoke, timescale, tremolo, vibrato, volume};

    @CheckReturnValue
    public boolean isEnabled() {
        for(var config : filters) {
            if(config.configured()) return true;
        }
        return false;
    }

    @Nullable
    @CheckReturnValue
    public PcmFilterFactory factory() {
        return isEnabled() ? new Factory(this) : null;
    }

    @Nonnull
    @CheckReturnValue
    public JsonObject encode() {
        return new JsonObject()
                .put("enabled", isEnabled())
                .put("equalizer", equalizer.encode().put("enabled", equalizer.configured()))
                .put("karaoke", karaoke.encode().put("enabled", karaoke.configured()))
                .put("timescale", timescale.encode().put("enabled", timescale.configured()))
                .put("tremolo", tremolo.encode().put("enabled", tremolo.configured()))
                .put("vibrato", vibrato.encode().put("enabled", vibrato.configured()))
                .put("volume", volume.encode().put("enabled", volume.configured()));
    }

    @Nonnull
    @CheckReturnValue
    public EqualizerConfig equalizer() {
        return equalizer;
    }

    @Nonnull
    @CheckReturnValue
    public KaraokeConfig karaoke() {
        return karaoke;
    }

    @Nonnull
    @CheckReturnValue
    public TimescaleConfig timescale() {
        return timescale;
    }

    @Nonnull
    @CheckReturnValue
    public TremoloConfig tremolo() {
        return tremolo;
    }

    @Nonnull
    @CheckReturnValue
    public VibratoConfig vibrato() {
        return vibrato;
    }

    @Nonnull
    @CheckReturnValue
    public VolumeConfig volume() {
        return volume;
    }

    private static class Factory implements PcmFilterFactory {
        private final FilterChainConfiguration configuration;

        private Factory(FilterChainConfiguration configuration) {
            this.configuration = configuration;
        }

        @Override
        public List<AudioFilter> buildChain(AudioTrack track, AudioDataFormat format, UniversalPcmAudioFilter output) {
            var builder = new FilterChainBuilder();
            builder.addFirst(output);
            for(var config : configuration.filters) {
                var filter = config.configured() ?
                        config.create(format, builder.makeFirstFloat(format.channelCount)) //may return null
                        : null;
                if(filter != null) {
                    builder.addFirst(filter);
                }
            }
            var list = builder.build(null, format.channelCount).filters;
            //remove output
            return list.subList(1, list.size());
        }
    }
}
