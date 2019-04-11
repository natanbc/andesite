package andesite.node.util.metadata;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MetadataEntry {
    private final MetadataType type;
    private final Object value;
    
    private MetadataEntry(@Nonnull MetadataType type, @Nonnull Object value) {
        this.type = type;
        this.value = value;
    }
    
    @Nonnull
    @CheckReturnValue
    public MetadataType type() {
        return type;
    }
    
    @Nonnull
    @CheckReturnValue
    public Object rawValue() {
        return value;
    }
    
    @Nonnull
    @CheckReturnValue
    public String asString() {
        checkType(MetadataType.STRING, MetadataType.VERSION);
        return (String) value;
    }
    
    @CheckReturnValue
    public int asInteger() {
        checkType(MetadataType.INTEGER);
        return (int) value;
    }
    
    @Nonnull
    @CheckReturnValue
    @SuppressWarnings("unchecked")
    public List<String> asStringList() {
        checkType(MetadataType.STRING_LIST);
        return (List<String>) value;
    }
    
    @Nonnull
    @CheckReturnValue
    public String asVersion() {
        checkType(MetadataType.VERSION);
        return (String) value;
    }
    
    @Nonnull
    @CheckReturnValue
    public static MetadataEntry string(@Nonnull String value) {
        return new MetadataEntry(MetadataType.STRING, value);
    }
    
    @Nonnull
    @CheckReturnValue
    public static MetadataEntry integer(int value) {
        return new MetadataEntry(MetadataType.INTEGER, value);
    }
    
    @Nonnull
    @CheckReturnValue
    public static MetadataEntry stringList(@Nonnull Collection<String> value) {
        return new MetadataEntry(MetadataType.STRING_LIST, value instanceof List ? value : new ArrayList<>(value));
    }
    
    @Nonnull
    @CheckReturnValue
    public static MetadataEntry version(@Nonnull String value) {
        return new MetadataEntry(MetadataType.VERSION, value);
    }
    
    @Override
    public int hashCode() {
        return value.hashCode();
    }
    
    @Override
    public boolean equals(Object obj) {
        return obj instanceof MetadataEntry && value.equals(((MetadataEntry) obj).value);
    }
    
    @Override
    public String toString() {
        switch(type) {
            case STRING:
            case VERSION:
                return (String) value;
            case INTEGER:
                return value.toString();
            case STRING_LIST:
                return String.join(",", asStringList());
            default:
                throw new AssertionError();
        }
    }
    
    private void checkType(@Nonnull MetadataType type) {
        if(this.type != type) {
            throw new IllegalStateException("Expected type to be " + type + ", was " + this.type);
        }
    }
    
    private void checkType(@Nonnull MetadataType t1, @Nonnull MetadataType t2) {
        if(this.type != t1 && this.type != t2) {
            throw new IllegalStateException("Expected type to be " + t1 + " or " + t2 + ", was " + this.type);
        }
    }
}
