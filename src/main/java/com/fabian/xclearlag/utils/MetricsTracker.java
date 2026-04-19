package com.fabian.xclearlag.utils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks cleanup statistics and performance history.
 */
public class MetricsTracker {

    private final TPSMonitor tpsMonitor;
    private final Map<String, Integer> recentStats = new ConcurrentHashMap<>();
    private final List<CleanupRecord> history = Collections.synchronizedList(new LinkedList<>());
    private static final int MAX_HISTORY = 50;

    public MetricsTracker(TPSMonitor tpsMonitor) {
        this.tpsMonitor = tpsMonitor;
    }

    public void record(String taskName, int removed) {
        recentStats.merge(taskName, removed, Integer::sum);
        
        CleanupRecord record = new CleanupRecord(
            taskName, 
            removed, 
            System.currentTimeMillis(), 
            tpsMonitor.getTPS()
        );
        
        synchronized (history) {
            history.add(0, record);
            if (history.size() > MAX_HISTORY) {
                history.remove(history.size() - 1);
            }
        }
    }

    public Map<String, Integer> getRecentStats() {
        return new HashMap<>(recentStats);
    }

    public List<CleanupRecord> getHistory() {
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

    public int getTotalRemoved() {
        return recentStats.values().stream().mapToInt(Integer::intValue).sum();
    }

    public double getAverageRemoved() {
        if (history.isEmpty()) return 0;
        return (double) getTotalRemoved() / history.size();
    }

    public void reset() {
        recentStats.clear();
        history.clear();
    }

    /**
     * Data class for cleanup history.
     */
    public static class CleanupRecord {
        public final String taskName;
        public final int removed;
        public final long timestamp;
        public final double tpsAtTime;

        public CleanupRecord(String taskName, int removed, long timestamp, double tpsAtTime) {
            this.taskName = taskName;
            this.removed = removed;
            this.timestamp = timestamp;
            this.tpsAtTime = tpsAtTime;
        }
    }
}
