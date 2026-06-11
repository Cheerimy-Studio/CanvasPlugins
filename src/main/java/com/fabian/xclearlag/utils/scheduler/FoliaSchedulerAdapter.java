package com.fabian.xclearlag.utils.scheduler;

import com.fabian.xclearlag.XClearlag;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import com.fabian.xclearlag.utils.DebugLogger;

/**
 * Folia-specific implementation of the SchedulerAdapter using Reflection.
 * This allows the plugin to compile against legacy Spigot APIs (like 1.8.8)
 * while still functioning correctly on Folia servers at runtime.
 */
public class FoliaSchedulerAdapter implements SchedulerAdapter {

    private final XClearlag plugin;
    private Object globalScheduler;
    private Object asyncScheduler;

    private Method runMethod;
    private Method runDelayedMethod;
    private Method runAtFixedRateMethod;
    private Method runNowAsyncMethod;

    public FoliaSchedulerAdapter(XClearlag plugin) {
        this.plugin = plugin;
        try {
            Class<?> serverClass = Bukkit.getServer().getClass();

            // Get Global Scheduler
            Method getGlobalMethod = serverClass.getMethod("getGlobalRegionScheduler");
            this.globalScheduler = getGlobalMethod.invoke(Bukkit.getServer());

            // Get Async Scheduler
            Method getAsyncMethod = serverClass.getMethod("getAsyncScheduler");
            this.asyncScheduler = getAsyncMethod.invoke(Bukkit.getServer());

            Class<?> globalClass = globalScheduler.getClass();
            Class<?> asyncClass = asyncScheduler.getClass();

            // Prepare methods
            // run(Plugin plugin, Consumer<ScheduledTask> task)
            this.runMethod = globalClass.getMethod("run", Plugin.class, Consumer.class);
            
            // runDelayed(Plugin plugin, Consumer<ScheduledTask> task, long ticks)
            this.runDelayedMethod = globalClass.getMethod("runDelayed", Plugin.class, Consumer.class, long.class);
            
            // runAtFixedRate(Plugin plugin, Consumer<ScheduledTask> task, long initialTicks, long periodTicks)
            this.runAtFixedRateMethod = globalClass.getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);

            // runNow(Plugin plugin, Consumer<ScheduledTask> task)
            this.runNowAsyncMethod = asyncClass.getMethod("runNow", Plugin.class, Consumer.class);

        } catch (Exception e) {
            plugin.logWarning("Failed to initialize Folia Reflection: " + e.getMessage());
            DebugLogger.debug("Scheduler", "FoliaSchedulerAdapter reflection init failed.", e);
        }
    }

    @Override
    public void runTask(Runnable runnable) {
        try {
            runMethod.invoke(globalScheduler, plugin, (Consumer<Object>) t -> runnable.run());
        } catch (Exception ignored) {}
    }

    @Override
    public void runTaskLater(Runnable runnable, long delay) {
        try {
            runDelayedMethod.invoke(globalScheduler, plugin, (Consumer<Object>) t -> runnable.run(), delay);
        } catch (Exception ignored) {}
    }

    @Override
    public Object runTaskTimer(Runnable runnable, long delay, long period) {
        try {
            return runAtFixedRateMethod.invoke(globalScheduler, plugin, (Consumer<Object>) t -> runnable.run(), delay, period);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void runTaskAsync(Runnable runnable) {
        try {
            runNowAsyncMethod.invoke(asyncScheduler, plugin, (Consumer<Object>) t -> runnable.run());
        } catch (Exception ignored) {}
    }

    @Override
    public void runTaskOnEntity(org.bukkit.entity.Entity entity, Runnable runnable) {
        try {
            // Get the EntityScheduler via reflection
            Method getSchedulerMethod = entity.getClass().getMethod("getScheduler");
            Object entityScheduler = getSchedulerMethod.invoke(entity);

            // run(Plugin plugin, Consumer<ScheduledTask> task, Runnable retired)
            Method runMethod = entityScheduler.getClass().getMethod("run", Plugin.class, Consumer.class, Runnable.class);
            runMethod.invoke(entityScheduler, plugin, (Consumer<Object>) t -> runnable.run(), null);
        } catch (Exception e) {
            // Fallback to global scheduler if entity-specific fails
            DebugLogger.debug("Scheduler", "Entity scheduler failed, falling back to global.");
            runTask(runnable);
        }
    }

    @Override
    public void cancelTask(Object task) {
        if (task == null) return;
        try {
            Method cancelMethod = task.getClass().getMethod("cancel");
            cancelMethod.invoke(task);
        } catch (Exception ignored) {}
    }
}
