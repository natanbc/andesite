package andesite.node.util;

import andesite.node.Version;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Paths;

public class ConfigUtil {
    private static final Logger log = LoggerFactory.getLogger(ConfigUtil.class);

    public static Config load() {
        var defaultConfig = ConfigFactory.empty()
                .withValue("andesite.version.string", ConfigValueFactory.fromAnyRef(Version.VERSION))
                .withValue("andesite.version.major", ConfigValueFactory.fromAnyRef(Version.VERSION_MAJOR))
                .withValue("andesite.version.minor", ConfigValueFactory.fromAnyRef(Version.VERSION_MINOR))
                .withValue("andesite.version.revision", ConfigValueFactory.fromAnyRef(Version.VERSION_REVISION))
                .withValue("andesite.version.commit", ConfigValueFactory.fromAnyRef(Version.COMMIT))
                .withValue("andesite.version.build-number", ConfigValueFactory.fromAnyRef(Version.BUILD_NUMBER))
                .withFallback(
                        ConfigFactory.systemEnvironment()
                                .withFallback(ConfigFactory.systemProperties())
                                .withFallback(ConfigFactory.parseResources("reference.conf"))
                );
        var path = Paths.get("application.conf");
        if (Files.isReadable(path)) {
            log.info("Loading config from {}", path.toAbsolutePath());
            return ConfigFactory.parseFile(path.toFile()).withFallback(defaultConfig).resolve();
        }
        return defaultConfig.resolve();
    }
}
