package net.azisaba.spigotcommander.util;

import org.bukkit.Bukkit;

import java.lang.reflect.Method;

public class CommandUtil {
    public static void syncCommands() throws ReflectiveOperationException {
        Method method = Bukkit.getServer().getClass().getDeclaredMethod("syncCommands");
        method.setAccessible(true);
        method.invoke(Bukkit.getServer());
    }
}
