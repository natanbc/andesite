package andesite.node.event;

import javax.annotation.Nonnull;

public interface EventDispatcher {
    void register(@Nonnull AndesiteEventListener listener);

    void unregister(@Nonnull AndesiteEventListener listener);
}
