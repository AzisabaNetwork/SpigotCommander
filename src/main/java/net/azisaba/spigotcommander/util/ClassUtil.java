package net.azisaba.spigotcommander.util;

import net.azisaba.spigotcommander.SpigotCommander;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class ClassUtil {
    public static @NotNull Object construct(@NotNull ClassLoader cl, @NotNull String className, @NotNull SpigotCommander plugin) {
        Class<?> clazz;
        try {
            clazz = cl.loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        try {
            return clazz.getConstructor().newInstance();
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            return clazz.getConstructor(Plugin.class).newInstance(plugin);
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            return clazz.getConstructor(JavaPlugin.class).newInstance(plugin);
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            return clazz.getConstructor(SpigotCommander.class).newInstance(plugin);
        } catch (ReflectiveOperationException ignored) {
        }
        String error = "No valid constructor found in " + clazz.getCanonicalName() + "\n";
        error += "Class must have one of these constructors:\n";
        error += "- public " + clazz.getSimpleName() + "()\n";
        error += "- public " + clazz.getSimpleName() + "(org.bukkit.plugin.Plugin)\n";
        error += "- public " + clazz.getSimpleName() + "(org.bukkit.plugin.java.JavaPlugin)\n";
        error += "- public " + clazz.getSimpleName() + "(" + SpigotCommander.class.getCanonicalName() + ")";
        throw new RuntimeException(error);
    }
}
