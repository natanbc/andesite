package andesite.node.plugin;

import andesite.node.NodeState;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.jar.JarFile;

public class JarPluginLoader extends PluginLoader {
    private final JarFile file;
    private final URL base;
    
    public JarPluginLoader(NodeState state, File file) throws IOException {
        this.file = new JarFile(file);
        this.base = new URL("jar:file://" + file.toURI().getPath() + "!/");
        state.cleaner().register(this, cleanerCode(this.file));
    }
    
    @Nullable
    @Override
    public InputStream openFile(@Nonnull String path) throws IOException {
        var entry = file.getEntry(path);
        if(entry == null) {
            return null;
        }
        return file.getInputStream(entry);
    }
    
    @Nonnull
    @Override
    public URL baseUrl() {
        return base;
    }
    
    @Nullable
    @Override
    public URL createUrl(@Nonnull String path) {
        if(file.getEntry(path) == null) {
            return null;
        }
        try {
            return new URL(base.toString() + (path.startsWith("/") ? path.substring(1) : path));
        } catch(MalformedURLException e) {
            return null;
        }
    }
    
    @Nonnull
    @CheckReturnValue
    private static Runnable cleanerCode(@Nonnull JarFile jar) {
        return () -> {
            try {
                jar.close();
            } catch(IOException ignored) {
            }
        };
    }
}
