package com.fabian.xclearlag.utils;

import com.fabian.xclearlag.XClearlag;
import com.fabian.xclearlag.managers.MessageManager;
import com.fabian.xclearlag.utils.scheduler.SchedulerAdapter;
import com.fabian.xclearlag.utils.DebugLogger;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker {

    private final XClearlag plugin;
    private final SchedulerAdapter schedulerAdapter;
    private static final int RESOURCE_ID = 132713;
    private String latestVersion;
    private boolean updateAvailable;

    public UpdateChecker(XClearlag plugin) {
        this.plugin = plugin;
        this.schedulerAdapter = plugin.getSchedulerAdapter();
        this.updateAvailable = false;
    }

    public void checkForUpdates() {
        checkForUpdates(null);
    }

    public void checkForUpdates(CommandSender sender) {
        DebugLogger.debug("UpdateChecker", "Checking for updates (sender=" + (sender != null ? sender.getName() : "console") + ")...");
        schedulerAdapter.runTaskAsync(() -> {
            try {
                String currentVersion = plugin.getDescription().getVersion();

                // Spigot API for resource versions
                URL url = new URL("https://api.spigotmc.org/legacy/update.php?resource=" + RESOURCE_ID);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "Fabian/X-Clearlag/" + currentVersion);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String version = reader.readLine();
                reader.close();

                this.latestVersion = version;
                MessageManager lang = plugin.getMessageManager();

                if (latestVersion != null && isNewerVersion(currentVersion, latestVersion)) {
                    this.updateAvailable = true;
                    DebugLogger.debug("UpdateChecker", "Update available: " + currentVersion + " -> " + latestVersion);

                    if (sender != null) {
                        sender.sendMessage(
                                lang.getWithContext(sender, "update-available", "%current%", currentVersion, "%latest%", latestVersion));
                        sender.sendMessage(lang.getWithContext(sender, "update-download", "%url%", getDownloadUrl()));
                    } else {
                        Bukkit.getConsoleSender()
                                .sendMessage(
                                        lang.getWithContext(null, "update-available", "%current%", currentVersion, "%latest%", latestVersion));
                        Bukkit.getConsoleSender()
                                .sendMessage(lang.getWithContext(null, "update-download", "%url%", getDownloadUrl()));
                    }
                } else {
                    if (sender != null) {
                        sender.sendMessage(lang.getWithContext(sender, "update-current"));
                    } else {
                        Bukkit.getConsoleSender().sendMessage(lang.getWithContext(null, "update-current"));
                    }
                }

            } catch (Exception e) {
                DebugLogger.debug("UpdateChecker", "Update check failed.", e);
                if (sender != null) {
                    sender.sendMessage(plugin.getMessageManager().getWithContext(sender, "update-error"));
                } else {
                    Bukkit.getConsoleSender()
                            .sendMessage(plugin.getMessageManager().getWithContext(null, "update-error"));
                }
            }
        });
    }

    private boolean isNewerVersion(String current, String remote) {
        if (remote == null || remote.isEmpty())
            return false;

        // Remove 'v' or 'V' if present
        String v1 = current.toLowerCase().replace("v", "");
        String v2 = remote.toLowerCase().replace("v", "");

        String[] currentParts = v1.split("\\.");
        String[] remoteParts = v2.split("\\.");

        int length = Math.max(currentParts.length, remoteParts.length);
        for (int i = 0; i < length; i++) {
            int currentPart = i < currentParts.length ? Integer.parseInt(currentParts[i].replaceAll("[^0-9]", "")) : 0;
            int remotePart = i < remoteParts.length ? Integer.parseInt(remoteParts[i].replaceAll("[^0-9]", "")) : 0;

            if (remotePart > currentPart)
                return true;
            if (remotePart < currentPart)
                return false;
        }

        return false;
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public String getDownloadUrl() {
        return "https://www.spigotmc.org/resources/" + RESOURCE_ID + "/";
    }
}
