package com.fabian.xclearlag.managers;

import com.fabian.xclearlag.managers.*;
import com.fabian.xclearlag.utils.BossBarManager;
import com.fabian.xclearlag.utils.DebugLogger;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.logging.Logger;

/**
 * Handles all user-facing notifications (Chat, BossBar, ActionCenter, etc.)
 * Separates UI concerns from cleanup logic.
 */
public class CleanupNotifier {

    private final LanguageManager languageManager;
    private final BossBarManager bossBarManager;
    private final Logger logger;

    public CleanupNotifier(LanguageManager languageManager, BossBarManager bossBarManager, Logger logger) {
        this.languageManager = languageManager;
        this.bossBarManager = bossBarManager;
        this.logger = logger;
    }

    public void updateCountdown(int secondsLeft, int totalSeconds) {
        bossBarManager.update(secondsLeft, totalSeconds);
    }

    public void broadcast(String keyOrRaw, boolean raw, CommandSender context) {
        DebugLogger.debug("Notifier", "Broadcasting: " + keyOrRaw + " (raw=" + raw + ")");
        String msgTemplate = raw ? keyOrRaw : languageManager.get(keyOrRaw);
        if (msgTemplate.isEmpty()) return;

        // Use ConsoleSender to avoid double [X-Clearlag] prefix and preserve colors
        Bukkit.getConsoleSender().sendMessage(msgTemplate);

        boolean papiEnabled = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("xclearlag.notify")) {
                String personalizedMsg = msgTemplate;
                if (papiEnabled) {
                    try {
                        personalizedMsg = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(p, personalizedMsg);
                    } catch (Exception ignored) {}
                }
                p.sendMessage(personalizedMsg);
            }
        }
    }

    public void hideUI() {
        bossBarManager.hide();
    }
}
