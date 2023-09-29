package net.azisaba.spigotcommander.commands;

import net.azisaba.spigotcommander.SpigotCommander;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class SpigotCommanderCommand implements TabExecutor {
    private static final List<String> COMMANDS = Collections.singletonList("reload");

    private final SpigotCommander plugin;

    public SpigotCommanderCommand(@NotNull SpigotCommander plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "/spigotcommander (" + String.join("|", COMMANDS) + ")");
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage(ChatColor.GOLD + "ファイルをリロード中です。");
            long start = System.currentTimeMillis();
            plugin.reload().thenRun(() -> sender.sendMessage(ChatColor.GREEN + "リロードが完了しました。" + ChatColor.DARK_GRAY + " (" + (System.currentTimeMillis() - start) + "ms)"));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return COMMANDS.stream().filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
