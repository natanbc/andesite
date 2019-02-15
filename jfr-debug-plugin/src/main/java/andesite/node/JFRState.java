package andesite.node;

import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

public class JFRState {
    private static volatile Recording current;

    @Nullable
    @CheckReturnValue
    public static synchronized Recording createNew() {
        if(current != null) return null;
        var r = new Recording();
        for(var t : FlightRecorder.getFlightRecorder().getEventTypes()) {
            r.disable(t.getName());
        }
        return current = r;
    }

    @Nullable
    @CheckReturnValue
    public static synchronized Recording stop() {
        var r = current;
        if(r == null) return null;
        r.stop();
        current = null;
        return r;
    }

    @Nullable
    @CheckReturnValue
    public static synchronized Recording current() {
        return current;
    }
}
