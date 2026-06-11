package com.fabian.xclearlag.services;

import com.fabian.xclearlag.XClearlag;
import com.fabian.xclearlag.managers.XConfig;
import com.fabian.xclearlag.utils.DebugLogger;
import java.util.Collection;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the registration and lifecycle of cleanup tasks.
 */
public class TaskManager {

    private final XClearlag plugin;
    private final Map<String, ClearTask> tasks = new ConcurrentHashMap<>();

    public TaskManager(XClearlag plugin) {
        this.plugin = plugin;
    }

    public void loadTasks() {
        DebugLogger.debug("TaskManager", "Loading tasks...");
        stopAll();
        tasks.clear();

        XConfig config = plugin.getConfigManager().get();
        for (Map.Entry<String, XConfig.TaskConfig> entry : config.tasks.entrySet()) {
            if (entry.getValue().enabled) {
                DebugLogger.debug("TaskManager", "Registering task: " + entry.getKey() + " (interval=" + entry.getValue().interval + "s)");
                ClearTask task = new ClearTask(
                    plugin, 
                    entry.getKey(), 
                    entry.getValue(),
                    plugin.getCommandDispatcher(),
                    plugin.getCleanupNotifier(),
                    plugin.getClearExecutor(),
                    plugin.getMetricsTracker(),
                    plugin.getLanguageManager(),
                    plugin.getSchedulerAdapter()
                );
                tasks.put(entry.getKey().toLowerCase(), task);
                task.start();
            } else {
                DebugLogger.debug("TaskManager", "Skipping disabled task: " + entry.getKey());
            }
        }
        DebugLogger.debug("TaskManager", "Loaded " + tasks.size() + " enabled tasks.");
    }

    public void stopAll() {
        DebugLogger.debug("TaskManager", "Stopping all tasks (" + tasks.size() + ")...");
        for (ClearTask task : tasks.values()) {
            task.stop();
        }
        tasks.clear(); // Clear the map after stopping
        DebugLogger.debug("TaskManager", "All tasks stopped.");
    }

    public Map<String, ClearTask> getTaskMap() {
        return tasks;
    }

    public Collection<ClearTask> getTasks() {
        return tasks.values();
    }
}
