package com.fabian.xclearlag.config;

import java.util.List;

public class TpsSettings {
    public final boolean enabled;
    public final double threshold;
    public final int interval;
    public final List<String> tasksToRun;
    public final long cooldownMs;
    public final int consecutiveChecks;

    public TpsSettings(boolean enabled, double threshold, int interval, 
                       List<String> tasksToRun, long cooldownMs, int consecutiveChecks) {
        this.enabled = enabled;
        this.threshold = threshold;
        this.interval = interval;
        this.tasksToRun = tasksToRun;
        this.cooldownMs = cooldownMs;
        this.consecutiveChecks = consecutiveChecks;
    }
}
