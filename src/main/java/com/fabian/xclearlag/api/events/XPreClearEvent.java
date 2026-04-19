package com.fabian.xclearlag.api.events;

import com.fabian.xclearlag.config.XConfig;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Cancellable;

/**
 * Fired BEFORE a cleanup task begins its discovery phase.
 * Cancellable: other plugins can prevent this cleanup from occurring.
 */
public class XPreClearEvent extends XClearEvent implements Cancellable {
    private final String taskName;
    private final XConfig.TaskConfig config;
    private final CommandSender trigger;
    private boolean cancelled = false;

    public XPreClearEvent(String taskName, XConfig.TaskConfig config, CommandSender trigger) {
        this.taskName = taskName;
        this.config = config;
        this.trigger = trigger;
    }

    public String getTaskName() { return taskName; }
    public XConfig.TaskConfig getConfig() { return config; }
    public CommandSender getTrigger() { return trigger; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }
}
