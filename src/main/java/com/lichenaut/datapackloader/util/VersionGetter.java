package com.lichenaut.datapackloader.util;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Scanner;
import java.util.function.Consumer;

@RequiredArgsConstructor
public class VersionGetter {

    private final JavaPlugin plugin;
    private final GenUtil genUtil;

    public void getVersion(final Consumer<String> consumer) {
        if (genUtil.isFolia()) {
            plugin.getServer().getAsyncScheduler().runNow(plugin, task -> fetchVersion(consumer));
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> fetchVersion(consumer));
        }
    }

    private void fetchVersion(Consumer<String> consumer) {
        try (InputStream inputStream = new URI("https://api.github.com/repos/Cheerimy-Studio/MinecraftPlugins/releases/latest")
                .toURL()
                .openStream(); Scanner scanner = new Scanner(inputStream)) {
            StringBuilder sb = new StringBuilder();
            while (scanner.hasNext()) sb.append(scanner.next());
            String json = sb.toString();
            int idx = json.indexOf("\"tag_name\":\"DatapackLoader-v");
            if (idx >= 0) {
                String tag = json.substring(idx + 28, json.indexOf("\"", idx + 28));
                consumer.accept(tag);
            }
        } catch (IOException | URISyntaxException e) {
            // 更新检查失败不影响正常使用
        }
    }
}