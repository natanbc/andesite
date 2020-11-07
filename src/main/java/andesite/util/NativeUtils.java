package andesite.util;

import com.github.natanbc.lavadsp.natives.TimescaleNativeLibLoader;
import com.github.natanbc.nativeloader.NativeLibLoader;
import com.sedmelluq.discord.lavaplayer.natives.ConnectorNativeLibLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;

public class NativeUtils {
    private static final Logger log = LoggerFactory.getLogger(NativeUtils.class);
    private static final String QUEUE_MANAGER_LIBRARY_NAME =
            "com.sedmelluq.discord.lavaplayer.udpqueue.natives.UdpQueueManagerLibrary";
    private static final String LOAD_RESULT_NAME =
            "com.sedmelluq.lava.common.natives.NativeLibraryLoader$LoadResult";
    private static final NativeLibLoader UDP_QUEUE_LOADER = NativeLibLoader.create(
            NativeUtils.class, "udpqueue"
    );
    private static final NativeLibLoader CONNECTOR_LOADER = NativeLibLoader.create(
            NativeUtils.class, "connector"
    );
    private static final Object LOAD_RESULT;
    private static Boolean udpQueueAvailable;
    
    static {
        Object loadResult;
        try {
            var ctor = Class.forName(LOAD_RESULT_NAME)
                    .getDeclaredConstructor(boolean.class, RuntimeException.class);
            ctor.setAccessible(true);
            loadResult = ctor.newInstance(true, null);
        } catch(ReflectiveOperationException e) {
            log.error("Unable to create successful load result");
            loadResult = null;
        }
        LOAD_RESULT = loadResult;
    }
    
    public static void tryLoad() {
        tryLoadConnector();
        isUdpQueueAvailable();
        tryLoadTimescale();
    }
    
    public static void tryLoadTimescale() {
        try {
            TimescaleNativeLibLoader.loadTimescaleLibrary();
            log.info("Loaded timescale");
        } catch(Throwable t) {
            log.warn("Error loading timescale", t);
        }
    }
    
    public static void tryLoadConnector() {
        try {
            /*
             * Load the lp-cross version of the library, then mark lavaplayer's loader
             * as loaded to avoid failing when loading mpg123 on windows/attempting to load
             * connector again
             */
            CONNECTOR_LOADER.load();
            var loadersField = ConnectorNativeLibLoader.class.getDeclaredField("loaders");
            loadersField.setAccessible(true);
            for(int i = 0; i < 2; i++) {
                markLoaded(Array.get(loadersField.get(null), i));
            }
            log.info("Loaded connector");
        } catch(Throwable t) {
            log.warn("Error loading connector", t);
        }
    }
    
    public static boolean isUdpQueueAvailable() {
        if(udpQueueAvailable != null) {
            return udpQueueAvailable;
        }
        try {
            /*
             * Load the lp-cross version of the library, then mark jda-nas' loader
             * as loaded to avoid failing attempting to load udp-queue
             */
            UDP_QUEUE_LOADER.load();
            var loaderField = Class.forName(QUEUE_MANAGER_LIBRARY_NAME)
                                      .getDeclaredField("nativeLoader");
            loaderField.setAccessible(true);
            markLoaded(loaderField.get(null));
            log.info("Loaded udp-queue library");
            return udpQueueAvailable = true;
        } catch(Throwable t) {
            log.warn("Error loading udp-queue", t);
            return udpQueueAvailable = false;
        }
    }
    
    private static void markLoaded(Object loader) throws ReflectiveOperationException {
        var previousResultField = loader.getClass().getDeclaredField("previousResult");
        previousResultField.setAccessible(true);
        previousResultField.set(loader, LOAD_RESULT);
    }
}
