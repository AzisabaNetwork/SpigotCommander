package net.azisaba.spigotcommander;

import net.azisaba.spigotcommander.commands.SpigotCommanderCommand;
import net.azisaba.spigotcommander.util.CommandUtil;
import net.azisaba.spigotcommander.util.FileUtil;
import net.azisaba.spigotcommander.util.tools.JavaCompiler;
import net.azisaba.spigotcommander.util.tools.JavaTools;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

public class SpigotCommander extends JavaPlugin {
    private static final AtomicLong INDEX = new AtomicLong((long) (Math.random() * 100000000));
    private final AtomicReference<URLClassLoader> cl = new AtomicReference<>();
    private final Map<String, Command> commands = new HashMap<>();
    private final Executor syncExecutor = r -> Bukkit.getScheduler().runTask(this, r);
    private final Executor asyncExecutor = r -> Bukkit.getScheduler().runTaskAsynchronously(this, r);

    @Override
    public void onEnable() {
        if (!JavaTools.isLoaded()) {
            throw new RuntimeException("tools.jar (included in JDK) is not available. Path: " + JavaTools.TOOLS_JAR_PATH, JavaTools.UNAVAILABLE_REASON);
        }
        Objects.requireNonNull(getCommand("spigotcommander")).setExecutor(new SpigotCommanderCommand(this));
        // reload asynchronously
        reload();
    }

