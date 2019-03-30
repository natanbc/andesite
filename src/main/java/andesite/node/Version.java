package andesite.node;

@SuppressWarnings({"ConstantConditions", "unused", "WeakerAccess"})
public class Version {
    public static final String VERSION_MAJOR = "@VERSION_MAJOR@";
    public static final String VERSION_MINOR = "@VERSION_MINOR@";
    public static final String VERSION_REVISION = "@VERSION_REVISION@";
    
    public static final String VERSION;
    public static final String COMMIT = "@COMMIT@";
    
    static {
        VERSION = VERSION_MAJOR.startsWith("@") ? "dev" : VERSION_MAJOR + "." +
                VERSION_MINOR + "." + VERSION_REVISION;
    }
}
