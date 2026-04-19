package com.fabian.xclearlag.config;

import java.util.List;

public class ManualClearSettings {
    public final boolean enabled;
    public final List<String> tasks;
    public final boolean allowSpecific;
    public final boolean broadcast;
    public final boolean ignoreMinPlayers;
    public final boolean instant;
    public final int cooldown;

    public ManualClearSettings(boolean enabled, List<String> tasks, boolean allowSpecific,
                               boolean broadcast, boolean ignoreMinPlayers, boolean instant, int cooldown) {
        this.enabled = enabled;
        this.tasks = tasks;
        this.allowSpecific = allowSpecific;
        this.broadcast = broadcast;
        this.ignoreMinPlayers = ignoreMinPlayers;
        this.instant = instant;
        this.cooldown = cooldown;
    }
}
