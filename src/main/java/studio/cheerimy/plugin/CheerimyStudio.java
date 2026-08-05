package studio.cheerimy.plugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;

public final class CheerimyStudio extends JavaPlugin implements CommandExecutor {

    @Override
    public void onEnable() {
        Bukkit.getLogger().info("[Cheerimy-Studio] Thank you for using Cheerimy-Studio plugins!");
        Bukkit.getLogger().info("[Cheerimy-Studio] If you enjoy our work, please give us a Star on GitHub!");
        Bukkit.getLogger().info("[Cheerimy-Studio] GitHub: https://github.com/Cheerimy-Studio/");

        PluginCommand cmd = getCommand("cheerimy-studio");
        if (cmd != null) {
            cmd.setExecutor(this);
        } else {
            Bukkit.getLogger().warning("[Cheerimy-Studio] Command 'cheerimy-studio' is not defined in plugin.yml!");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        CompletableFuture.runAsync(() -> {
            sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== Cheerimy-Studio ===");
            sender.sendMessage(ChatColor.YELLOW + "Thank you for using our plugins!");
            sender.sendMessage(ChatColor.YELLOW + "If you find our work helpful, please consider giving us a Star!");
            sender.sendMessage(ChatColor.AQUA + "GitHub: " + ChatColor.YELLOW + "https://github.com/Cheerimy-Studio/");
            sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "======================");
        });
        return true;
    }
}
