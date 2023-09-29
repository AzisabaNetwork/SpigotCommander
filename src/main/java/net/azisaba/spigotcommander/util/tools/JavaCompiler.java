package net.azisaba.spigotcommander.util.tools;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import net.azisaba.spigotcommander.util.ClasspathUtil;
import net.azisaba.spigotcommander.util.ThreadLocalLoggedBufferedOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

public class JavaCompiler {
    private static final Logger LOGGER = LoggerFactory.getLogger("SpigotCommander");
    public static final Set<String> classpath;

    static {
        Set<String> cp = new HashSet<>();
        addToClasspath(cp, "org.bukkit.Bukkit", true);
        addToClasspath(cp, "org.jetbrains.annotations.NotNull", false);
        addToClasspath(cp, "javax.annotation.Nonnull", false);
        addToClasspath(cp, "org.objectweb.asm.ClassVisitor", false);
        addToClasspath(cp, "com.google.common.collect.ImmutableMap", false);
        addToClasspath(cp, "it.unimi.dsi.fastutil.floats.Float2FloatOpenHashMap", false);
        addToClasspath(cp, "org.bukkit.craftbukkit.libs.it.unimi.dsi.fastutil.floats.Float2FloatOpenHashMap", false);
        addToClasspath(cp, "com.google.gson.Gson", false);
        addToClasspath(cp, "org.apache.logging.log4j.Logger", false);
        addToClasspath(cp, "org.slf4j.Logger", false);
        addToClasspath(cp, "io.netty.buffer.ByteBuf", false);
        addToClasspath(cp, "io.netty.channel.Channel", false);
        addToClasspath(cp, "io.netty.handler.codec.AsciiHeadersEncoder", false);
        addToClasspath(cp, "io.netty.util.AttributeKey", false);
        addToClasspath(cp, "org.apache.commons.lang3.StringUtils", false);
        classpath = ImmutableSet.copyOf(cp);
        LOGGER.info("Classpath for compiler: " + Joiner.on(File.pathSeparator).join(classpath));
    }

    private static void addToClasspath(@NotNull Set<String> cp, @NotNull String name, boolean required) {
        try {
            cp.add(ClasspathUtil.getClasspath(Class.forName(name)));
        } catch (ClassNotFoundException e) {
            if (required) throw new RuntimeException(e);
        }
    }

    /**
     * Compiles a single .java file.
     * @param file .java file to compile.
     * @return .class file
     */
    @NotNull
    public static File compile(@NotNull File root, @NotNull File file, @Nullable File dest) {
        if (!file.getName().endsWith(".java")) throw new IllegalArgumentException("Illegal file name (must ends with .java): " + file.getAbsolutePath());
        List<String> args = new ArrayList<>();
        if (!classpath.isEmpty()) {
            args.add("-cp");
            args.add(Joiner.on(File.pathSeparator).join(classpath) + File.pathSeparator + root.getAbsolutePath());
        }
        if (dest != null) {
            args.add("-d");
            args.add(dest.getAbsolutePath());
        }
        args.add("-proc:none");
        args.add("-source");
        args.add(getMajorJavaVersion());
        args.add(file.getAbsolutePath());
        OutputStream out = new ThreadLocalLoggedBufferedOutputStream("SpigotCommander Live Compiler", Level.WARN);
        javax.tools.JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new RuntimeException("JavaCompiler is not available");
        int result = compiler.run(System.in, out, out, args.toArray(new String[0]));
        if (result != 0) LOGGER.warn("Compiler (for file {}) exited with code: {}", file.getAbsolutePath(), result);
        return new File(file.getAbsolutePath().replaceAll("(.*)\\.java", "$1.class"));
    }

