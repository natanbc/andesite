package andesite.node.util;

import com.github.natanbc.lavadsp.natives.KaraokeNativeLibLoader;
import com.github.natanbc.lavadsp.natives.TimescaleNativeLibLoader;
import com.github.natanbc.lavadsp.natives.TremoloNativeLibLoader;
import com.github.natanbc.lavadsp.natives.VibratoNativeLibLoader;
import com.github.natanbc.lavadsp.natives.VolumeNativeLibLoader;

public class FilterUtil {
    public static final boolean KARAOKE_AVAILABLE = tryLoad(KaraokeNativeLibLoader::loadKaraokeLibrary);
    public static final boolean TIMESCALE_AVAILABLE = tryLoad(TimescaleNativeLibLoader::loadTimescaleLibrary);
    public static final boolean TREMOLO_AVAILABLE = tryLoad(TremoloNativeLibLoader::loadTremoloLibrary);
    public static final boolean VIBRATO_AVAILABLE = tryLoad(VibratoNativeLibLoader::loadVibratoLibrary);
    public static final boolean VOLUME_AVAILABLE = tryLoad(VolumeNativeLibLoader::loadVolumeLibrary);

    private static boolean tryLoad(Runnable load) {
        try {
            load.run();
            return true;
        } catch(UnsatisfiedLinkError error) {
            return false;
        }
    }
}
