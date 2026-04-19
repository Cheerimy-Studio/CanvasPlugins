package com.fabian.xclearlag.services;

import com.fabian.xclearlag.api.CleanupReason;
import com.fabian.xclearlag.api.CleanupService;
import com.fabian.xclearlag.commands.*;
import com.fabian.xclearlag.config.*;
import com.fabian.xclearlag.utils.*;

import com.fabian.xclearlag.utils.scheduler.SchedulerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.List;

/**
 * Orchestrates a specific cleanup task.
 * Responsibility: Countdown management and triggering services.
 * Decoupled from direct UI/Notification logic.
 */
public class ClearTask {

    private final String name;
    private final XConfig.TaskConfig config;

    private final CountdownManager countdownManager;
    private final CommandDispatcher commandDispatcher;
    private final CleanupNotifier notifier;
    private final CleanupService cleanupService;
    private final MetricsTracker metricsTracker;
    private final MessageManager messageManager;
    private final SchedulerAdapter schedulerAdapter;

    private Object task;

    public ClearTask(JavaPlugin plugin, String name, XConfig.TaskConfig config,
                     CommandDispatcher commandDispatcher, CleanupNotifier notifier,
                     CleanupService cleanupService, MetricsTracker metricsTracker,
                     MessageManager messageManager, SchedulerAdapter schedulerAdapter) {
        this.name = name;
        this.config = config;
        this.commandDispatcher = commandDispatcher;
        this.notifier = notifier;
        this.cleanupService = cleanupService;
        this.metricsTracker = metricsTracker;
        this.messageManager = messageManager;
        this.schedulerAdapter = schedulerAdapter;
        this.countdownManager = new CountdownManager(config.interval, config.countdown);
    }

    public void start() {
        if (task != null) stop();

        task = schedulerAdapter.runTaskTimer(() -> {
            int online = Bukkit.getOnlinePlayers().size();
            if (online < config.minPlayers) {
                countdownManager.reset();
                notifier.hideUI();
                return;
            }

            int secondsLeft = countdownManager.decrement();
            
            // Countdown Commands
            List<String> specialCmds = config.countdownCommands.get(secondsLeft);
            if (specialCmds != null) {
                commandDispatcher.dispatch(specialCmds, Bukkit.getOnlinePlayers(), secondsLeft);
            }

            // UI Update delegated to Notifier
            notifier.updateCountdown(secondsLeft, config.interval);

            if (secondsLeft <= 0) {
                executeCleanup(new SilentCommandSender(), false, CleanupReason.SCHEDULE_TRIGGERED);
                countdownManager.reset();
            }
        }, 20L, 20L);
    }

    public void executeCleanup(CommandSender sender, boolean silent, CleanupReason reason) {
        if (!silent) {
            notifier.broadcast("clear-start", false, sender);
        } else {
            // Even if silent for players, log to console
            Bukkit.getConsoleSender().sendMessage(messageManager.getWithContext(null, "clear-start"));
        }

        cleanupService.execute(name, config, reason, sender, (removed) -> {
            metricsTracker.record(name, removed);

            if (!silent) {
                String clearedMsg = messageManager.getWithContext(sender,
                        removed > 0 ? "cleared" : "cleared-none",
                        "%count%", String.valueOf(removed)
                );
                notifier.broadcast(clearedMsg, true, sender);
            } else {
                // Console feedback for silent tasks
                String clearedMsg = messageManager.getWithContext(null,
                        removed > 0 ? "cleared" : "cleared-none",
                        "%count%", String.valueOf(removed)
                );
                Bukkit.getConsoleSender().sendMessage(clearedMsg);
            }

            commandDispatcher.dispatch(config.commands, Bukkit.getOnlinePlayers(), 0);
        });
    }

    public void stop() {
        if (task != null) {
            schedulerAdapter.cancelTask(task);
            task = null;
        }
        notifier.hideUI();
    }

    public String getName() { return name; }
    public XConfig.TaskConfig getConfig() { return config; }
    public CountdownManager getCountdownManager() { return countdownManager; }
}
