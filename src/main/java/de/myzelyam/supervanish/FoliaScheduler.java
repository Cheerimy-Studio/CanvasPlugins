package de.myzelyam.supervanish;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.function.Consumer;

/**
 * Utility class that bridges BukkitScheduler (standard Paper/Spigot) and
 * Folia/Canvas schedulers (GlobalRegionScheduler, EntityScheduler, AsyncScheduler).
 */
public final class FoliaScheduler {

    private static final boolean FOLIA;

    static {
        boolean found = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.RegionScheduler");
            Bukkit.getGlobalRegionScheduler();
            found = true;
        } catch (Throwable ignored) {
        }
        FOLIA = found;
    }

    private FoliaScheduler() {}

    public static boolean isFolia() {
        return FOLIA;
    }

    // ─── Global Region (runOnMain / delayed / repeating) ───

    /**
     * Execute a task on the global region (next tick).
     */
    public static void runOnGlobal(Plugin plugin, Runnable task) {
        if (FOLIA) {
            Bukkit.getGlobalRegionScheduler().run(plugin, t -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Execute a task on the global region after the given tick delay.
     */
    public static void runDelayedGlobal(Plugin plugin, Runnable task, long delayTicks) {
        if (FOLIA) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> task.run(), delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    /**
     * Schedule a repeating task on the global region.
     * @return a cancellable task handle (ScheduledTask on Folia, BukkitTask on Bukkit)
     */
    public static Object runTimerGlobal(Plugin plugin, Runnable task, long initialDelayTicks, long periodTicks) {
        if (FOLIA) {
            return Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> task.run(), initialDelayTicks, periodTicks);
        } else {
            return Bukkit.getScheduler().runTaskTimer(plugin, task, initialDelayTicks, periodTicks);
        }
    }

    // ─── Entity Scheduler (per-player, delayed) ───

    /**
     * Schedule a delayed task on the given player's entity scheduler.
     * Safe to call from any thread; the task will run on the owning region of the player.
     * @param player   The target player (must be online)
     * @param delayTicks Delay in ticks; pass 0 for next available tick
     */
    public static void runOnPlayer(Player player, Plugin plugin, Runnable task, long delayTicks) {
        if (FOLIA) {
            player.getScheduler().runDelayed(plugin, t -> task.run(), null, delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    // ─── Async Scheduler ───

    /**
     * Schedule a repeating task on the async scheduler.
     * @return a cancellable task handle
     */
    public static Object runAsyncTimer(Plugin plugin, Runnable task, long initialDelayTicks, long periodTicks) {
        if (FOLIA) {
            long delayMs = initialDelayTicks * 50L;
            long periodMs = periodTicks * 50L;
            return Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> task.run(), delayMs, periodMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } else {
            return Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, initialDelayTicks, periodTicks);
        }
    }

    // ─── Cancellation helpers ───

    /**
     * Cancel a single task returned by runTimerGlobal / runAsyncTimer.
     */
    public static void cancelTask(Object taskHandle) {
        if (taskHandle == null) return;
        if (taskHandle instanceof ScheduledTask st) {
            st.cancel();
        } else if (taskHandle instanceof org.bukkit.scheduler.BukkitTask bt) {
            bt.cancel();
        }
    }

    /**
     * Cancel all scheduled tasks for the plugin.
     * On Folia, cancels GlobalRegion + Async; on Bukkit, delegates to BukkitScheduler.
     * Entity scheduler tasks are not cancelled (they complete naturally).
     */
    public static void cancelAllTasks(Plugin plugin) {
        if (FOLIA) {
            Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
            Bukkit.getAsyncScheduler().cancelTasks(plugin);
        } else {
            Bukkit.getScheduler().cancelTasks(plugin);
        }
    }
}
