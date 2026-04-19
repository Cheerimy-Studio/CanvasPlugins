package com.fabian.xclearlag.api;

import com.fabian.xclearlag.config.XConfig;
import org.bukkit.command.CommandSender;
import java.util.function.Consumer;

/**
 * Interface for entity cleanup operations.
 */
public interface CleanupService {

    /**
     * Executes a cleanup task asynchronously.
     * 
     * @param taskName   The name of the task (from config).
     * @param task       The configuration of the task to run.
     * @param reason     The reason for this cleanup.
     * @param sender     The sender who triggered the cleanup (if any).
     * @param onComplete Callback with the total number of entities removed.
     */
    void execute(String taskName, XConfig.TaskConfig task, CleanupReason reason, CommandSender sender, Consumer<Integer> onComplete);
}
