package andesite.node.config;

import io.vertx.core.json.JsonObject;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads configs from a json file. The file must be a valid json <b>object</b>.
 * All values in this file must be primitives, strings or json objects. Arrays
 * are silently ignored.
 */
class JsonConfig extends Config {
    private JsonObject json;

    public JsonConfig(Path configPath) throws IOException {
        this.json = new JsonObject(Files.readString(configPath, StandardCharsets.UTF_8));
    }

    @Override
    @Nullable
    @CheckReturnValue
    protected String loadValue(@Nonnull String key) {
        return get(json, key.split("\\."), 0);
    }

    @Nullable
    private static String get(@Nonnull JsonObject object, @Nonnull String[] parts, @Nonnegative int idx) {
        if(idx == parts.length - 1) {
            var v = object.getValue(parts[idx], null);
            return v == null ? null : v.toString();
        }
        Object obj = object.getValue(parts[idx], null);
        return obj instanceof JsonObject ? get((JsonObject)obj, parts, idx + 1) : null;
    }
}
