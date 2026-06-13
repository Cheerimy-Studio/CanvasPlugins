package com.fabian.xclearlag.managers;

import com.fabian.xclearlag.XClearlag;
import com.fabian.xclearlag.utils.ColorUtils;
import com.fabian.xclearlag.utils.DebugLogger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages localized messages and color translation.
 */
public class LanguageManager {

    private final XClearlag plugin;
    private final Map<String, String> messages = new HashMap<>();

    public LanguageManager(XClearlag plugin) {
        this.plugin = plugin;
    }

    public void load() {
        messages.clear();
        DebugLogger.debug("Messages", "Loading messages...");
        XConfig config = plugin.getConfigManager().get();
        String lang = (config != null) ? config.general.language : plugin.getConfig().getString("language", "en");
        lang = lang.toLowerCase().trim();

        String fileName = lang + ".yml";
        File messagesFolder = new File(plugin.getDataFolder(), "messages");

        if (!messagesFolder.exists()) {
            messagesFolder.mkdirs();
        }

        File messagesFile = new File(messagesFolder, fileName);

        if (!messagesFile.exists()) {
            DebugLogger.debug("Messages", "Message file not found, extracting default: " + fileName);
            InputStream resource = plugin.getResource("messages/" + fileName);
            if (resource != null) {
                plugin.saveResource("messages/" + fileName, false);
            }
        }

        FileConfiguration messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        // Auto-sync missing keys
        YamlConfiguration defaults = null;
        try (InputStream ds = plugin.getResource("messages/" + fileName)) {
            if (ds != null) {
                defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(ds, StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            plugin.logWarning("Failed to load default messages for " + fileName + ": " + e.getMessage());
        }

        if (defaults == null) {
            try (InputStream ds = plugin.getResource("messages/en.yml")) {
                if (ds != null) {
                    defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(ds, StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                plugin.logWarning("Failed to load fallback en.yml messages: " + e.getMessage());
            }
        }

        if (defaults != null) {
            boolean modified = false;
            for (String key : defaults.getKeys(true)) {
                if (!messagesConfig.contains(key) && !defaults.isConfigurationSection(key)) {
                    messagesConfig.set(key, defaults.get(key));
                    modified = true;
                }
            }
            
            if (modified) {
                try {
                    messagesConfig.save(messagesFile);
                    plugin.logInfo("&eSynchronized missing keys in &f" + fileName);
                } catch (Exception e) {
                    plugin.logWarning("Failed to sync keys in " + fileName + ": " + e.getMessage());
                }
            }
            messagesConfig.setDefaults(defaults);
        }

        for (String key : messagesConfig.getKeys(true)) {
            if (!messagesConfig.isConfigurationSection(key)) {
                String val = messagesConfig.getString(key);
                if (val != null) {
                    messages.put(key, ColorUtils.translateColors(val));
                }
            }
        }
        DebugLogger.debug("Messages", "Loaded " + messages.size() + " message keys from " + fileName);
    }

    public String getWithContext(org.bukkit.command.CommandSender sender, String key, String... replacements) {
        String msg = messages.getOrDefault(key, "");
        if (msg.isEmpty()) return "";

        XConfig config = plugin.getConfigManager().get();
        if (config != null) {
            String prefix = ColorUtils.translateColors(config.general.prefix);
            msg = msg.replace("%prefix%", prefix);
        }

        for (int i = 0; i + 1 < replacements.length; i += 2) {
            msg = msg.replace(replacements[i], replacements[i + 1]);
        }

        if (plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                if (sender instanceof org.bukkit.entity.Player) {
                    msg = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders((org.bukkit.entity.Player) sender, msg);
                } else {
                    msg = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(null, msg);
                }
            } catch (Exception ignored) {}
        }

        return ColorUtils.translateColors(msg);
    }

    public String get(String key, String... replacements) {
        return getWithContext(null, key, replacements);
    }
}
