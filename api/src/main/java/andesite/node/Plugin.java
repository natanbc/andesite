package andesite.node;

import andesite.node.handler.WebSocketState;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import gg.amy.singyeong.Dispatch;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;

/**
 * Entry point for a plugin. Defines the callbacks used to modify the node state
 * or intercept commands.
 */
public interface Plugin {
    /**
     * Called when plugin loading is complete. This method is the first callback to be called
     * and is guaranteed to run only once.
     *
     * <br><br>Blocking in this method is not a problem, but will delay node initialization.
     *
     * @param state State of the node.
     *
     * @see NodeState
     */
    default void init(@Nonnull NodeState state) {}

    /**
     * Called to allow configuration of an audio player manager. May be called more than once with
     * different player managers.
     *
     * <br><b>Plugins should not change the output format of the manager.</b>
     *
     * <br><br>Blocking in this method is not a problem, but will delay node initialization.
     *
     * @param state State of the node.
     * @param manager Player manager to configure.
     *
     * @see NodeState
     * @see AudioPlayerManager
     */
    default void configurePlayerManager(@Nonnull NodeState state, @Nonnull AudioPlayerManager manager) {}

    /**
     * Whether or not this plugin requires a router to work. If this returns true,
     * a router will always be created, regardless of other settings. This
     * method might not be called.
     *
     * <br>Even if this returns false, a router may still be created and
     * {@link #configureRouter(NodeState, Router) configureRouter()} may
     * be called. Returning true only ensures the creation, returning false
     * doesn't stop it.
     *
     * <br>A router will be created if any of these conditions are met
     * <ul>
     *     <li>HTTP or WebSocket transports are enabled</li>
     *     <li>Prometheus metrics are enabled</li>
     *     <li><b>Any</b> plugins returns true on this method</li>
     * </ul>
     *
     * <br><br>Blocking in this method is not a problem, but will delay node initialization.
     *
     * @return Whether or not this plugin requires a router.
     */
    default boolean requiresRouter() {
        return false;
    }

    /**
     * Called to allow injecting custom routes on the provided router.
     * Only called if the HTTP server is enabled (see {@link #requiresRouter() requiresRouter()}
     * for HTTP server creation conditions).
     *
     * <br><br>Blocking in this method is not a problem, but will delay node initialization.
     *
     * @param state State of the node.
     * @param router Root router used by the node.
     *
     * @see NodeState
     * @see Router
     */
    default void configureRouter(@Nonnull NodeState state, @Nonnull Router router) {}

    /**
     * Starts custom listeners for this plugin. If the default listeners are disabled
     * and no plugin has custom listeners, the process will exit.
     *
     * <br><br>Blocking in this method is not a problem, but will delay node initialization.
     *
     * @param state State of the node.
     *
     * @return True if a listener was started.
     *
     * @see NodeState
     */
    default boolean startListeners(@Nonnull NodeState state) {
        return false;
    }

    /**
     * Called when a REST request is received. Runs on the event loop, so <b>blocking should be avoided.</b>
     *
     * @param state State of the node.
     * @param context Context for the request.
     *
     * @return Whether or not other handlers should be called.
     *
     * @see NodeState
     * @see RoutingContext
     * @see HookResult
     */
    @Nonnull
    @CheckReturnValue
    default HookResult onRawHttpRequest(@Nonnull NodeState state, @Nonnull RoutingContext context) {
        return HookResult.CALL_NEXT;
    }

    /**
     * Called when a websocket payload is received. Runs on the event loop, so <b>blocking should be avoided.</b>
     *
     * @param nodeState State of the node.
     * @param wsState State of the websocket connection.
     * @param payload Payload received.
     *
     * @return Whether or not other handlers should be called.
     *
     * @see NodeState
     * @see WebSocketState
     * @see HookResult
     */
    @Nonnull
    @CheckReturnValue
    default HookResult onRawWebSocketPayload(@Nonnull NodeState nodeState, @Nonnull WebSocketState wsState, @Nonnull JsonObject payload) {
        return HookResult.CALL_NEXT;
    }

    /**
     * Called when a singyeong payload is received. Runs on the event loop, so <b>blocking should be avoided.</b>
     *
     * @param state State of the node.
     * @param payload Payload received plus metadata.
     *
     * @return Whether or not other handlers should be called.
     *
     * @see NodeState
     * @see Dispatch
     * @see HookResult
     */
    @Nonnull
    @CheckReturnValue
    default HookResult onRawSingyeongPayload(@Nonnull NodeState state, @Nonnull Dispatch payload) {
        return HookResult.CALL_NEXT;
    }

    /**
     * Used to signal whether or not a payload should be dropped.
     *
     * Dropping a payload allows overriding other handlers or blocking a client.
     */
    enum HookResult {
        /**
         * Call next plugin, or the default andesite code if no more
         * plugins remain.
         */
        CALL_NEXT,
        /**
         * Ignore this event.
         */
        ABORT
    }
}
