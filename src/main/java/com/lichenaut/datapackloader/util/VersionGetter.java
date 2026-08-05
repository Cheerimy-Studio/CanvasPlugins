package com.lichenaut.datapackloader.util;

import lombok.RequiredArgsConstructor;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Consumer;

/**
 * 版本号读取（已删除旧的 Spigot 更新检查逻辑）
 */
@RequiredArgsConstructor
public class VersionGetter {

    private final JavaPlugin plugin;
    private final GenUtil genUtil;

    public void getVersion(final Consumer<String> consumer) {
        consumer.accept(plugin.getDescription().getVersion());
    }
}
