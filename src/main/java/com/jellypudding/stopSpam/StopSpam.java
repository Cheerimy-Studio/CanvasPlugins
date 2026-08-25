package com.jellypudding.stopSpam;

import com.jellypudding.stopSpam.config.PluginConfig;
import com.jellypudding.stopSpam.listener.ChatListener;
import com.jellypudding.stopSpam.spam.SpamDetector;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class StopSpam extends JavaPlugin {

    private final PluginConfig pluginConfig = new PluginConfig();
    private SpamDetector spamDetector;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        pluginConfig.load(this);

        spamDetector = new SpamDetector(pluginConfig);
        getServer().getPluginManager().registerEvents(new ChatListener(spamDetector), this);

        scheduleCleanupTasks();

        int pluginId = 27566;
        new Metrics(this, pluginId);

        getLogger().info("StopSpam has been enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("StopSpam has been disabled.");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!command.getName().equalsIgnoreCase("stopspam")) return false;

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("stopspam.admin")) {
                sender.sendMessage(Component.text("You don't have permission to use StopSpam.", NamedTextColor.RED));
                return true;
            }
            pluginConfig.load(this);
            sender.sendMessage(Component.text("StopSpam configuration reloaded.", NamedTextColor.GREEN));
            return true;
        }

        return false;
    }

    private void scheduleCleanupTasks() {
        long everyFourHours = 20L * 60 * 60 * 4;
        long everyMinute = 20L * 60;

        Runnable cleanupExpired = () -> {
            spamDetector.cleanupExpiredData(System.currentTimeMillis());
            getLogger().info("Cleaned up expired timeouts and old violation records.");
        };
        Runnable cleanupOld = () -> spamDetector.cleanupOldMessages(System.currentTimeMillis());

        if (isFolia()) {
            // Folia / Canvas: use GlobalRegionScheduler
            var globalScheduler = getServer().getGlobalRegionScheduler();
            globalScheduler.runAtFixedRate(this, task -> cleanupExpired.run(), everyFourHours, everyFourHours);
            globalScheduler.runAtFixedRate(this, task -> cleanupOld.run(), everyMinute, everyMinute);
        } else {
            // Standard Paper / Spigot: use legacy BukkitScheduler
            getServer().getScheduler().runTaskTimer(this, cleanupExpired, everyFourHours, everyFourHours);
            getServer().getScheduler().runTaskTimer(this, cleanupOld, everyMinute, everyMinute);
        }
    }

    /**
     * Detect Folia/Canvas at runtime. Canvas 26.2 includes Folia's GlobalRegionScheduler.
     */
    private static boolean isFolia() {
        try {
            // GlobalRegionScheduler exists only on Folia/Canvas; standard Paper has this method
            // returning a different type. We check for the actual interface.
            Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            return org.bukkit.Bukkit.getGlobalRegionScheduler() != null;
        } catch (ClassNotFoundException | NoSuchMethodError e) {
            return false;
        }
    }
}
