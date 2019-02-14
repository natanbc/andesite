package andesite.node.util;

import andesite.node.config.Config;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ConfigUtil {
    private static final Logger log = LoggerFactory.getLogger(ConfigUtil.class);

    public static Config load() throws IOException {
        var filename = Config.getGlobalConfig("config-file");
        var path = Paths.get(filename == null ? "config.json" : filename);
        if(Files.isReadable(path)) {
            log.info("Loading config from {}", path.toAbsolutePath());
            return new JsonConfig(path);
        }
        return new Config();
    }

    private static class JsonConfig extends Config {
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
}
