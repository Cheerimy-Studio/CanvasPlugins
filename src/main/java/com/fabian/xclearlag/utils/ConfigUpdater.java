package com.fabian.xclearlag.utils;

import com.fabian.xclearlag.XClearlag;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility to update configuration files while preserving comments.
 */
public class ConfigUpdater {

    /**
     * Updates a local YAML file by adding missing keys from a resource file.
     */
    public static void update(XClearlag plugin, String resourcePath, File diskFile) {
        if (!diskFile.exists()) return;

        try {
            YamlConfiguration resConfig = null;
            try (InputStream resourceStream = plugin.getResource(resourcePath)) {
                if (resourceStream == null) return;
                resConfig = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(resourceStream, StandardCharsets.UTF_8));
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to update " + diskFile.getName() + ": " + e.getMessage());
                return;
            }

            if (resConfig == null) return;

            YamlConfiguration diskConfig = new YamlConfiguration();
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(diskFile),
                    StandardCharsets.UTF_8)) {
                diskConfig.load(reader);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to read disk config " + diskFile.getName() + ": " + e.getMessage()
                        + " — Skipping update to prevent data loss.");
                return;
            }

            boolean isFlat = true;
            for (String key : resConfig.getKeys(true)) {
                if (key.contains(".")) {
                    isFlat = false;
                    break;
                }
            }

            if (isFlat) {
                updateFlatFile(plugin, resourcePath, diskFile, diskConfig);
            } else {
                updateHierarchicalFile(plugin, diskFile, diskConfig, resConfig);
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to update " + diskFile.getName() + ": " + e.getMessage());
        }
    }

    private static void updateFlatFile(XClearlag plugin, String resourcePath, File diskFile, YamlConfiguration diskConfig) throws IOException {
        List<String> resLines = new ArrayList<>();
        InputStream resStream = plugin.getResource(resourcePath);
        if (resStream == null) return;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                resLines.add(line);
            }
        }

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(diskFile), StandardCharsets.UTF_8))) {
            for (String line : resLines) {
                if (line.trim().startsWith("#") || line.trim().isEmpty()) {
                    writer.write(line);
                    writer.newLine();
                    continue;
                }

                int colonIndex = line.indexOf(':');
                if (colonIndex != -1) {
                    String key = line.substring(0, colonIndex).trim();
                    if (diskConfig.contains(key)) {
                        writer.write(key + ": " + formatValue(diskConfig.get(key)));
                    } else {
                        writer.write(line);
                    }
                } else {
                    writer.write(line);
                }
                writer.newLine();
            }
        }
    }

    private static void updateHierarchicalFile(XClearlag plugin, File diskFile, YamlConfiguration diskConfig, YamlConfiguration resConfig) throws IOException {
        List<String> missingKeys = new ArrayList<>();
        for (String key : resConfig.getKeys(true)) {
            if (!diskConfig.contains(key)) {
                missingKeys.add(key);
            }
        }

        if (missingKeys.isEmpty()) return;

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(diskFile, true), StandardCharsets.UTF_8))) {
            writer.newLine();

            for (String key : missingKeys) {
                if (resConfig.getConfigurationSection(key) == null) {
                    int depth = key.split("\\.").length - 1;
                    String indent = "";
                    for (int i = 0; i < depth; i++) {
                        indent += "  ";
                    }

                    String nodeName = key.contains(".") ? key.substring(key.lastIndexOf(".") + 1) : key;
                    Object value = resConfig.get(key);
                    String formattedValue = formatValue(value);

                    writer.write(indent + nodeName + ": " + formattedValue);
                    writer.newLine();
                }
            }
        }
    }

    private static String formatValue(Object value) {
        if (value == null) return "''";
        if (value instanceof String) {
            String s = (String) value;
            if (s.contains("'") || s.contains("&") || s.contains("\"")) {
                return "\"" + s.replace("\"", "\\\"") + "\"";
            }
            return "\"" + s + "\"";
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                sb.append(formatValue(list.get(i)));
                if (i < list.size() - 1) sb.append(", ");
            }
            sb.append("]");
            return sb.toString();
        }
        return value.toString();
    }
}