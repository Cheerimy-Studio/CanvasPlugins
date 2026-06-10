package com.fabian.xclearlag.managers;

import com.fabian.xclearlag.XClearlag;

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

    private static final int LATEST_VERSION = 2; // Incrementar para forzar migración

    public void load() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        
        if (configFile.exists()) {
            YamlConfiguration currentYaml = YamlConfiguration.loadConfiguration(configFile);
            int version = currentYaml.getInt("config-version", 0);
            
            if (version < LATEST_VERSION) {
                plugin.getLogger().info("Outdated configuration detected (v" + version + "). Migrating to v" + LATEST_VERSION + "...");
                migrate(configFile, currentYaml);
            }
        }
        
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = new XConfig(plugin.getConfig());
    }

    private void migrate(File configFile, YamlConfiguration oldYaml) {
        try {
            // 1. Respaldar configuración vieja
            File backupFile = new File(plugin.getDataFolder(), "config_old.yml");
            if (backupFile.exists()) backupFile.delete();
            configFile.renameTo(backupFile);
            
            // 2. Generar nueva configuración desde el recurso interno
            plugin.saveDefaultConfig();
            plugin.reloadConfig();
            FileConfiguration newYaml = plugin.getConfig();
            
            // 3. Migrar valores uno a uno (solo si existen en la vieja)
            for (String key : oldYaml.getKeys(true)) {
                // No migramos la versión ni secciones, solo valores finales
                if (key.equals("config-version") || oldYaml.isConfigurationSection(key)) continue;
                
                // Solo migramos si la clave aún existe en el nuevo formato
                if (newYaml.contains(key)) {
                    newYaml.set(key, oldYaml.get(key));
                }
            }
            
            // 4. Actualizar versión y guardar
            newYaml.set("config-version", LATEST_VERSION);
            plugin.saveConfig();
            plugin.getLogger().info("Configuration migration completed successfully. Old config saved as config_old.yml");
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to migrate configuration: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * @return The immutable configuration POJO.
     */
    public XConfig get() {
        return config;
    }
}
