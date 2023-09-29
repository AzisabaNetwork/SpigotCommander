import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class GiveDiamond implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player) || args.length > 0) {
            if (args.length == 0) {
                return false;
            }
            Player player = Bukkit.getPlayerExact(args[0]);
            if (player == null) {
                sender.sendMessage(ChatColor.RED + "No such player: " + args[0]);
                return true;
            }
            player.getInventory().addItem(new ItemStack(Material.DIAMOND));
            return true;
        }
        Player player = (Player) sender;
        player.getInventory().addItem(new ItemStack(Material.DIAMOND));
        return true;
    }
}
