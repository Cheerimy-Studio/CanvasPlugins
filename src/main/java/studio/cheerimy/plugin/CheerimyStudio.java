package studio.cheerimy.plugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CheerimyStudio extends JavaPlugin implements CommandExecutor {

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();
        Bukkit.getLogger().info("[Cheerimy-Studio] v" + getDescription().getVersion() + " loaded in " + (System.currentTimeMillis() - start) + "ms");
        Bukkit.getLogger().info("[Cheerimy-Studio] GitHub: https://github.com/Cheerimy-Studio/CanvasPlugins");

        PluginCommand cmd = getCommand("cheerimy-studio");
        if (cmd != null) {
            cmd.setExecutor(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== Cheerimy-Studio ===");
        sender.sendMessage(ChatColor.YELLOW + "v" + getDescription().getVersion());
        sender.sendMessage(ChatColor.AQUA + "GitHub: " + ChatColor.YELLOW + "https://github.com/Cheerimy-Studio/CanvasPlugins");
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "======================");
        return true;
    }
}
