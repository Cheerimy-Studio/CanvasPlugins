package com.fabian.xclearlag.services;

import com.fabian.xclearlag.XClearlag;
import com.fabian.xclearlag.managers.XConfig;
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
        stopAll();
        tasks.clear();

        XConfig config = plugin.getConfigManager().get();
        for (Map.Entry<String, XConfig.TaskConfig> entry : config.tasks.entrySet()) {
            if (entry.getValue().enabled) {
                ClearTask task = new ClearTask(
                    plugin, 
                    entry.getKey(), 
                    entry.getValue(),
                    plugin.getCommandDispatcher(),
                    plugin.getCleanupNotifier(),
                    plugin.getClearExecutor(),
                    plugin.getMetricsTracker(),
                    plugin.getMessageManager(),
                    plugin.getSchedulerAdapter()
                );
                tasks.put(entry.getKey().toLowerCase(), task);
                task.start();
            }
        }
    }

    public void stopAll() {
        for (ClearTask task : tasks.values()) {
            task.stop();
        }
        tasks.clear(); // Clear the map after stopping
    }

    public Map<String, ClearTask> getTaskMap() {
        return tasks;
    }

    public Collection<ClearTask> getTasks() {
        return tasks.values();
    }
}
