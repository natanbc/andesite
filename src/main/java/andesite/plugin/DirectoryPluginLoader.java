package andesite.plugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

public class DirectoryPluginLoader extends PluginLoader {
    private final File base;
    
    public DirectoryPluginLoader(File base) {
        this.base = base;
    }
    
    @Nullable
    @Override
    public InputStream openFile(@Nonnull String path) throws IOException {
        var file = new File(base, path);
        if(!(file.exists() && file.isFile() && file.canRead())) {
            return null;
        }
        return new FileInputStream(file);
    }
    
    @Nonnull
    @Override
    public URL baseUrl() {
        try {
            return base.toURI().toURL();
        } catch(MalformedURLException e) {
            throw new AssertionError(e);
        }
    }
    
    @Nullable
    @Override
    public URL createUrl(@Nonnull String path) {
        var file = new File(base, path);
        if(!(file.exists() && file.isFile() && file.canRead())) {
            return null;
        }
        try {
            return file.toURI().toURL();
        } catch(MalformedURLException e) {
            return null;
        }
    }
}
