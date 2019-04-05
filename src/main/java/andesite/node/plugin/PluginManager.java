package andesite.node.plugin;

import andesite.node.NodeState;
import andesite.node.Plugin;
import andesite.node.handler.WebSocketState;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import gg.amy.singyeong.Dispatch;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

public class PluginManager {
    private final List<PluginLoader> loaders = new ArrayList<>();
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
    
    @Nonnull
    public Class<?> findClass(@Nonnull String name) throws ClassNotFoundException {
        for(var loader : loaders) {
            try {
                return loader.loadClass(name);
            } catch(ClassNotFoundException ignored) {
            }
        }
        throw new ClassNotFoundException(name);
    }
    
    @Nonnull
    public <T> T loadHandler(@Nonnull Class<T> type, @Nonnull String name) {
        try {
            var c = findClass(name);
            if(!type.isAssignableFrom(c)) {
                throw new IllegalArgumentException("Class " + name + " not found on any plugins");
            }
            return c.asSubclass(type).getConstructor(NodeState.class).newInstance(state);
        } catch(ClassNotFoundException e) {
            throw new IllegalArgumentException("Unable to find handler with name " + name);
        } catch(NoSuchMethodException e) {
            throw new IllegalArgumentException("Handler class does not have a constructor accepting a NodeState object");
        } catch(InstantiationException e) {
            throw new IllegalArgumentException("Unable to instantiate handler class", e);
        } catch(InvocationTargetException e) {
            throw new IllegalArgumentException("Unable to instantiate handler class", e.getCause());
        } catch(IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }
    
    public void load(@Nonnull File path) throws IOException {
        var loader = PluginLoader.create(state, path);
        loaders.add(loader);
        var p = loader.loadPlugins();
        plugins.addAll(p);
        for(var plugin : p) {
            loadedPlugins.add(plugin.getClass().getName());
        }
    }
    
    public Config applyPluginDefaults(@Nonnull Config config) {
        for(var l : loaders) {
            if(l.hasFile("reference.conf")) {
                config = config.withFallback(ConfigFactory.parseResources(l, "reference.conf"));
            }
        }
        return config;
    }
    
    public void init() {
        for(var p : plugins) {
            p.init(state);
        }
    }
    
    public void configurePlayerManager(@Nonnull AudioPlayerManager manager) {
        for(var p : plugins) {
            p.configurePlayerManager(state, manager);
        }
    }
    
    public boolean requiresRouter() {
        for(var p : plugins) {
            if(p.requiresRouter()) {
                return true;
            }
        }
        return false;
    }
    
    public void configureRouter(@Nonnull Router router) {
        for(var p : plugins) {
            p.configureRouter(state, router);
        }
    }
    
    public boolean startListeners() {
        var started = false;
        for(var p : plugins) {
            started |= p.startListeners(state);
        }
        return started;
    }
    
    public boolean customHandleHttpRequest(@Nonnull RoutingContext context) {
        for(var p : plugins) {
            if(p.onRawHttpRequest(state, context) == Plugin.HookResult.ABORT) {
                return true;
            }
        }
        return false;
    }
    
    public boolean customHandleWebSocketPayload(@Nonnull WebSocketState state, @Nonnull JsonObject payload) {
        for(var p : plugins) {
            if(p.onRawWebSocketPayload(this.state, state, payload) == Plugin.HookResult.ABORT) {
                return true;
            }
        }
        return false;
    }
    
    public boolean customHandleSingyeongPayload(@Nonnull Dispatch dispatch) {
        for(var p : plugins) {
            if(p.onRawSingyeongPayload(state, dispatch) == Plugin.HookResult.ABORT) {
                return true;
            }
        }
        return false;
    }
}
