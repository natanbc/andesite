package andesite.util;

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
        //some dependency has LP's NativeLibLoader available on the compile classpath
        //but gradle's dependency resolution ends up removing that, so this would silently
        //fail because it couldn't find NativeLibLoader, so just call directly the
        //queue manager library to trigger the load. no native resources are allocated
        //by the getInstance call itself, so this won't leak resources.
        try {
            var klass = Class.forName(QUEUE_MANAGER_LIBRARY);
            var method = klass.getDeclaredMethod("getInstance");
            method.setAccessible(true);
            method.invoke(null);
            log.info("Loaded udp-queue library");
            return available = true;
        } catch(Throwable t) {
            log.debug("Error loading udp-queue", t);
            log.warn("Unable to load udp-queue library");
            return available = false;
        }
    }
}
