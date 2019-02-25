package andesite.node.util;

import java.util.Optional;
import java.util.function.Supplier;

public class LazyInit<T> implements Supplier<T> {
    private final Supplier<T> supplier;
    private volatile T value;
    
    public LazyInit(Supplier<T> supplier) {
        this.supplier = supplier;
    }
    
    public boolean isPresent() {
        return value != null;
    }
    
    public synchronized Optional<T> getIfPresent() {
        return Optional.ofNullable(value);
    }
    
    @Override
    public synchronized T get() {
        if(value == null) {
            value = supplier.get();
        }
        return value;
    }
}
