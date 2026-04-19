package com.fabian.xclearlag.config;

import java.util.List;

public class GeneralSettings {
    public final String prefix;
    public final String language;
    public final List<String> disabledWorlds;

    // BossBar settings moved here
    public final boolean bossBarEnabled;
    public final String bossBarColor;
    public final String bossBarStyle;
    public final String bossBarTitle;

    public GeneralSettings(String prefix, String language, List<String> disabledWorlds,
                           boolean bossBarEnabled, String bossBarColor, String bossBarStyle, String bossBarTitle) {
        this.prefix = prefix;
        this.language = language;
        this.disabledWorlds = disabledWorlds;
        this.bossBarEnabled = bossBarEnabled;
        this.bossBarColor = bossBarColor;
        this.bossBarStyle = bossBarStyle;
        this.bossBarTitle = bossBarTitle;
    }
}
