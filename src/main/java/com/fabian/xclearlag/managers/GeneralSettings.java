package com.fabian.xclearlag.managers;

import java.util.List;

public class GeneralSettings {
    public final String prefix;
    public final String language;
    public final List<String> disabledWorlds;

    // Standard X plugin settings
    public final boolean checkUpdates;
    public final boolean debug;
    public final boolean metrics;

    // BossBar settings
    public final boolean bossBarEnabled;
    public final String bossBarColor;
    public final String bossBarStyle;
    public final String bossBarTitle;

    public GeneralSettings(String prefix, String language, List<String> disabledWorlds,
                           boolean checkUpdates, boolean debug, boolean metrics,
                           boolean bossBarEnabled, String bossBarColor, String bossBarStyle, String bossBarTitle) {
        this.prefix = prefix;
        this.language = language;
        this.disabledWorlds = disabledWorlds;
        this.checkUpdates = checkUpdates;
        this.debug = debug;
        this.metrics = metrics;
        this.bossBarEnabled = bossBarEnabled;
        this.bossBarColor = bossBarColor;
        this.bossBarStyle = bossBarStyle;
        this.bossBarTitle = bossBarTitle;
    }
}