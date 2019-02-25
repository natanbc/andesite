package andesite.node.plugin;

import andesite.node.NodeState;
import andesite.node.Plugin;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.Permissions;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class PluginLoader extends ClassLoader {
    private static final Logger log = LoggerFactory.getLogger(PluginLoader.class);
    
    @Nullable
    @CheckReturnValue
    public abstract InputStream openFile(@Nonnull String path) throws IOException;
    
    @Nonnull
    @CheckReturnValue
    public abstract URL baseUrl();
    
    @Nullable
    @CheckReturnValue
    public abstract URL createUrl(@Nonnull String path);
    
    @Nonnull
    @CheckReturnValue
    public List<Plugin> loadPlugins() {
        var manifest = loadManifest();
        if(manifest == null) {
            return Collections.emptyList();
        }
        var p = manifest.getJsonArray("classes");
        if(p == null) {
            throw new IllegalArgumentException("Missing 'classes' array from manifest");
        }
        var list = new ArrayList<Plugin>();
        for(var v : p) {
            if(!(v instanceof String)) {
                throw new IllegalArgumentException("All elements in the 'classes' array must be strings");
            }
            log.info("Loading plugin {}", v);
            try {
                var c = loadClass((String) v);
                var ctor = c.getDeclaredConstructor();
                ctor.setAccessible(true);
                list.add((Plugin) ctor.newInstance());
            } catch(Exception e) {
                throw new IllegalStateException("Unable to load plugin " + v, e);
            }
        }
        return list;
    }
    
    @Nullable
    @CheckReturnValue
    private JsonObject loadManifest() {
        try {
            var is = openFile("manifest.json");
            if(is == null) return null;
            return new JsonObject(new String(is.readAllBytes(), StandardCharsets.UTF_8));
        } catch(IOException ignored) {
            return null;
        } catch(Exception e) {
            throw new IllegalArgumentException("Malformed manifest", e);
        }
    }
    
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        try(var is = openFile(name.replace('.', '/') + ".class")) {
            if(is == null) {
                return super.findClass(name);
            }
            var bytes = is.readAllBytes();
            return defineClass(name, bytes, 0, bytes.length,
                new ProtectionDomain(new CodeSource(baseUrl(),
                    (CodeSigner[]) null), new Permissions()));
        } catch(IOException e) {
            throw new ClassNotFoundException(name, e);
        }
    }
    
    @Override
    protected URL findResource(String name) {
        return createUrl(name);
    }
    
    @Nonnull
    @CheckReturnValue
    public static PluginLoader create(NodeState state, File file) throws IOException {
        if(file.isDirectory()) {
            return new DirectoryPluginLoader(file);
        }
        if(file.isFile() && file.canRead() && file.getName().endsWith(".jar")) {
            return new JarPluginLoader(state, file);
        }
        throw new IllegalArgumentException("Unable to load " + file + ": no suitable loader found");
    }
}
