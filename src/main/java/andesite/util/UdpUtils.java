package andesite.util;

import com.sedmelluq.discord.lavaplayer.natives.NativeLibLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UdpUtils {
    private static final Logger log = LoggerFactory.getLogger(UdpUtils.class);
    private static final String QUEUE_MANAGER_LIBRARY =
            "com.sedmelluq.discord.lavaplayer.udpqueue.natives.UdpQueueManagerLibrary";
    private static Boolean available;
    
    public static boolean isUdpQueueAvailable() {
        if(available != null) {
            return available;
        }
        try {
            NativeLibLoader.load(Class.forName(QUEUE_MANAGER_LIBRARY), "udpqueue");
            log.info("Loaded udp-queue library");
            return available = true;
        } catch(Throwable t) {
            log.warn("Unable to load udp-queue library");
            return available = false;
        }
    }
}
