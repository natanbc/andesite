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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public class FilterChainConfiguration {
    private final EqualizerConfig equalizer = new EqualizerConfig();
    private final KaraokeConfig karaoke = new KaraokeConfig();
    private final TimescaleConfig timescale = new TimescaleConfig();
    private final TremoloConfig tremolo = new TremoloConfig();
    private final VibratoConfig vibrato = new VibratoConfig();
    private final VolumeConfig volume = new VolumeConfig();
    private final List<Config> filters = new ArrayList<>(6);
    private final Map<Class<? extends Config>, Config> custom = new HashMap<>();

    public FilterChainConfiguration() {
        Collections.addAll(filters, equalizer, karaoke, timescale, tremolo, vibrato, volume);
    }

    public boolean hasCustomConfig(Class<? extends Config> clazz) {
        return custom.containsKey(clazz);
    }

    @SuppressWarnings("unchecked")
    @CheckReturnValue
    public <T extends Config> T customConfig(@Nonnull Class<T> clazz) {
        return (T)custom.get(clazz);
    }

    @SuppressWarnings("unchecked")
    @CheckReturnValue
    public <T extends Config> T customConfig(@Nonnull Class<T> clazz, @Nonnull Supplier<T> supplier) {
        return (T)custom.computeIfAbsent(clazz, __ -> {
            var config = Objects.requireNonNull(supplier.get(), "Provided configuration may not be null");
            if(!clazz.isInstance(config)) {
                throw new IllegalArgumentException("Config not instance of provided class");
            }
            filters.add(config);
            return config;
        });
    }

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
        var obj = new JsonObject();
        custom.forEach((k, v) -> {
            obj.put(k.getName(), v.encode().put("enabled", v.configured()));
        });
        return new JsonObject()
                .put("enabled", isEnabled())
                .put("equalizer", equalizer.encode().put("enabled", equalizer.configured()))
                .put("karaoke", karaoke.encode().put("enabled", karaoke.configured()))
                .put("timescale", timescale.encode().put("enabled", timescale.configured()))
                .put("tremolo", tremolo.encode().put("enabled", tremolo.configured()))
                .put("vibrato", vibrato.encode().put("enabled", vibrato.configured()))
                .put("volume", volume.encode().put("enabled", volume.configured()))
                .put("custom", obj);
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