    /**
     * Compiles all .java files in specified directory, or just compiles a single .java file.
     * @param file the file(s) to compile
     */
    @NotNull
    public static File compileAll(@NotNull File file, boolean ignoreErrors) throws IOException {
        if (!file.isDirectory() && !file.getName().endsWith(".java")) throw new IllegalArgumentException("Illegal file name (not a directory nor .java file): " + file.getAbsolutePath());
        Path path = file.toPath();
        File tmp = Files.createTempDirectory("spigotcommander-live-compiler-").toFile();
        tmp.deleteOnExit();
        AtomicReference<Throwable> throwable = new AtomicReference<>();
        int nThreads = Math.max(4, Runtime.getRuntime().availableProcessors());
        ExecutorService compilerExecutor = Executors.newFixedThreadPool(nThreads, new ThreadFactoryBuilder().setNameFormat("SpigotCommander Compiler Worker #%d").build());
        LOGGER.info("Compiling the source code using up to " + nThreads + " threads");
        AtomicBoolean first = new AtomicBoolean(true);
        try (Stream<Path> stream = Files.walk(file.toPath())) {
            stream.map(Path::toFile)
                    .forEach(f -> {
                        if (throwable.get() != null) return;
                        if (f.isDirectory()) {
                            // if dir
                            File target = new File(tmp, path.relativize(f.toPath()).toString());
                            if (!target.mkdirs() && !target.getAbsolutePath().equals(tmp.getAbsolutePath())) {
                                LOGGER.warn("Failed to create directory {} -> {}", f.getAbsolutePath(), target.getAbsolutePath());
                            } else {
                                LOGGER.debug("Created directory {} -> {}", f.getAbsolutePath(), target.getAbsolutePath());
                            }
                        } else {
                            // if file
                            if (f.getName().endsWith(".java")) {
                                Runnable doCompile = () -> {
                                    String rel = path.relativize(f.toPath()).toString().replaceAll("(.*)\\.java", "$1.class");
                                    LOGGER.debug("Compiling: " + rel);
                                    compile(file, f, tmp);
                                    if (!new File(tmp, rel).exists()) {
                                        if (!ignoreErrors) throwable.set(new RuntimeException("Compilation failed: " + rel));
                                        LOGGER.error("Failed to compile: " + rel);
                                        return;
                                    }
                                    LOGGER.info("Compiled {} -> {}", f.getAbsolutePath(), tmp.getAbsolutePath());
                                };
                                if (first.get()) { // to prevent race condition
                                    first.set(false);
                                    doCompile.run();
                                } else {
                                    compilerExecutor.submit(() -> {
                                        if (throwable.get() != null) return;
                                        try {
                                            doCompile.run();
                                        } catch (Exception throwable1) {
                                            String rel = path.relativize(f.toPath()).toString().replaceAll("(.*)\\.java", "$1.class");
                                            if (!ignoreErrors) throwable.set(new RuntimeException("Compilation failed: " + rel, throwable1));
                                            LOGGER.error("Failed to compile: " + rel);
                                        }
                                    });
                                }
                            } else {
                                File dest = new File(tmp, path.relativize(f.toPath()).toString());
                                try {
                                    Files.copy(f.toPath(), dest.toPath());
                                    LOGGER.debug("Copied {} -> {}", f.getAbsolutePath(), dest.getAbsolutePath());
                                } catch (IOException ex) {
                                    LOGGER.warn("Failed to copy {} -> {}", f.getAbsolutePath(), dest.getAbsolutePath(), ex);
                                }
                            }
                        }
                    });
        }
        compilerExecutor.shutdown();
        try {
            if (!compilerExecutor.awaitTermination(5L, TimeUnit.MINUTES)) {
                LOGGER.warn("Timed out compilation. Some files may be missing.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (throwable.get() != null) {
            if (throwable.get() instanceof RuntimeException) throw (RuntimeException) throwable.get();
            throw new RuntimeException(throwable.get());
        }
        return tmp;
    }

    public static @NotNull String getMajorJavaVersion() {
        String version = System.getProperty("java.version", "8");
        return version.substring(0, version.indexOf('.'));
    }
}
