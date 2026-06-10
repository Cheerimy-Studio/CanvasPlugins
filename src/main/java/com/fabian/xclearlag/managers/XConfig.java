package com.fabian.xclearlag.managers;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import com.fabian.xclearlag.utils.DebugLogger;
import java.util.*;

/**
 * Clean container for modular plugin settings.
 */
public class XConfig {

    public final GeneralSettings general;
    public final TpsSettings tps;
    public final ManualClearSettings manualClear;
    public final Map<String, TaskConfig> tasks = new HashMap<>();

    public XConfig(FileConfiguration config) {
        // 1. General Settings
        this.general = new GeneralSettings(
            config.getString("prefix", "&8[&bX-ClearLag&8] &7"),
            config.getString("language", "en"),
            config.getStringList("worlds.blacklist"),
            config.getBoolean("check-updates", true),
            config.getBoolean("debug", false),
            config.getBoolean("metrics", true),
            config.getBoolean("bossbar.enabled", false),
            config.getString("bossbar.color", "RED"),
            config.getString("bossbar.style", "SOLID"),
            config.getString("bossbar.title", "&e&lLimpiando en &c&l%time%s")
        );

        // 2. TPS Settings
        this.tps = new TpsSettings(
            config.getBoolean("tps-check.enabled", true),
            config.getDouble("tps-check.threshold", 16.0),
            config.getInt("tps-check.interval", 60),
            config.getStringList("tps-check.task-to-run"),
            config.getLong("tps-check.cooldown-seconds", 60) * 1000L,
            config.getInt("tps-check.consecutive-checks-before-trigger", 3)
        );

        // 3. Manual Clear Settings
        this.manualClear = new ManualClearSettings(
            config.getBoolean("manual-clear.enabled", true),
            config.getStringList("manual-clear.tasks"),
            config.getBoolean("manual-clear.allow-specific", true),
            config.getBoolean("manual-clear.broadcast", true),
            config.getBoolean("manual-clear.ignore-min-players", true),
            config.getBoolean("manual-clear.instant", true),
            config.getInt("manual-clear.cooldown", 30)
        );

        // 5. Tasks
        ConfigurationSection taskSection = config.getConfigurationSection("tasks");
        if (taskSection != null) {
            for (String key : taskSection.getKeys(false)) {
                tasks.put(key.toLowerCase(), new TaskConfig(taskSection.getConfigurationSection(key)));
            }
        }
        DebugLogger.debug("XConfig", "Parsed " + tasks.size() + " tasks, TPS enabled=" + tps.enabled + ", ManualClear enabled=" + manualClear.enabled);
    }

    public static class TaskConfig {
        public final boolean enabled;
        public final int interval;
        public final int minPlayers;
        public final boolean removeDrops;
        public final boolean protectNamed;
        public final boolean protectLeashed;
        public final boolean protectNearPlayer;
        public final int nearPlayerRadius;
        public final List<Integer> countdown;
        public final List<String> entities;
        public final List<String> protectedEntities;
        public final List<String> commands;
        public final Map<Integer, List<String>> countdownCommands = new HashMap<>();

        public TaskConfig(ConfigurationSection section) {
            this.enabled = section.getBoolean("enabled", true);
            this.interval = section.getInt("interval", 300);
            this.minPlayers = section.getInt("min-players", 0);
            this.removeDrops = section.getBoolean("remove-drops", true);
            this.protectNamed = section.getBoolean("protect-named", true);
            this.protectLeashed = section.getBoolean("protect-leashed", true);
            this.protectNearPlayer = section.getBoolean("protect-near-player", false);
            this.nearPlayerRadius = section.getInt("near-player-radius", 16);
            this.countdown = section.getIntegerList("countdown");
            this.entities = section.getStringList("entities");
            this.protectedEntities = section.getStringList("protected-entities");
            this.commands = section.getStringList("commands");

            ConfigurationSection ccSection = section.getConfigurationSection("countdown-commands");
            if (ccSection != null) {
                for (String sec : ccSection.getKeys(false)) {
                    try {
                        countdownCommands.put(Integer.parseInt(sec), ccSection.getStringList(sec));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
    }
}