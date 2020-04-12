package andesite.node.util;


import com.github.natanbc.lavadsp.natives.TimescaleNativeLibLoader;

/**
 * Utility class listing what filters are available. Unavailable filters may still be enabled,
 * but won't be applied.
 */
public class FilterUtil {
    public static final boolean TIMESCALE_AVAILABLE = tryLoad(TimescaleNativeLibLoader::loadTimescaleLibrary);

    private static boolean tryLoad(Runnable load) {
        try {
            load.run();
            return true;
        } catch (Throwable error) {
            return false;
        }
    }
}
