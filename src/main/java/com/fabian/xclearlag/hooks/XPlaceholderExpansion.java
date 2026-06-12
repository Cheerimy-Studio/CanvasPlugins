package com.fabian.xclearlag.hooks;

import com.fabian.xclearlag.XClearlag;
import com.fabian.xclearlag.managers.ClearTask;
import com.fabian.xclearlag.metrics.Metrics;
import com.fabian.xclearlag.utils.DebugLogger;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

/**
 * Native PlaceholderAPI expansion for X-Clearlag.
 */
public class XPlaceholderExpansion extends PlaceholderExpansion {

    private final XClearlag plugin;

    public XPlaceholderExpansion(XClearlag plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "xclearlag";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Fabian";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        DebugLogger.debug("PAPI", "Placeholder request: xclearlag_" + params);
        if (params.equalsIgnoreCase("tps")) {
            return String.format("%.2f", plugin.getTpsMonitor().getTPS());
        }

        if (params.equalsIgnoreCase("last_removed")) {
            java.util.List<Metrics.CleanupRecord> history = plugin.getMetricsTracker().getHistory();
            if (history.isEmpty()) return "0";
            return String.valueOf(history.get(0).removed);
        }

        if (params.equalsIgnoreCase("total_removed")) {
            return String.valueOf(plugin.getMetricsTracker().getTotalRemoved());
        }

        // %xclearlag_next_clear_<task>%
        if (params.startsWith("next_clear_")) {
            String taskName = params.substring(11).toLowerCase();
            ClearTask task = plugin.getTaskManager().getTaskMap().get(taskName);
            if (task != null) {
                int seconds = task.getCountdownManager().getCurrentSeconds();
                return String.valueOf(seconds);
            }
            return "N/A";
        }

        return null;
    }
}
