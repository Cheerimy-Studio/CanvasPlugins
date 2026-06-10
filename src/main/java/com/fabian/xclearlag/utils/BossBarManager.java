package com.fabian.xclearlag.utils;

import com.fabian.xclearlag.managers.*;

import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.lang.reflect.Method;
import java.lang.reflect.Array;

/**
 * Manages the BossBar display using 100% reflection for 1.8-1.21 compatibility.
 */
public class BossBarManager {

    private final ConfigManager configManager;
    private Object bossBar;
    private boolean enabled = false;

    private Class<?> bossBarClass;
    private Class<?> barColorClass;
    private Class<?> barStyleClass;
    private Class<?> barFlagClass;
    private Method createBossBarMethod;
    private Method addPlayerMethod;
    private Method setTitleMethod;
    private Method setProgressMethod;
    private Method setVisibleMethod;
    private Method removeAllMethod;

    public BossBarManager(ConfigManager configManager) {
        this.configManager = configManager;
        initReflection();
    }

    private void initReflection() {
        try {
            bossBarClass = Class.forName("org.bukkit.boss.BossBar");
            barColorClass = Class.forName("org.bukkit.boss.BarColor");
            barStyleClass = Class.forName("org.bukkit.boss.BarStyle");
            barFlagClass = Class.forName("org.bukkit.boss.BarFlag");

            Object emptyFlags = Array.newInstance(barFlagClass, 0);

            createBossBarMethod = Bukkit.class.getMethod("createBossBar", String.class, barColorClass, barStyleClass, emptyFlags.getClass());
            addPlayerMethod = bossBarClass.getMethod("addPlayer", Player.class);
            setTitleMethod = bossBarClass.getMethod("setTitle", String.class);
            setProgressMethod = bossBarClass.getMethod("setProgress", double.class);
            setVisibleMethod = bossBarClass.getMethod("setVisible", boolean.class);
            removeAllMethod = bossBarClass.getMethod("removeAll");

            enabled = true;
        } catch (Exception e) {
            enabled = false;
        }
    }

    public void update(int secondsLeft, int totalSeconds) {
        if (!enabled) return;
        
        XConfig config = configManager.get();
        if (!config.general.bossBarEnabled) {
            hide();
            return;
        }

        try {
            if (bossBar == null) {
                String title = ChatColor.translateAlternateColorCodes('&', config.general.bossBarTitle)
                        .replace("%time%", String.valueOf(secondsLeft));
                
                @SuppressWarnings("unchecked")
                Object color = Enum.valueOf((Class<Enum>) barColorClass, config.general.bossBarColor.toUpperCase());
                @SuppressWarnings("unchecked")
                Object style = Enum.valueOf((Class<Enum>) barStyleClass, config.general.bossBarStyle.toUpperCase());
                Object emptyFlags = Array.newInstance(barFlagClass, 0);

                bossBar = createBossBarMethod.invoke(null, title, color, style, emptyFlags);
            }

            double progress = (double) secondsLeft / totalSeconds;
            progress = Math.max(0.0, Math.min(1.0, progress));

            setTitleMethod.invoke(bossBar, ChatColor.translateAlternateColorCodes('&', config.general.bossBarTitle)
                    .replace("%time%", String.valueOf(secondsLeft)));
            setProgressMethod.invoke(bossBar, progress);

            for (Player player : Bukkit.getOnlinePlayers()) {
                addPlayerMethod.invoke(bossBar, player);
            }

            setVisibleMethod.invoke(bossBar, true);

        } catch (Exception e) {
            enabled = false;
        }
    }

    public void hide() {
        if (bossBar == null) return;
        try {
            setVisibleMethod.invoke(bossBar, false);
            removeAllMethod.invoke(bossBar);
            bossBar = null;
        } catch (Exception ignored) {}
    }
}
