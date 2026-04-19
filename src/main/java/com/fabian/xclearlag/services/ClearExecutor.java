package com.fabian.xclearlag.services;

import com.fabian.xclearlag.api.CleanupReason;
import com.fabian.xclearlag.api.CleanupService;
import com.fabian.xclearlag.api.events.*;
import com.fabian.xclearlag.XClearlag;
import com.fabian.xclearlag.config.*;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/**
 * Orchestrates the cleanup process by delegating to specialized scanner and processor.
 */
public class ClearExecutor implements CleanupService {

    private final ConfigManager configManager;
    private final EntityScanner scanner;
    private final CleanupProcessor processor;

    public ClearExecutor(XClearlag plugin, ConfigManager configManager) {
        this.configManager = configManager;
        this.scanner = new EntityScanner(plugin.getSchedulerAdapter());
        this.processor = new CleanupProcessor(plugin.getSchedulerAdapter());
    }

    @Override
    public void execute(String taskName, XConfig.TaskConfig taskConfig, CleanupReason reason, CommandSender sender, Consumer<Integer> onComplete) {
        // 1. Fire Pre-Clear Event
        XPreClearEvent preEvent = new XPreClearEvent(taskName, taskConfig, sender);
        Bukkit.getPluginManager().callEvent(preEvent);
        if (preEvent.isCancelled()) {
            onComplete.accept(0);
            return;
        }

        XConfig globalConfig = configManager.get();

        // 2. Discover entities (Non-blocking)
        scanner.scan(Integer.MAX_VALUE, globalConfig.general.disabledWorlds, (entity) -> shouldRemove(entity, taskConfig), (found) -> {
            
            // 3. Batch remove (Non-blocking)
            processor.process(found, Integer.MAX_VALUE, (removed) -> {
                
                // 4. Fire Post-Clear Event
                XPostClearEvent postEvent = new XPostClearEvent(taskName, taskConfig, removed);
                Bukkit.getPluginManager().callEvent(postEvent);
                
                onComplete.accept(removed);
            });
        });
    }

    private boolean shouldRemove(Entity entity, XConfig.TaskConfig taskConfig) {
        if (entity == null || !entity.isValid()) return false;
        if (entity instanceof Player) return false;
        
        if (taskConfig.protectNearPlayer) {
            double radiusSquared = taskConfig.nearPlayerRadius * taskConfig.nearPlayerRadius;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getWorld().equals(entity.getWorld()) && p.getLocation().distanceSquared(entity.getLocation()) <= radiusSquared) {
                    return false;
                }
            }
        }
        
        // Items
        if (entity instanceof Item) {
            if (!taskConfig.removeDrops) return false;
            Item item = (Item) entity;
            String typeName = item.getItemStack().getType().name();
            if (taskConfig.protectedEntities.contains(typeName)) return false;
            
            if (taskConfig.protectNamed && item.getItemStack().hasItemMeta() && item.getItemStack().getItemMeta().hasDisplayName()) {
                return false;
            }
            return true;
        }

        // Mobs/Other
        String typeName = entity.getType().name();
        if (taskConfig.protectedEntities.contains(typeName)) return false;
        
        if (taskConfig.protectNamed && entity.getCustomName() != null) {
            return false;
        }

        if (taskConfig.protectLeashed && entity instanceof org.bukkit.entity.LivingEntity) {
            if (((org.bukkit.entity.LivingEntity) entity).isLeashed()) {
                return false;
            }
        }

        return taskConfig.entities.contains(typeName) || taskConfig.entities.contains("ALL");
    }
}
