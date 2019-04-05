package andesite.node.util;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Paths;

public class ConfigUtil {
    private static final Logger log = LoggerFactory.getLogger(ConfigUtil.class);
    
    public static Config load() {
        var defaultConfig = ConfigFactory.systemEnvironment()
                .withFallback(ConfigFactory.systemProperties())
                .withFallback(ConfigFactory.parseResources("reference.conf"));
        var path = Paths.get("application.conf");
        if(Files.isReadable(path)) {
            log.info("Loading config from {}", path.toAbsolutePath());
            return ConfigFactory.parseFile(path.toFile()).withFallback(defaultConfig).resolve();
    
        }
        return defaultConfig.resolve();
    }
}
