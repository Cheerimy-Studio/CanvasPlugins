package com.fabian.xclearlag.utils.scheduler;

/**
 * Interface to unify task scheduling between Bukkit and Folia.
 */
public interface SchedulerAdapter {

    /**
     * Runs a task on the next tick (Main thread in Bukkit, Global region in Folia).
     */
    void runTask(Runnable runnable);

    /**
     * Runs a task after a delay.
     */
    void runTaskLater(Runnable runnable, long delay);

    /**
     * Runs a repeating task.
     */
    Object runTaskTimer(Runnable runnable, long delay, long period);

    /**
     * Runs a task asynchronously.
     */
    void runTaskAsync(Runnable runnable);

    /**
     * Runs a task on the thread that owns the given entity.
     * In Bukkit, this is the main thread.
     * In Folia, this is the region thread for the entity.
     */
    void runTaskOnEntity(org.bukkit.entity.Entity entity, Runnable runnable);

    /**
     * Cancels a scheduled task.
     */
    void cancelTask(Object task);
}
