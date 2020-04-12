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
    private final Map<Class<? extends Config>, Config> filters = new HashMap<>();

    public FilterChainConfiguration() {
        filters.put(equalizer.getClass(), equalizer);
        filters.put(karaoke.getClass(), karaoke);
        filters.put(timescale.getClass(), timescale);
        filters.put(tremolo.getClass(), tremolo);
        filters.put(vibrato.getClass(), vibrato);
        filters.put(volume.getClass(), volume);
    }

    /**
     * Returns true if a configuration of the provided class is present.
     *
     * @param clazz Class of the configuration.
     * @return True if a configuration of the provided class is present.
     */
    public boolean hasConfig(Class<? extends Config> clazz) {
        return filters.containsKey(clazz);
    }

    /**
     * Returns a configuration of the provided class, if it exists. May return null.
     *
     * @param clazz Class of the configuration.
     * @param <T>   Type of the configuration.
     * @return The existing instance, or null if there is none.
     */
    @SuppressWarnings("unchecked")
    @CheckReturnValue
    public <T extends Config> T config(@Nonnull Class<T> clazz) {
        return (T) filters.get(clazz);
    }

    /**
     * Returns a configuration of the provided class if it exists, or creates
     * a new instance of it with the provided supplier.
     *
     * @param clazz    Class of the configuration.
     * @param supplier Supplier for creating a new instance of the configuration.
     * @param <T>      Type of the configuration.
     * @return An instance of the provided class stored. If none is stored, a new
     * one is created and stored.
     */
    @SuppressWarnings("unchecked")
    @CheckReturnValue
    public <T extends Config> T config(@Nonnull Class<T> clazz, @Nonnull Supplier<T> supplier) {
        return (T) filters.computeIfAbsent(clazz, __ -> {
            var config = Objects.requireNonNull(supplier.get(), "Provided configuration may not be null");
            if (!clazz.isInstance(config)) {
                throw new IllegalArgumentException("Config not instance of provided class");
            }
            for (var c : filters.values()) {
                if (c.name().equals(config.name())) {
                    throw new IllegalArgumentException("Duplicate configuration name " + c.name());
                }
            }
            return config;
        });
    }

    /**
     * Returns whether or not this configuration has filters enabled.
     *
     * <br>This method returns true if any of the configurations reports
     * it's enabled.
     *
     * @return True if this configuration is enabled.
     */
    @CheckReturnValue
    public boolean isEnabled() {
        for (var config : filters.values()) {
            if (config.enabled()) return true;
        }
        return false;
    }

    /**
     * Returns a filter factory with the currently enabled filters.
     *
     * <br>If no configuration is enabled, this method returns null.
     *
     * @return A filter factory for the currently enabled filters,
     * or null if none are enabled.
     */
    @Nullable
    @CheckReturnValue
    public PcmFilterFactory factory() {
        return isEnabled() ? new Factory(this) : null;
    }

    /**
     * Encodes the state of this configuration and all filters in it.
     *
     * @return The encoded state.
     */
    @Nonnull
    @CheckReturnValue
    public JsonObject encode() {
        var obj = new JsonObject();
        for (Config config : filters.values()) {
            obj.put(config.name(), config.encode().put("enabled", config.enabled()));
        }
        return obj;
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
            for (var config : configuration.filters.values()) {
                var filter = config.enabled() ?
                        config.create(format, builder.makeFirstFloat(format.channelCount)) //may return null
                        : null;
                if (filter != null) {
                    builder.addFirst(filter);
                }
            }
            var list = builder.build(null, format.channelCount).filters;
            //remove output
            return list.subList(1, list.size());
        }
    }
}
