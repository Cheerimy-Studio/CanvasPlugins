package com.tntoptimizer.config;

import com.tntoptimizer.TNTOptimizer;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * 插件配置封装
 * <p>
 * 与 Canvas 的优化层无冲突：所有配置项均为独立命名空间，
 * 不修改任何 Bukkit/Paper 系统属性。
 */
public class PluginConfig {

    private final TNTOptimizer plugin;
    private final FileConfiguration cfg;

    public PluginConfig(TNTOptimizer plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.cfg = plugin.getConfig();
    }

    // ──────────── 基础设置 ────────────

    public boolean isEnabled() {
        return cfg.getBoolean("settings.enabled", true);
    }

    public int getAsyncThreadPoolSize() {
        // 0 或负数 = 自动（按 CPU 核心数），正数 = 使用配置值
        return cfg.getInt("settings.async-thread-pool-size", 0);
    }

    // ──────────── 聚类设置 ────────────

    public boolean isClusteringEnabled() {
        return cfg.getBoolean("settings.clustering.enabled", true);
    }

    public int getMinTNTCountForClustering() {
        return cfg.getInt("settings.clustering.min-tnt-count", 3);
    }

    public double getClusterDistanceMultiplier() {
        return cfg.getDouble("settings.clustering.distance-multiplier", 2.5);
    }

    public int getMaxClusterSize() {
        return cfg.getInt("settings.clustering.max-cluster-size", 64);
    }

    // ──────────── 快照设置 ────────────

    public int getSnapshotRadius() {
        return cfg.getInt("settings.snapshot.radius", 8);
    }

    // ──────────── 爆炸设置 ────────────

    /**
     * 是否触发 EntityExplodeEvent（TNT 类插件兼容性）
     * 关闭可提升少量性能，但依赖该事件的插件（床战争、TNT 大炮等）会失效
     */
    public boolean isFireEntityExplodeEvent() {
        return cfg.getBoolean("settings.explosion.fire-entity-explode-event", true);
    }

    public enum DropMode {
        /** 原版爆炸掉落衰减（每个掉落物约 1/威力 概率保留，TNT 约 25%） */
        VANILLA,
        /** 100% 掉落（大多数服务器偏好） */
        FULL
    }

    public DropMode getDropMode() {
        try {
            return DropMode.valueOf(cfg.getString("settings.explosion.drop-mode", "VANILLA").toUpperCase());
        } catch (Exception e) {
            return DropMode.VANILLA;
        }
    }

    /**
     * 跨区域并行写回：爆炸跨越多个 Region 时，
     * 每个 Region 由各自的区域线程并行写回（Folia 不同 Region 由不同线程拥有）
     */
    public boolean isMultiRegionWriteback() {
        return cfg.getBoolean("settings.explosion.multi-region-writeback", true);
    }

    public enum RayMode {
        NORMAL,    // 标准 1352 条射线
        REDUCED,   // 512 条射线（性能优先）
        ADAPTIVE   // 自适应
    }

    public RayMode getRayMode() {
        try {
            return RayMode.valueOf(
                    cfg.getString("settings.explosion.ray-mode", "ADAPTIVE").toUpperCase());
        } catch (IllegalArgumentException e) {
            return RayMode.ADAPTIVE;
        }
    }

    public int getAdaptiveRayMultiplier() {
        return cfg.getInt("settings.explosion.adaptive-ray-multiplier", 80);
    }

    public int getMaxRays() {
        return cfg.getInt("settings.explosion.max-rays", 1352);
    }

    // ──────────── 性能监控 ────────────

    public boolean isMetricsEnabled() {
        return cfg.getBoolean("settings.metrics.enabled", true);
    }

    public int getMetricsLogInterval() {
        return cfg.getInt("settings.metrics.log-interval", 100);
    }

    public boolean isDebug() {
        return cfg.getBoolean("settings.debug", false);
    }

    // ──────────── 消息 ────────────

    public String getMessage(String key) {
        return cfg.getString("messages.prefix", "§8[§cTNT-Optimizer§8]§r") + " "
                + cfg.getString("messages." + key, "");
    }
}