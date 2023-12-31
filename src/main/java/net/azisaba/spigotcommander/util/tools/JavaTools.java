package net.azisaba.spigotcommander.util.tools;

import org.jetbrains.annotations.Nullable;

import javax.tools.ToolProvider;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * "This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice."
 */
public final class JavaTools {
    public static final String TOOLS_JAR_PATH;
    public static final Throwable UNAVAILABLE_REASON;

    static {
        String path = System.getProperty("net.azisaba.spigotcommander.toolspath");
        File file;
        if (path == null || !new File(path).exists()) {
            String home = System.getProperty("java.home");
            file = new File(path = home + "/../lib/tools.jar");
        } else {
            file = new File(path);
        }
        TOOLS_JAR_PATH = path;
        UNAVAILABLE_REASON = isLoaded() ? null : load(file);
    }

    public static boolean isLoaded() {
        if (UNAVAILABLE_REASON != null) return false;
        try {
            return ToolProvider.getSystemJavaCompiler() != null;
        } catch (NoClassDefFoundError ex) {
            return false;
        }
    }

    @Nullable
    private static Throwable load(File file) {
        if (isLoaded()) return null;
        if (!file.exists()) return new UnsupportedOperationException("no java compiler is provided and tools.jar is not available");
        try {
            try (URLClassLoader classLoader = new URLClassLoader(new URL[]{file.toURI().toURL()})) {
                classLoader.loadClass("javax.tools.ToolProvider");
            }
            if (!isLoaded()) return new UnsupportedOperationException("Loaded tools.jar but java compiler is still unavailable");
            return null;
        } catch (Throwable e) {
            return e;
        }
    }
}
