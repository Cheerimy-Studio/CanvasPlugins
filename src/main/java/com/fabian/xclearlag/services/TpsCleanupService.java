package com.fabian.xclearlag.services;

import com.fabian.xclearlag.XClearlag;
import com.fabian.xclearlag.api.CleanupReason;
import com.fabian.xclearlag.commands.SilentCommandSender;
import com.fabian.xclearlag.managers.*;
import com.fabian.xclearlag.utils.*;
import com.fabian.xclearlag.utils.scheduler.SchedulerAdapter;
import com.fabian.xclearlag.utils.DebugLogger;

import org.bukkit.Bukkit;
import java.util.List;
import java.util.logging.Logger;

/**
 * Monitor TPS and trigger automatic cleanups if the server lags.
 * Includes "Death-Loop" protection with consecutive checks and rigid cooldowns.
 */
public class TpsCleanupService {

    private final XClearlag plugin;
    private final ConfigManager configManager;
    private final TPSMonitor tpsMonitor;
    private final TaskManager taskManager;
    private final Logger logger;
    private final SchedulerAdapter schedulerAdapter;
    
    private Object task;
    private long lastCleanupTime = -1;
    private int lowTpsCount = 0;

    public TpsCleanupService(XClearlag plugin, ConfigManager configManager, TPSMonitor tpsMonitor, TaskManager taskManager, SchedulerAdapter schedulerAdapter) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.tpsMonitor = tpsMonitor;
        this.taskManager = taskManager;
        this.schedulerAdapter = schedulerAdapter;
        this.logger = plugin.getLogger();
    }

    public void start() {
        XConfig config = configManager.get();
        if (!config.tps.enabled) {
            DebugLogger.debug("TPS-Cleanup", "TPS cleanup disabled in config, skipping.");
            return;
        }
        DebugLogger.debug("TPS-Cleanup", "Starting TPS cleanup monitor (threshold=" + config.tps.threshold + ", consecutiveChecks=" + config.tps.consecutiveChecks + ")");

        task = schedulerAdapter.runTaskTimer(() -> {
            double currentTps = tpsMonitor.getTPS();

            if (currentTps < config.tps.threshold) {
                lowTpsCount++;
                DebugLogger.debug("TPS-Cleanup", "Low TPS detected: " + String.format("%.2f", currentTps) + " < " + config.tps.threshold + " (count=" + lowTpsCount + "/" + config.tps.consecutiveChecks + ")");
                
                if (lowTpsCount < config.tps.consecutiveChecks) {
                    return;
                }

                long now = System.currentTimeMillis();
                if (lastCleanupTime >= 0 && (now - lastCleanupTime) < config.tps.cooldownMs) {
                    return;
                }

                lowTpsCount = 0;
                lastCleanupTime = now;
                
                List<String> taskNames = config.tps.tasksToRun;
                DebugLogger.debug("TPS-Cleanup", "TPS threshold breached! Triggering cleanup tasks: " + taskNames);
                Bukkit.getConsoleSender().sendMessage(plugin.getMessageManager().getWithContext(null, "tps-critical", 
                    "%tps%", String.format("%.2f", currentTps),
                    "%checks%", String.valueOf(config.tps.consecutiveChecks)));
                for (String taskName : taskNames) {
                    ClearTask ct = taskManager.getTaskMap().get(taskName.toLowerCase());
                    if (ct != null) {
                        try {
                            ct.executeCleanup(new SilentCommandSender(), true, CleanupReason.TPS_TRIGGERED);
                        } catch (Exception e) {
                            logger.warning("Cleanup error in auto-task '" + taskName + "': " + e.getMessage());
                        }
                    }
                }
            } else {
                lowTpsCount = 0;
            }
        }, 20L, 20L);
        DebugLogger.debug("TPS-Cleanup", "TPS cleanup monitor scheduled (every 1s).");
    }

    public void stop() {
        if (task != null) {
            schedulerAdapter.cancelTask(task);
            task = null;
            DebugLogger.debug("TPS-Cleanup", "TPS cleanup monitor stopped.");
        }
    }
}
