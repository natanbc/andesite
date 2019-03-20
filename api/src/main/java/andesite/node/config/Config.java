package andesite.node.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Handles loading configurations.
 *
 * <h2>Load Order</h2>
 * Load order can be configured with the {@code andesite.config.load-order} system property.
 * It's value is a comma separated list of {@link Source source} {@link Enum#name() names} to
 * try loading values from. Names in this property are case insensitive.
 * The default value is {@code file,props,env}.
 */
public class Config {
    private static final Logger log = LoggerFactory.getLogger(Config.class);
    private static final String LOAD_ORDER_RAW = System.getProperty("andesite.config.load-order", "file,props,env");
    private static final Source[] LOAD_ORDER = parseLoadOrder();
    @GuardedBy("itself")
    private final Map<String, String> cache = new HashMap<>();
    
    static {
        log.debug("Raw load order: {}", LOAD_ORDER_RAW);
        log.debug("Resolved load order: {}", Arrays.toString(LOAD_ORDER));
    }
    
    @CheckReturnValue
    public long getLong(@Nonnull String key) {
        return getLong(key, 0L);
    }
    
    @CheckReturnValue
    public long getLong(@Nonnull String key, long defaultValue) {
        var v = get(key);
        return v == null ? defaultValue : Long.parseLong(v);
    }
    
    @CheckReturnValue
    public double getDouble(@Nonnull String key) {
        return getDouble(key, 0D);
    }
    
    @CheckReturnValue
    public double getDouble(@Nonnull String key, double defaultValue) {
        var v = get(key);
        return v == null ? defaultValue : Double.parseDouble(v);
    }
    
    @CheckReturnValue
    public int getInt(@Nonnull String key) {
        return getInt(key, 0);
    }
    
    @CheckReturnValue
    public int getInt(@Nonnull String key, int defaultValue) {
        var v = get(key);
        return v == null ? defaultValue : Integer.parseInt(v);
    }
    
    @CheckReturnValue
    public float getFloat(@Nonnull String key) {
        return getFloat(key, 0F);
    }
    
    @CheckReturnValue
    public float getFloat(@Nonnull String key, float defaultValue) {
        var v = get(key);
        return v == null ? defaultValue : Float.parseFloat(v);
    }
    
    @CheckReturnValue
    public boolean getBoolean(@Nonnull String key) {
        return getBoolean(key, false);
    }
    
    @CheckReturnValue
    public boolean getBoolean(@Nonnull String key, boolean defaultValue) {
        var v = get(key);
        return v == null ? defaultValue : "true".equals(v);
    }
    
    @Nonnull
    @CheckReturnValue
    public String require(@Nonnull String key) {
        var v = get(key);
        if(v == null) {
            throw new IllegalArgumentException("Required key " + key + " not available");
        }
        return v;
    }
    
    @Nonnull
    @CheckReturnValue
    public String get(@Nonnull String key, @Nonnull String defaultValue) {
        var v = get(key);
        return v == null ? defaultValue : v;
    }
    
    @Nullable
    @CheckReturnValue
    public String get(@Nonnull String key) {
        var value = cache.get(key);
        if(value == null && !cache.containsKey(key) /* avoid loading if it already contains the value */) {
            synchronized(cache) {
                for(Source source : LOAD_ORDER) {
                    if((value = source.get(this, key)) != null) {
                        break;
                    }
                }
                //cache it
                cache.put(key, value);
            }
        }
        return value;
    }
    
    /**
     * Attempts to load a value for the given key. Subclasses can override this
     * for providing custom ways of loading values, eg from files.
     *
     * @param key Key to load.
     *
     * @return Value for the key. May be null.
     */
    @Nullable
    @CheckReturnValue
    protected String loadValue(@Nonnull String key) {
        return null;
    }
    
    /**
     * Loads a global config with the given name.
     * <br>The load order is the same as configured, except
     * the {@link Source#FILE file} source is ignored.
     *
     * @param key Key to load.
     *
     * @return The value of the global config with the provided key.
     */
    @Nullable
    public static String getGlobalConfig(@Nonnull String key) {
        String value;
        for(Source source : LOAD_ORDER) {
            if(source == Source.FILE) continue;
            if((value = source.get(null, key)) != null) {
                return value;
            }
        }
        return null;
    }
    
    private static Source[] parseLoadOrder() {
        var array = Arrays.stream(LOAD_ORDER_RAW.split(","))
            .map(source -> {
                var key = source.toUpperCase().strip();
                try {
                    return Source.valueOf(key);
                } catch(IllegalArgumentException e) {
                    log.error("Unknown source {}", source);
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .distinct()
            .toArray(Source[]::new);
        if(array.length == 0) {
            log.error("Invalid load order specified, using defaults");
            array = new Source[]{Source.FILE, Source.PROPS, Source.ENV};
        }
        return array;
    }
    
    /**
     * Source used for loading config values.
     */
    private enum Source {
        /**
         * Config instance specific.
         */
        FILE {
            @Nullable
            @CheckReturnValue
            @Override
            String get(Config config, @Nonnull String key) {
                return config.loadValue(key);
            }
        },
        /**
         * Loads from {@code System.getProperty("andesite." + key)}.
         */
        PROPS {
            @Nullable
            @CheckReturnValue
            @Override
            String get(Config config, @Nonnull String key) {
                return System.getProperty("andesite." + key);
            }
        },
        /**
         * Loads from {@code System.getenv("ANDESITE_" + key.replace('.', '_').replace('-', '_').toUpperCase())}.
         */
        ENV {
            @Nullable
            @CheckReturnValue
            @Override
            String get(Config config, @Nonnull String key) {
                return System.getenv("ANDESITE_" + key.replace('.', '_').replace('-', '_').toUpperCase());
            }
        };
        
        @Nullable
        @CheckReturnValue
        abstract String get(Config config, @Nonnull String key);
    }
}
