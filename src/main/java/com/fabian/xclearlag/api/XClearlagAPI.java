package com.fabian.xclearlag.api;

import com.fabian.xclearlag.XClearlag;
import com.fabian.xclearlag.managers.XConfig;
import com.fabian.xclearlag.utils.DebugLogger;
import org.bukkit.command.CommandSender;
import java.util.function.Consumer;

/**
 * High-level API entry point for external developers.
 */
public class XClearlagAPI {

    private static XClearlagAPI instance;
    private final XClearlag plugin;

    private XClearlagAPI(XClearlag plugin) {
        this.plugin = plugin;
    }

    public static void init(XClearlag plugin) {
        instance = new XClearlagAPI(plugin);
        DebugLogger.debug("API", "XClearlagAPI initialized.");
    }

    public static XClearlagAPI getInstance() {
        if (instance == null) {
            throw new IllegalStateException("XClearlagAPI not initialized yet!");
        }
        return instance;
    }

    /**
     * Programmatically trigger a cleanup task.
     * 
     * @param taskName   The ID of the task to run.
     * @param sender     The sender context (e.g. Bukkit.getConsoleSender()).
     * @param onComplete Callback when cleanup finishes.
     */
    public void triggerCleanup(String taskName, CommandSender sender, Consumer<Integer> onComplete) {
        DebugLogger.debug("API", "API cleanup triggered: task=" + taskName + ", sender=" + sender.getName());
        XConfig.TaskConfig task = plugin.getConfigManager().get().tasks.get(taskName.toLowerCase());
        if (task != null) {
            plugin.getClearExecutor().execute(taskName, task, CleanupReason.API_TRIGGERED, sender, onComplete);
        } else if (onComplete != null) {
            onComplete.accept(0);
        }
    }

    /**
     * Gets the current server TPS as measured by X-Clearlag.
     */
    public double getTPS() {
        return plugin.getTpsMonitor().getTPS();
    }
    
    /**
     * Gets accessibility to the raw underlying plugin if needed.
     */
    public XClearlag getPlugin() {
        return plugin;
    }
}
