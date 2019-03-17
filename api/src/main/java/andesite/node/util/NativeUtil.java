package andesite.node.util;

import andesite.node.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class NativeUtil {
    private static final Logger log = LoggerFactory.getLogger(NativeUtil.class);
    
    public static final AVXMode AVX_MODE = detectAvx();
    
    public enum AVXMode {
        FORCED, ENABLED, DISABLED
    }
    
    private static AVXMode detectAvx() {
        if(Boolean.parseBoolean(Config.getGlobalConfig("avx2.force"))) {
            return AVXMode.FORCED;
        }
        var config = Config.getGlobalConfig("avx2.enabled");
        var enabled = config == null || Boolean.parseBoolean(config);
        if(!enabled) {
            return AVXMode.DISABLED;
        }
        //try reading from /proc/cpuinfo
        var cpuInfo = Path.of("/proc/cpuinfo");
        if(Files.exists(cpuInfo)) {
            try {
                return Files.lines(cpuInfo)
                    .filter(line -> line.startsWith("flags"))
                    .allMatch(NativeUtil::hasAvx2) ? AVXMode.ENABLED : AVXMode.DISABLED;
            } catch(IOException e) {
                log.error("Unable to read /proc/cpuinfo, falling back to configuration", e);
            }
        }
        return AVXMode.ENABLED;
    }
    
    private static boolean hasAvx2(String flags) {
        return Arrays.asList(flags.substring(flags.indexOf(':')).split("\\s+")).contains("avx2");
    }
}
