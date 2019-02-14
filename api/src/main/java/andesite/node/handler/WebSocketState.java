package andesite.node.handler;

import io.vertx.core.http.ServerWebSocket;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface WebSocketState {
    /**
     * The user id of this socket. This is a default value and may be overriden
     * per-payload.
     *
     * @return The user id.
     */
    String user();

    /**
     * The websocket connection. Can be used to send payloads to clients. All sent payloads
     * sent must be valid json objects.
     *
     * @return The websocket connection.
     */
    ServerWebSocket ws();

    /**
     * The id of this connection. Used for resuming.
     *
     * @return The id of this connection.
     */
    String connectionId();

    /**
     * Whether or not this connection is lavalink compatible.
     *
     * <br>Lavalink resumes are not supported.
     *
     * @return Whether or not this connection is lavalink compatible.
     */
    boolean lavalink();

    /**
     * Gets a value stored in this connection. Similar to ThreadLocal but on
     * a connection scope.
     *
     * <br>If no value is stored, the {@link Key#defaultValue() default} is returned.
     *
     * @param key Key to lookup.
     * @param <T> Type of the object stored.
     *
     * @return The value stored. May be null.
     */
    @CheckReturnValue
    <T> T get(@Nonnull Key<T> key);

    /**
     * Stores a value in this connection. Similar to ThreadLocal but on
     * a connection scope.
     *
     * @param key Key to store.
     * @param value Value to store.
     * @param <T> Type of object to store.
     *
     * @return The old value. May be null.
     */
    @Nullable
    <T> T set(@Nonnull Key<T> key, @Nullable T value);

    /**
     * Key used for connection storage. Equality is implemented as identity equality.
     *
     * <br>A key is only equal to itself.
     *
     * @param <T> Type of object to store.
     */
    final class Key<T> {
        private final T defaultValue;

        public Key(@Nullable T defaultValue) {
            this.defaultValue = defaultValue;
        }

        /**
         * The default value for this key.
         *
         * @return The default value for this key. May be null.
         */
        @CheckReturnValue
        public T defaultValue() {
            return defaultValue;
        }
    }
}
