package andesite.node.plugin;

import andesite.node.NodeState;
import andesite.node.Plugin;
import andesite.node.event.EventDispatcher;
import andesite.node.handler.WebSocketState;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import gg.amy.singyeong.Dispatch;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PluginManager {
    private final List<Plugin> plugins = new ArrayList<>();
    private final List<String> loadedPlugins = new ArrayList<>();
    private final NodeState state;

    public PluginManager(@Nonnull NodeState state) {
        this.state = state;
    }

    @Nonnull
    @CheckReturnValue
    public List<String> loadedPlugins() {
        return loadedPlugins;
    }

    public void load(@Nonnull File path) throws IOException {
        var p = PluginLoader.create(state, path).loadPlugins();
        plugins.addAll(p);
        for(var plugin : p) {
            loadedPlugins.add(plugin.getClass().getName());
        }
    }

    public void configureRouter(@Nonnull Router router) {
        for(var p : plugins) {
            p.configureRouter(state, router);
        }
    }

    public void configurePlayerManager(@Nonnull AudioPlayerManager manager) {
        for(var p : plugins) {
            p.configurePlayerManager(state, manager);
        }
    }

    public void registerListeners(@Nonnull EventDispatcher dispatcher) {
        for(var p : plugins) {
            p.registerListeners(state, dispatcher);
        }
    }

    public boolean customHandleHttpRequest(@Nonnull RoutingContext context) {
        for(var p : plugins) {
            if(p.onRawHttpRequest(state, context) == Plugin.HookResult.ABORT) return true;
        }
        return false;
    }

    public boolean customHandleWebSocketPayload(@Nonnull WebSocketState state, @Nonnull JsonObject payload) {
        for(var p : plugins) {
            if(p.onRawWebSocketPayload(this.state, state, payload) == Plugin.HookResult.ABORT) return true;
        }
        return false;
    }

    public boolean customHandleSingyeongPayload(@Nonnull Dispatch dispatch) {
        for(var p : plugins) {
            if(p.onRawSingyeongPayload(state, dispatch) == Plugin.HookResult.ABORT) return true;
        }
        return false;
    }
}
