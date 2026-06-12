package com.fabian.xclearlag.managers;

import com.fabian.xclearlag.utils.scheduler.SchedulerAdapter;
import com.fabian.xclearlag.utils.DebugLogger;
import org.bukkit.entity.Entity;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
/**
 * Handles batch removal of entities with tick-rate limiting.
 */
public class CleanupProcessor {

    private final SchedulerAdapter schedulerAdapter;

    public CleanupProcessor(SchedulerAdapter schedulerAdapter) {
        this.schedulerAdapter = schedulerAdapter;
    }

    /**
     * Removes a list of entities in batches.
     * 
     * @param entities   The list of entities to remove.
     * @param batchSize  How many entities to remove each tick (0 for instant).
     * @param onComplete Callback with total entities removed.
     */
    public void process(List<Entity> entities, int batchSize, Consumer<Integer> onComplete) {
        DebugLogger.debug("CleanupProcessor", "Processing " + entities.size() + " entities (batchSize=" + batchSize + ")");
        if (entities.isEmpty()) {
            onComplete.accept(0);
            return;
        }

        if (batchSize <= 0 || entities.size() <= batchSize) {
            int count = 0;
            for (Entity e : entities) {
                if (e != null && e.isValid()) {
                    schedulerAdapter.runTaskOnEntity(e, e::remove);
                    count++;
                }
            }
            DebugLogger.debug("CleanupProcessor", "Instant removal complete: " + count + " entities.");
            onComplete.accept(count);
            return;
        }

        RemovalTask taskLogic = new RemovalTask(entities, batchSize, onComplete, schedulerAdapter);
        Object task = schedulerAdapter.runTaskTimer(taskLogic, 0L, 1L);
        taskLogic.setTaskHandle(task);
        DebugLogger.debug("CleanupProcessor", "Batched removal started (batchSize=" + batchSize + ").");
    }

    private static class RemovalTask implements Runnable {
        private final Queue<Entity> queue;
        private final int batchSize;
        private final Consumer<Integer> onComplete;
        private final SchedulerAdapter schedulerAdapter;
        
        private Object taskHandle;
        private int totalRemoved = 0;
        private boolean finished = false;

        public RemovalTask(List<Entity> entities, int batchSize, Consumer<Integer> onComplete, SchedulerAdapter schedulerAdapter) {
            this.queue = new LinkedList<>(entities);
            this.batchSize = batchSize;
            this.onComplete = onComplete;
            this.schedulerAdapter = schedulerAdapter;
        }

        private void finish() {
            if (finished) return;
            finished = true;
            DebugLogger.debug("CleanupProcessor", "Batched removal complete: " + totalRemoved + " entities removed.");
            onComplete.accept(totalRemoved);
            if (taskHandle != null) {
                schedulerAdapter.cancelTask(taskHandle);
            }
        }

        public void setTaskHandle(Object handle) {
            this.taskHandle = handle;
            if (finished && handle != null) {
                schedulerAdapter.cancelTask(handle);
            }
        }

        @Override
        public void run() {
            if (finished) return;

            try {
                int count = 0;
                while (!queue.isEmpty() && count < batchSize) {
                    Entity e = queue.poll();
                    if (e != null && e.isValid()) {
                        schedulerAdapter.runTaskOnEntity(e, e::remove);
                        totalRemoved++;
                    }
                    count++;
                }

                if (queue.isEmpty()) {
                    finish();
                }
            } catch (Exception e) {
                Bukkit.getLogger().severe("[X-ClearLag] Critical error during entity removal: " + e.getMessage());
                e.printStackTrace();
                finish();
            }
        }
    }
}
