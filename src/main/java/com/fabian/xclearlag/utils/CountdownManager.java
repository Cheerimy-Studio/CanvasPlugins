package com.fabian.xclearlag.utils;

import java.util.List;
import java.util.ArrayList;

/**
 * Manages the countdown state for a task.
 */
public class CountdownManager {

    private final int interval;
    private final List<Integer> warningSeconds;
    private int currentSeconds;

    public CountdownManager(int interval, List<Integer> warningSeconds) {
        this.interval = interval;
        this.warningSeconds = warningSeconds != null ? warningSeconds : new ArrayList<>();
        this.currentSeconds = interval;
        DebugLogger.debug("Countdown", "Created: interval=" + interval + "s, warnings=" + this.warningSeconds);
    }

    /**
     * Decrements the counter.
     * @return Seconds left.
     */
    public int decrement() {
        if (currentSeconds > 0) {
            currentSeconds--;
        }
        return currentSeconds;
    }

    public void reset() {
        this.currentSeconds = interval;
        DebugLogger.debug("Countdown", "Reset to " + interval + "s.");
    }

    public int getCurrentSeconds() {
        return currentSeconds;
    }

    public boolean isWarning(int seconds) {
        return warningSeconds.contains(seconds);
    }
}
