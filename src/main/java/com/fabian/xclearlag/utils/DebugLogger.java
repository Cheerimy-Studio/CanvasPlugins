package com.fabian.xclearlag.utils;

import com.fabian.xclearlag.XClearlag;
import com.fabian.xclearlag.managers.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

/**
 * Static debug logging utility for X-Clearlag.
 * All debug output is gated behind the {@code debug: true} config option.
 */
public final class DebugLogger {

    private static final String PREFIX = "&8[&bX-Clearlag&8] &b[DEBUG] &7";

    private DebugLogger() {}

    /**
     * Logs a debug message (no category).
     *
     * @param message the message to log
     */
    public static void debug(String message) {
        if (!isDebugEnabled()) return;
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', PREFIX + message));
    }

    /**
     * Logs a debug message with a category tag.
     *
     * @param category a short category label (e.g. "Config", "Task", "TPS")
     * @param message  the message to log
     */
    public static void debug(String category, String message) {
        if (!isDebugEnabled()) return;
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
                PREFIX + "&f[" + category + "&f] &7" + message));
    }

    /**
     * Logs a debug message with a category tag and an attached throwable stack trace.
     *
     * @param category  a short category label
     * @param message   the message to log
     * @param throwable the throwable whose stack trace will be printed to console
     */
    public static void debug(String category, String message, Throwable throwable) {
        if (!isDebugEnabled()) return;
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
                PREFIX + "&f[" + category + "&f] &7" + message));
        if (throwable != null) {
            throwable.printStackTrace();
        }
    }

    /**
     * Checks whether debug mode is enabled in the current configuration.
     * Handles null plugin instance, null config manager, and null config gracefully.
     */
    private static boolean isDebugEnabled() {
        try {
            XClearlag plugin = XClearlag.getInstance();
            if (plugin == null) return false;
            ConfigManager configManager = plugin.getConfigManager();
            if (configManager == null) return false;
            com.fabian.xclearlag.managers.XConfig config = configManager.get();
            if (config == null) return false;
            return config.general.debug;
        } catch (Exception e) {
            return false;
        }
    }
}