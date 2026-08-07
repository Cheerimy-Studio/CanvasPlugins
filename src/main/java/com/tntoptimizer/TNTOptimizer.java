package com.tntoptimizer;

import com.tntoptimizer.config.PluginConfig;
import com.tntoptimizer.explosion.AsyncExplosionHandler;
import com.tntoptimizer.listener.ExplosionListener;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicLong;

/**
 * TNT-Optimizer 主插件类
 * <p>
 * 基于 Folia 多线程架构，通过异步射线追踪 + 空间聚类实现 TNT 爆炸并行处理。
 * 完全兼容 CraftCanvasMC/Canvas 插件规范，不修改任何 NMS 代码。
 */
public final class TNTOptimizer extends JavaPlugin {

    private PluginConfig config;
    private AsyncExplosionHandler explosionHandler;
    private ExplosionListener explosionListener;

    // 性能统计
    final AtomicLong totalProcessed = new AtomicLong(0);
    final AtomicLong totalTimeNanos = new AtomicLong(0);
    volatile boolean enabled = true;

    public long getTotalProcessed() { return totalProcessed.get(); }
    public long getTotalTimeNanos() { return totalTimeNanos.get(); }

    @Override
    public void onEnable() {
        // 保存默认配置
        saveDefaultConfig();

        // 加载配置
        this.config = new PluginConfig(this);

        // 初始化异步爆炸处理器
        this.explosionHandler = new AsyncExplosionHandler(this);

        // 注册事件监听器
        this.explosionListener = new ExplosionListener(this);
        Bukkit.getPluginManager().registerEvents(explosionListener, this);

        // 注册命令
        var cmd = getCommand("tntoptimizer");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }

        getLogger().info("TNT-Optimizer v" + getDescription().getVersion() + " 已启用");
        int cores = Runtime.getRuntime().availableProcessors();
        int poolCfg = config.getAsyncThreadPoolSize();
        getLogger().info("  异步线程池: " + (poolCfg > 0 ? Math.min(poolCfg, 64) : Math.min(cores, Math.max(4, cores * 3 / 4))) + " 线程"
                + (cores > 16 ? " (疑似双路 CPU)" : ""));
        getLogger().info("  空间聚类: " + (config.isClusteringEnabled() ? "启用" : "禁用"));
        getLogger().info("  射线模式: " + config.getRayMode());
        getLogger().info("  兼容 Folia " + Bukkit.getMinecraftVersion() + " / Canvas");
    }

    @Override
    public void onDisable() {
        if (explosionHandler != null) {
            explosionHandler.shutdown();
        }
        getLogger().info("TNT-Optimizer 已禁用。处理了 " + totalProcessed.get() + " 次爆炸");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        if (args.length == 0) {
            sendStatus(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                reloadConfig();
                this.config = new PluginConfig(this);
                this.explosionHandler.reloadConfig();
                sender.sendMessage(colorize(config.getMessage("reloaded")));
            }
            case "status" -> sendStatus(sender);
            case "toggle" -> {
                enabled = !enabled;
                sender.sendMessage(colorize(enabled
                        ? config.getMessage("enabled")
                        : config.getMessage("disabled")));
            }
            default -> sendStatus(sender);
        }
        return true;
    }

    private void sendStatus(CommandSender sender) {
        long processed = totalProcessed.get();
        long totalNanos = totalTimeNanos.get();
        double avgMs = processed > 0
                ? (totalNanos / (double) processed) / 1_000_000.0
                : 0.0;

        String msg = config.getMessage("status")
                .replace("%status%", enabled ? "§a启用" : "§c禁用")
                .replace("%processed%", String.valueOf(processed))
                .replace("%avg_time%", String.format("%.2f", avgMs));
        sender.sendMessage(colorize(msg));
    }

    // ──────────────────── 公开 API ────────────────────

    public PluginConfig getPluginConfig() {
        return config;
    }

    public AsyncExplosionHandler getExplosionHandler() {
        return explosionHandler;
    }

    /**
     * 优化功能是否启用（不同于 JavaPlugin.isEnabled()）
     */
    public boolean isOptimizationEnabled() {
        return enabled && isEnabled();
    }

    public void recordExplosion(long nanos) {
        totalProcessed.incrementAndGet();
        totalTimeNanos.addAndGet(nanos);
    }

    // ──────────────────── 工具方法 ────────────────────

    public static String colorize(String msg) {
        if (msg == null) return "";
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
}