    public @NotNull CompletableFuture<Void> reload() {
        return CompletableFuture.runAsync(() -> {
            // unregister commands
            Map<String, Command> knownCommands = Bukkit.getCommandMap().getKnownCommands();
            commands.forEach((label, command) -> {
                knownCommands.remove(label, command);
                knownCommands.remove("spigotcommander:" + label, command);
            });
            commands.clear();

            // close class loader
            if (cl.get() != null) {
                try {
                    cl.get().close();
                } catch (IOException e) {
                    getSLF4JLogger().error("Failed to close class loader", e);
                }
            }

            // generate default files
            saveDefaultConfig();
            if (!new File(getDataFolder(), "classes").exists()) {
                saveResource("classes/Ping.java", true);
            }

            // reload config
            reloadConfig();
        }, syncExecutor).thenApplyAsync(dummy -> {
            // compile all classes
            String packageName = getNextPackageName();
            try {
                Path tmp = Files.createTempDirectory("spigotcommander-live-compiler-src-");
                Path javaDir = tmp.resolve(packageName.replaceAll("\\.", "/"));
                //noinspection ResultOfMethodCallIgnored
                javaDir.toFile().mkdirs();
                // prepare for compile
                try (Stream<Path> stream = Files.list(getDataFolder().toPath().resolve("classes"))) {
                    stream.forEach(sourcePath -> {
                        Path targetPath = javaDir.resolve(sourcePath.getFileName().toString());
                        try {
                            Files.copy(sourcePath, targetPath);
                            String source = FileUtil.readString(targetPath);
                            StringBuilder stripped = new StringBuilder(3);
                            if (source.startsWith("package ")) {
                                source = source.substring(source.indexOf(';') + 1);
                                while (source.startsWith("\n")) {
                                    source = source.substring(1);
                                    stripped.append("\n");
                                }
                            }
                            source = "package " + packageName + ";" + stripped + source;
                            FileUtil.writeString(targetPath, source);
                        } catch (IOException e) {
                            getSLF4JLogger().error("Failed to copy {} -> {}", sourcePath, targetPath);
                        }
                    });
                }
                // compile
                Path compiled = JavaCompiler.compileAll(tmp.toFile(), true).toPath();
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        FileUtil.deleteRecursively(compiled);
                    } catch (IOException e) {
                        //noinspection CallToPrintStackTrace
                        e.printStackTrace();
                    }
                }));
                // post compile
                // remove source directory
                FileUtil.deleteRecursively(tmp);
                cl.set(new URLClassLoader(new URL[]{compiled.toUri().toURL()}));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return packageName;
        }).thenAcceptAsync(packageName -> {
            // load commands
            ConfigurationSection commandsSection = getConfig().getConfigurationSection("commands");
            if (commandsSection != null) {
                for (String key : commandsSection.getKeys(false)) {
                    ConfigurationSection commandSection = commandsSection.getConfigurationSection(key);
                    if (commandSection == null) continue;
                    String commandName = key.toLowerCase();
                    if (commandName.equals("spigotcommander")) {
                        getSLF4JLogger().warn("Skipping reserved name: {}", commandName);
                        continue;
                    }
                    String className = commandSection.getString("class");
                    // construct command executor
                    Class<?> clazz;
                    CommandExecutor commandExecutor = null;
                    try {
                        clazz = cl.get().loadClass(packageName + "." + className);
                    } catch (ClassNotFoundException e) {
                        getSLF4JLogger().error("Failed to load class {}", className, e);
                        continue;
                    }
                    try {
                        commandExecutor = (CommandExecutor) clazz.getConstructor().newInstance();
                    } catch (ReflectiveOperationException ignored) {
                    }
                    try {
                        commandExecutor = (CommandExecutor) clazz.getConstructor(Plugin.class).newInstance(this);
                    } catch (ReflectiveOperationException ignored) {
                    }
                    try {
                        commandExecutor = (CommandExecutor) clazz.getConstructor(JavaPlugin.class).newInstance(this);
                    } catch (ReflectiveOperationException ignored) {
                    }
                    try {
                        commandExecutor = (CommandExecutor) clazz.getConstructor(SpigotCommander.class).newInstance(this);
                    } catch (ReflectiveOperationException ignored) {
                    }
                    if (commandExecutor == null) {
                        getSLF4JLogger().error("No valid constructor found in " + clazz);
                        getSLF4JLogger().error("Constructor must be one of these:");
                        getSLF4JLogger().error("- public " + clazz.getSimpleName() + "()");
                        getSLF4JLogger().error("- public " + clazz.getSimpleName() + "(org.bukkit.plugin.Plugin)");
                        getSLF4JLogger().error("- public " + clazz.getSimpleName() + "(org.bukkit.plugin.java.JavaPlugin)");
                        getSLF4JLogger().error("- public " + clazz.getSimpleName() + "(" + SpigotCommander.class.getCanonicalName() + ")");
                        continue;
                    }
                    // register command
                    String permission = commandSection.getString("permission");
                    String permissionMessage = commandSection.getString("permissionMessage");
                    String description = Objects.requireNonNull(commandSection.getString("description", ""));
                    String usage = Objects.requireNonNull(commandSection.getString("usage", ""));
                    List<String> aliases = commandSection.getStringList("aliases");
                    CommandExecutor finalCommandExecutor = commandExecutor;
                    Command command = new Command(commandName, description, usage, aliases) {
                        @Override
                        public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
                            return finalCommandExecutor.onCommand(sender, this, commandLabel, args);
                        }

                        @Override
                        public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) throws IllegalArgumentException {
                            if (finalCommandExecutor instanceof TabCompleter) {
                                List<String> list = ((TabCompleter) finalCommandExecutor).onTabComplete(sender, this, alias, args);
                                if (list != null) return list;
                            }
                            return super.tabComplete(sender, alias, args);
                        }
                    };
                    command.setPermission(permission);
                    command.setPermissionMessage(permissionMessage);
                    commands.put(commandName, command);
                    Bukkit.getCommandMap().register(commandName, "spigotcommander", command);
                    getSLF4JLogger().info("Registered command {} -> {} ({})", commandName, command, commandExecutor);
                }
                try {
                    CommandUtil.syncCommands();
                } catch (Exception e) {
                    getSLF4JLogger().warn("Failed to sync commands", e);
                }
            }
        }, asyncExecutor);
    }

    @Override
    public void onDisable() {
        // unregister commands
        Map<String, Command> knownCommands = Bukkit.getCommandMap().getKnownCommands();
        commands.forEach((label, command) -> {
            knownCommands.remove(label, command);
            knownCommands.remove("spigotcommander:" + label, command);
        });
        commands.clear();
        if (cl.get() != null) {
            try {
                cl.get().close();
            } catch (IOException e) {
                getSLF4JLogger().error("Failed to close URLClassLoader", e);
            }
        }
        try {
            CommandUtil.syncCommands();
        } catch (Exception e) {
            getSLF4JLogger().warn("Failed to sync commands", e);
        }
    }

    private static @NotNull String getNextPackageName() {
        return "net.azisaba.spigotcommander.generated$" + INDEX.getAndIncrement();
    }
}
