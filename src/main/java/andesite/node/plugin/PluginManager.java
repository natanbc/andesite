package andesite.node.plugin;

import andesite.node.Plugin;
import andesite.node.event.EventDispatcher;
import andesite.node.handler.WebSocketState;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import gg.amy.singyeong.Dispatch;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PluginManager {
    private final List<Plugin> plugins = new ArrayList<>();

    public void load(File path) throws IOException {
        plugins.addAll(PluginLoader.create(path).loadPlugins());
    }

    public void configureRouter(@Nonnull Router router) {
        for(var p : plugins) {
            p.configureRouter(router);
        }
    }

    public void configurePlayerManager(@Nonnull AudioPlayerManager manager) {
        for(var p : plugins) {
            p.configurePlayerManager(manager);
        }
    }

    public void registerListeners(@Nonnull EventDispatcher dispatcher) {
        for(var p : plugins) {
            p.registerListeners(dispatcher);
        }
    }

    public boolean customHandleHttpRequest(@Nonnull RoutingContext context) {
        for(var p : plugins) {
            if(p.onRawHttpRequest(context) == Plugin.HookResult.ABORT) return true;
        }
        return false;
    }

    public boolean customHandleWebSocketPayload(@Nonnull WebSocketState state, @Nonnull JsonObject payload) {
        for(var p : plugins) {
            if(p.onRawWebSocketPayload(state, payload) == Plugin.HookResult.ABORT) return true;
        }
        return false;
    }

    public boolean customHandleSingyeongPayload(@Nonnull Dispatch dispatch) {
        for(var p : plugins) {
            if(p.onRawSingyeongPayload(dispatch) == Plugin.HookResult.ABORT) return true;
        }
        return false;
    }
}
