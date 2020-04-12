package andesite.node.plugin;

import andesite.node.NodeState;
import andesite.node.Plugin;
import andesite.node.handler.WebSocketState;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import gg.amy.singyeong.data.Dispatch;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PluginManager {
    private static final Logger log = LoggerFactory.getLogger(PluginManager.class);

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
        log.info("Attempting to find plugin class {}", name);
        for (var loader : loaders) {
            log.debug("Attempting to load class {} from loader {}", name, loader);
            try {
                return loader.loadClass(name);
            } catch (ClassNotFoundException ignored) {
            }
        }
        throw new ClassNotFoundException(name);
    }

    @Nonnull
    public <T> T loadHandler(@Nonnull Class<T> type, @Nonnull String name) {
        log.info("Loading handler {} of type {}", name, type.getName());
        try {
            var c = findClass(name);
            if (!type.isAssignableFrom(c)) {
                throw new IllegalArgumentException("Class " + name + " is not a subtype of " + type.getName());
            }
            return c.asSubclass(type).getConstructor(NodeState.class).newInstance(state);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Unable to find handler with name " + name);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Handler class does not have a constructor accepting a NodeState object");
        } catch (InstantiationException e) {
            throw new IllegalArgumentException("Unable to instantiate handler class", e);
        } catch (InvocationTargetException e) {
            throw new IllegalArgumentException("Unable to instantiate handler class", e.getCause());
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    public void load(@Nonnull File path) throws IOException {
        var loader = PluginLoader.create(state, path);
        loaders.add(loader);
        var p = loader.loadPlugins();
        plugins.addAll(p);
        for (var plugin : p) {
            loadedPlugins.add(plugin.getClass().getName());
        }
    }

    public Config applyPluginDefaults(@Nonnull Config config) {
        for (var l : loaders) {
            if (l.hasFile("reference.conf")) {
                log.info("Loading reference.conf from {}", l);
                try (var reader = new BufferedReader(new InputStreamReader(
                        Objects.requireNonNull(l.openFile("reference.conf")))
                )) {
                    var reference = ConfigFactory.parseReader(reader);
                    log.debug("Loaded reference.conf from {}: {}", l, reference.root().render());
                    config = config.withFallback(reference.resolveWith(config));
                } catch (IOException e) {
                    throw new IllegalStateException("reference.conf exists but cannot be read", e);
                }
            }
        }
        return config;
    }

    public void init() {
        for (var p : plugins) {
            log.debug("Initializing plugin {}", p);
            p.init(state);
        }
    }

    public void configurePlayerManager(@Nonnull AudioPlayerManager manager) {
        for (var p : plugins) {
            log.debug("Configuring player manager {} with plugin {}", manager, p);
            p.configurePlayerManager(state, manager);
        }
    }

    public boolean requiresRouter() {
        for (var p : plugins) {
            log.debug("Checking if plugin {} requires router", p);
            if (p.requiresRouter()) {
                return true;
            }
        }
        return false;
    }

    public void configureRouter(@Nonnull Router router) {
        for (var p : plugins) {
            log.debug("Configuring router with plugin {}", p);
            p.configureRouter(state, router);
        }
    }

    public boolean startListeners() {
        var started = false;
        for (var p : plugins) {
            log.debug("Starting listeners from plugin {}", p);
            started |= p.startListeners(state);
        }
        return started;
    }

    public boolean customHandleHttpRequest(@Nonnull RoutingContext context) {
        for (var p : plugins) {
            log.debug("Calling custom http handling of plugin {}", p);
            if (p.onRawHttpRequest(state, context) == Plugin.HookResult.ABORT) {
                log.debug("Request handled");
                return true;
            }
        }
        return false;
    }

    public boolean customHandleWebSocketPayload(@Nonnull WebSocketState state, @Nonnull JsonObject payload) {
        for (var p : plugins) {
            log.debug("Calling custom ws handling of plugin {}", p);
            if (p.onRawWebSocketPayload(this.state, state, payload) == Plugin.HookResult.ABORT) {
                log.debug("Payload handled");
                return true;
            }
        }
        return false;
    }

    public boolean customHandleSingyeongPayload(@Nonnull Dispatch dispatch) {
        for (var p : plugins) {
            log.debug("Calling custom singyeong handling of plugin {}", p);
            if (p.onRawSingyeongPayload(state, dispatch) == Plugin.HookResult.ABORT) {
                log.debug("Payload handled");
                return true;
            }
        }
        return false;
    }
}
