package andesite.node.util;

import sun.misc.Unsafe;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;

@FunctionalInterface
public interface ByteArrayProvider {
    @Nonnull
    @CheckReturnValue
    byte[] provide(@Nonnegative int size);
    
    //tested with jdk 11+28, will probably work with any hotspot compatible
    //array layout
    //don't complain if it crashes - this code modifies internal jvm data
    //in completely unsupported ways
    @Nonnull
    @CheckReturnValue
    static ByteArrayProvider reuseExisting(@Nonnegative int maxSize) {
        Unsafe unsafe;
        try {
            var c = Unsafe.class.getDeclaredConstructor();
            c.setAccessible(true);
            unsafe = c.newInstance();
        } catch(Exception e) {
            //should be impossible, unsafe is in the jdk.unsupported module,
            //which exposes the sun.misc package - `opens sun.misc;`
            throw new AssertionError(e);
        }
        byte[] array = new byte[maxSize];
        var offset = unsafe.arrayBaseOffset(byte[].class) - 4;
        return size -> {
            //let's not crash the vm
            if(size > maxSize) {
                throw new IllegalStateException("Size exceeds array length");
            }
            if(size < 0) {
                throw new NegativeArraySizeException();
            }
            unsafe.putIntVolatile(array, offset, size);
            //avoid reordering array length reads with the change above
            unsafe.fullFence();
            return array;
        };
    }
    
    @Nonnull
    @CheckReturnValue
    static ByteArrayProvider createNew() {
        return byte[]::new;
    }
}
