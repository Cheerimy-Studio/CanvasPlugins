package com.fabian.xclearlag.managers;

import com.fabian.xclearlag.XClearlag;
import com.fabian.xclearlag.utils.ConfigUpdater;
import com.fabian.xclearlag.utils.DebugLogger;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;

/**
 * Manages the plugin configuration file and provides access to the XConfig POJO.
 */
public class ConfigManager {

    private final XClearlag plugin;
    private XConfig config;

    public ConfigManager(XClearlag plugin) {
        this.plugin = plugin;
    }

    public void load() {
        DebugLogger.debug("Config", "Loading configuration...");
        File configFile = new File(plugin.getDataFolder(), "config.yml");

        if (configFile.exists()) {
            DebugLogger.debug("Config", "config.yml found on disk.");
            // Read code from disk config
            YamlConfiguration diskYaml = YamlConfiguration.loadConfiguration(configFile);
            int diskCode = diskYaml.getInt("code", 0);

            // Read code from JAR default config
            YamlConfiguration jarYaml = null;
            try (java.io.InputStream is = plugin.getResource("config.yml")) {
                if (is != null) {
                    jarYaml = YamlConfiguration.loadConfiguration(
                            new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8));
                }
            } catch (Exception ignored) {}

            int jarCode = (jarYaml != null) ? jarYaml.getInt("code", 0) : 0;

            if (diskCode < jarCode) {
                DebugLogger.debug("Config", "Config outdated (disk=" + diskCode + " < jar=" + jarCode + "), updating...");
                plugin.logInfo("Outdated configuration detected (code " + diskCode + " < " + jarCode + "). Updating config...");

                // Backup current config
                File backupFile = new File(plugin.getDataFolder(), "config_old.yml");
                if (backupFile.exists()) backupFile.delete();
                configFile.renameTo(backupFile);
                plugin.logInfo("Old config backed up as config_old.yml");

                // Save fresh default config from JAR
                plugin.saveDefaultConfig();
                File newConfigFile = new File(plugin.getDataFolder(), "config.yml");

                // Use ConfigUpdater to migrate old values into the new file
                ConfigUpdater.update(plugin, "config.yml", newConfigFile);
                plugin.logInfo("Configuration updated successfully via ConfigUpdater.");
            } else {
                // Config is up to date, still run ConfigUpdater to add any missing keys
                DebugLogger.debug("Config", "Config up to date, running ConfigUpdater for missing keys...");
                ConfigUpdater.update(plugin, "config.yml", configFile);
            }
        }

        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = new XConfig(plugin.getConfig());
        DebugLogger.debug("Config", "Configuration loaded. Debug=" + this.config.general.debug + ", Tasks=" + this.config.tasks.size() + ", DisabledWorlds=" + this.config.general.disabledWorlds);
    }

    /**
     * @return The immutable configuration POJO.
     */
    public XConfig get() {
        return config;
    }
}