package andesite.node;

import andesite.node.event.EventDispatcher;
import andesite.node.handler.WebSocketState;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import gg.amy.singyeong.Dispatch;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;

public interface Plugin {
    default void configurePlayerManager(@Nonnull AudioPlayerManager manager) {}

    default void configureRouter(@Nonnull Router router) {}

    default void registerListeners(@Nonnull EventDispatcher dispatcher) {}

    @Nonnull
    @CheckReturnValue
    default HookResult onRawHttpRequest(@Nonnull RoutingContext context) {
        return HookResult.CALL_NEXT;
    }

    @Nonnull
    @CheckReturnValue
    default HookResult onRawWebSocketPayload(@Nonnull WebSocketState state, @Nonnull JsonObject payload) {
        return HookResult.CALL_NEXT;
    }

    @Nonnull
    @CheckReturnValue
    default HookResult onRawSingyeongPayload(@Nonnull Dispatch payload) {
        return HookResult.CALL_NEXT;
    }

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
