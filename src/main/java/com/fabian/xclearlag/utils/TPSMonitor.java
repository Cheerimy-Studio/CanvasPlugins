package com.fabian.xclearlag.utils;

/**
 * Monitors server Ticks Per Second (TPS) using wall-clock time.
 *
 * <p>IMPORTANT: This runnable MUST be scheduled with a period of 1 tick (1L),
 * NOT 20L. Scheduling it every 20 ticks would record one sample per second,
 * causing getTPS(200) to measure 200 *seconds* instead of 200 *ticks*, which
 * always yields TPS ≈ 1.0.
 *
 * <p>Correct registration in onEnable():
 * <pre>
 *   tpsMonitor.runTaskTimer(plugin, 1L, 1L);
 * </pre>
 *   
 *   Refactored for Folia support by implementing Runnable.
 */
public class TPSMonitor implements Runnable {

    /**
     * Ring buffer of wall-clock timestamps (ms) for each server tick.
     * 1200 entries = 60 seconds of ticks at 20 TPS.
     */
    private final long[] ticks = new long[1200];
    private int tickIndex = 0;
    private int tickCount = 0;

    {
        DebugLogger.debug("TPS", "TPSMonitor initialized (ring buffer: " + ticks.length + " ticks).");
    }

    @Override
    public void run() {
        ticks[tickIndex] = System.currentTimeMillis();
        tickIndex = (tickIndex + 1) % ticks.length;
        if (tickCount < ticks.length) tickCount++;
    }

    /**
     * Get average TPS over the last ~10 seconds (200 ticks at 20 TPS).
     *
     * @return TPS value clamped to [0, 20]. Returns 20.0 during warm-up.
     */
    public double getTPS() {
        return getTPS(200);
    }

    /**
     * Get average TPS over the most recent {@code interval} ticks.
     *
     * @param interval number of ticks to average (must be <= ring-buffer size)
     * @return TPS value clamped to [0, 20]. Returns 20.0 during warm-up.
     */
    public double getTPS(int interval) {
        // Not enough data yet — server just started, assume 20 TPS
        if (tickCount < interval + 1)
            return 20.0;

        int latest = (tickIndex - 1 + ticks.length) % ticks.length;
        int oldest = (tickIndex - 1 - interval + ticks.length) % ticks.length;

        long endTime   = ticks[latest];
        long startTime = ticks[oldest];

        long elapsed = endTime - startTime;
        if (elapsed <= 0)
            return 20.0;

        // interval ticks happened in `elapsed` milliseconds → ticks per second
        double tps = (interval * 1000.0) / elapsed;

        // Clamp: TPS can never exceed 20 and should never be negative
        return Math.max(0.0, Math.min(20.0, tps));
    }
}
