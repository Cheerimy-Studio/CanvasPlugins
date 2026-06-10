package com.fabian.xclearlag.api.events;

import com.fabian.xclearlag.managers.XConfig;

/**
 * Fired AFTER a cleanup task has finished removing entities.
 */
public class XPostClearEvent extends XClearEvent {
    private final String taskName;
    private final XConfig.TaskConfig config;
    private final int entitiesRemoved;

    public XPostClearEvent(String taskName, XConfig.TaskConfig config, int entitiesRemoved) {
        this.taskName = taskName;
        this.config = config;
        this.entitiesRemoved = entitiesRemoved;
    }

    public String getTaskName() { return taskName; }
    public XConfig.TaskConfig getConfig() { return config; }
    public int getEntitiesRemoved() { return entitiesRemoved; }
}
