package andesite.node;

@SuppressWarnings({"ConstantConditions", "unused", "WeakerAccess"})
public class Version {
    private static final String VERSION_MAJOR = "@VERSION_MAJOR@";
    private static final String VERSION_MINOR = "@VERSION_MINOR@";
    private static final String VERSION_REVISION = "@VERSION_REVISION@";

    public static final String VERSION;

    static {
        VERSION = VERSION_MAJOR.startsWith("@") ? "dev" : VERSION_MAJOR + "." + VERSION_MINOR + "." + VERSION_REVISION;
    }
}
