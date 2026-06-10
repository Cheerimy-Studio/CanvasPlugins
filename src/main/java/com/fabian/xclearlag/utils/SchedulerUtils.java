package com.fabian.xclearlag.utils;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.function.Consumer;

/**
 * Utility class to handle task scheduling on both traditional (Spigot/Paper)
 * and regional (Folia) servers.
 */
public class SchedulerUtils {

    private static Boolean isFolia = null;

    public static boolean isFolia() {
        if (isFolia == null) {
            try {
                Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
                isFolia = true;
            } catch (ClassNotFoundException e) {
                isFolia = false;
            }
        }
        return isFolia;
    }

    /**
     * Runs a task timer. On Folia, uses GlobalRegionScheduler.
     * On traditional servers, uses BukkitScheduler.
     */
    public static Object runTimer(Plugin plugin, Runnable runnable, long delay, long period) {
        if (isFolia()) {
            try {
                Object globalRegionScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
                return globalRegionScheduler.getClass()
                        .getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class)
                        .invoke(globalRegionScheduler, plugin, (Consumer<Object>) o -> runnable.run(),
                                Math.max(1, delay), period);
            } catch (Exception e) {
                // Fallback to traditional if reflection fails (shouldn't happen on Folia)
                return Bukkit.getScheduler().runTaskTimer(plugin, runnable, delay, period);
            }
        } else {
            return Bukkit.getScheduler().runTaskTimer(plugin, runnable, delay, period);
        }
    }

    public static void cancelTask(Object task) {
        if (task == null)
            return;
        if (task instanceof BukkitTask) {
            ((BukkitTask) task).cancel();
        } else {
            try {
                task.getClass().getMethod("cancel").invoke(task);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Runs a task once. On Folia, uses GlobalRegionScheduler.
     */
    public static void runTask(Plugin plugin, Runnable runnable) {
        if (isFolia()) {
            try {
                Object globalRegionScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
                globalRegionScheduler.getClass()
                        .getMethod("run", Plugin.class, Consumer.class)
                        .invoke(globalRegionScheduler, plugin, (Consumer<Object>) o -> runnable.run());
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, runnable);
            }
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    /**
     * Runs a task asynchronously. On Folia, uses AsyncScheduler.
     */
    public static void runTaskAsync(Plugin plugin, Runnable runnable) {
        if (isFolia()) {
            try {
                Object asyncScheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
                asyncScheduler.getClass()
                        .getMethod("runNow", Plugin.class, Consumer.class)
                        .invoke(asyncScheduler, plugin, (Consumer<Object>) o -> runnable.run());
            } catch (Exception e) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
            }
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
        }
    }
}
