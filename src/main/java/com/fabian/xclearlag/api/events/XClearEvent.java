package com.fabian.xclearlag.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Base event for all X-Clearlag activities.
 */
public abstract class XClearEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
