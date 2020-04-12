package andesite.node;

@SuppressWarnings({"ConstantConditions", "unused"})
public class Version {
    public static final String VERSION_MAJOR = "@VERSION_MAJOR@";
    public static final String VERSION_MINOR = "@VERSION_MINOR@";
    public static final String VERSION_REVISION = "@VERSION_REVISION@";

    public static final String VERSION;
    public static final String COMMIT = "@COMMIT@";
    public static final int BUILD_NUMBER = parseBuildNumber();

    static {
        VERSION = VERSION_MAJOR.startsWith("@") ? "0.0.0-dev" : VERSION_MAJOR + "." +
                VERSION_MINOR + "." + VERSION_REVISION;
    }

    private static int parseBuildNumber() {
        try {
            return Integer.parseInt("@BUILD_NUMBER@");
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
