package andesite.node.handler;

import io.vertx.core.http.ServerWebSocket;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface WebSocketState {
    String user();

    ServerWebSocket ws();

    long connectionId();

    boolean lavalink();

    @CheckReturnValue
    <T> T get(@Nonnull Key<T> key);

    @Nullable
    <T> T set(@Nonnull Key<T> key, @Nullable T value);

    class Key<T> {
        private final T defaultValue;

        public Key(@Nonnull T defaultValue) {
            this.defaultValue = defaultValue;
        }

        public T defaultValue() {
            return defaultValue;
        }
    }
}
