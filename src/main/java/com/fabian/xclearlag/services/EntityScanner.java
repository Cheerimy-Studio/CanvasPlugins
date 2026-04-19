package com.fabian.xclearlag.services;

import com.fabian.xclearlag.utils.scheduler.SchedulerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Handles non-blocking entity discovery across all loaded chunks.
 */
public class EntityScanner {

    private final SchedulerAdapter schedulerAdapter;

    public EntityScanner(SchedulerAdapter schedulerAdapter) {
        this.schedulerAdapter = schedulerAdapter;
    }

    /**
     * Starts a non-blocking scan for entities matching the filter.
     * 
     * @param chunksPerTick How many chunks to process each tick.
     * @param disabledWorlds List of world names to ignore.
     * @param filter         Predicate to determine if an entity should be "discovered".
     * @param onComplete     Callback with the list of found entities.
     */
    public void scan(int chunksPerTick, List<String> disabledWorlds, Predicate<Entity> filter, Consumer<List<Entity>> onComplete) {
        ScanTask taskLogic = new ScanTask(chunksPerTick, disabledWorlds, filter, onComplete, schedulerAdapter);
        Object task = schedulerAdapter.runTaskTimer(taskLogic, 0L, 1L);
        taskLogic.setTaskHandle(task);
    }

    private static class ScanTask implements Runnable {
        private final int limitPerTick;
        private final List<String> disabledWorlds;
        private final Predicate<Entity> filter;
        private final Consumer<List<Entity>> onComplete;
        private final SchedulerAdapter schedulerAdapter;
        
        private Object taskHandle;
        private final Queue<World> worldQueue;
        private Iterator<Entity> currentEntityIterator;
        private final List<Entity> discoveredEntities = new ArrayList<>();
        private boolean finished = false;

        public ScanTask(int chunksPerTick, List<String> disabledWorlds, Predicate<Entity> filter, Consumer<List<Entity>> onComplete, SchedulerAdapter schedulerAdapter) {
            // A chunk scan is much heavier than an entity filter.
            // We multiply by a factor to maintain similar performance profile.
            this.limitPerTick = chunksPerTick * 100; 
            this.disabledWorlds = disabledWorlds;
            this.filter = filter;
            this.onComplete = onComplete;
            this.schedulerAdapter = schedulerAdapter;
            this.worldQueue = new LinkedList<>(Bukkit.getWorlds());
            
            if (!prepareNextWorld()) {
                finish();
            }
        }

        private void finish() {
            if (finished) return;
            finished = true;
            onComplete.accept(discoveredEntities);
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

        private boolean prepareNextWorld() {
            while (!worldQueue.isEmpty()) {
                World world = worldQueue.poll();
                if (disabledWorlds.contains(world.getName())) continue;
                currentEntityIterator = world.getEntities().iterator();
                return true;
            }
            return false;
        }

        @Override
        public void run() {
            if (finished) return;

            try {
                int processed = 0;
                while (processed < limitPerTick) {
                    // 1. Ensure we have an active entity iterator
                    while (currentEntityIterator == null || !currentEntityIterator.hasNext()) {
                        if (!prepareNextWorld()) { // No more worlds left to prepare
                            finish();
                            return; // Task completed
                        }
                        // If prepareNextWorld() found a world but it has no entities, loop to try next world in the same tick
                    }

                    // 2. Process an entity from the current iterator
                    Entity entity = currentEntityIterator.next();
                    if (entity != null && entity.isValid()) {
                        if (filter.test(entity)) {
                            discoveredEntities.add(entity);
                        }
                    }
                    processed++;
                }

                // If we finished processing 'limitPerTick' entities, but there are no more entities in the *current* world
                // and no more worlds in the queue, then we are done.
                if (!currentEntityIterator.hasNext() && worldQueue.isEmpty()) {
                    finish();
                }

            } catch (Exception e) {
                Bukkit.getConsoleSender().sendMessage("§c[X-ClearLag] Error scanning entities: " + e.getMessage());
                finish();
            }
        }
    }
}
