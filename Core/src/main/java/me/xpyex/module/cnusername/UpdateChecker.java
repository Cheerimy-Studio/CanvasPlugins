package me.xpyex.module.cnusername;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 仅保留版本号读取功能，删除旧的网络更新检查逻辑
 */
public class UpdateChecker {
    public static String version = "";

    static {
        try (InputStream is = UpdateChecker.class.getClassLoader().getResourceAsStream("version")) {
            if (is != null) {
                version = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
        } catch (IOException e) {
            // 忽略
        }
    }
}
