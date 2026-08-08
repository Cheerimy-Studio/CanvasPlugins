/*
 * SadAC Lag Detection — ported from Nukkit to Paper/Folia API
 *
 * 功能:
 * 1. 高频红石检测：监听 BlockRedstoneEvent，频率超过阈值自动移除红石元件
 * 2. 区块实体清理：定期扫描玩家附近区块，清理多余的经验球和船
 *
 * Folia 26.2 兼容:
 * - 实体清理按区块调度到 RegionScheduler（区域线程安全）
 * - ConcurrentHashMap 线程安全
 * - 红石事件在区域线程触发，直接操作方块安全
 */

package fr.neatmonster.nocheatplus.sadac;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import fr.neatmonster.nocheatplus.compat.SchedulerHelper;
import fr.neatmonster.nocheatplus.config.ConfigFile;
import fr.neatmonster.nocheatplus.config.ConfigManager;

/**
 * SadAC 原版 Lag 检测 — 移植到 Paper/Folia API
 *
 * 配置路径: sadac.lag
 */
public class SadLagListener implements Listener {

    // ── 配置 (可运行时热更新) ──
    private volatile boolean enabled = true;
    private volatile int redstoneIntervalMs = 50;
    private volatile int maxEntitiesPerChunk = 50;
    private volatile int scanRadiusChunks = 3;

    // ── 静默统计（供 /sad status 查询，不刷日志） ──
    private final AtomicLong removedRedstoneCount = new AtomicLong();
    private final AtomicLong removedEntityCount = new AtomicLong();

    // ── 高频红石跟踪 ──
    private final Map<String, Long> redstoneUpdateTimes = new ConcurrentHashMap<>(256);

    // ── 定时任务 ID ──
    private Object entityCheckTaskId = null;

    private final JavaPlugin plugin;

    public SadLagListener(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /* ================================================================
     * 高频红石检测 — BlockRedstoneEvent
     * Paper 原生事件，Folia 上在区域线程触发，方块操作安全
     * ================================================================ */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRedstone(final BlockRedstoneEvent event) {
        if (!enabled) return;

        final Block block = event.getBlock();
        final Material type = block.getType();

        // 检测高频红石相关方块
        if (type != Material.REDSTONE_TORCH
                && type != Material.REDSTONE_WALL_TORCH
                && type != Material.COMPARATOR
                && type != Material.REPEATER
                && type != Material.REDSTONE_WIRE
                && type != Material.OBSERVER
                && type != Material.TARGET) {
            return;
        }

        final String blockKey = blockKey(block);
        final long now = System.currentTimeMillis();
        final Long lastUpdate = redstoneUpdateTimes.get(blockKey);

        // 首次或间隔足够长 —— 放行
        if (lastUpdate == null || now - lastUpdate > redstoneIntervalMs) {
            redstoneUpdateTimes.put(blockKey, now);
            return;
        }

        // 高频红石 —— 静默移除（禁用物理更新避免连锁爆炸）
        try {
            block.setType(Material.AIR, false);
            removedRedstoneCount.incrementAndGet();
        } catch (final Throwable ignored) {
            // 静默忽略：方块可能已被其他插件移除
        }
    }

    /* ================================================================
     * 区块实体清理 — 定期扫描
     * Folia: 按区块调度到 RegionScheduler（区域线程安全）
     * 非 Folia: 主线程直接清理
     * ================================================================ */

    private void scheduleEntityCheck() {
        if (entityCheckTaskId != null) {
            SchedulerHelper.cancelTask(entityCheckTaskId);
        }
        // 定时器本身在全局/主线程，仅负责派发；实际清理按区块调度
        entityCheckTaskId = SchedulerHelper.runSyncRepeatingTask(
                plugin,
                (task) -> dispatchChunkCleanup(),
                600L, 600L
        );
    }

    private void dispatchChunkCleanup() {
        if (!enabled) return;

        final int radius = scanRadiusChunks;
        for (final Player player : Bukkit.getOnlinePlayers()) {
            final World world = player.getWorld();
            final int cx = player.getLocation().getBlockX() >> 4;
            final int cz = player.getLocation().getBlockZ() >> 4;

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    final int chunkX = cx + dx;
                    final int chunkZ = cz + dz;
                    if (!world.isChunkLoaded(chunkX, chunkZ)) continue;
                    scheduleChunkCleanup(world, chunkX, chunkZ);
                }
            }
        }
    }

    /**
     * 在拥有该区块的线程上执行清理。
     * 非 Folia：调用方在主线程（BukkitScheduler），直接执行。
     * Folia：调度到 RegionScheduler，保证区域线程所有权。
     */
    private void scheduleChunkCleanup(final World world, final int chunkX, final int chunkZ) {
        if (!SchedulerHelper.isFoliaServer()) {
            cleanupChunk(world, chunkX, chunkZ);
            return;
        }
        try {
            final Method getRegionScheduler = Server.class.getMethod("getRegionScheduler");
            final Object regionScheduler = getRegionScheduler.invoke(Bukkit.getServer());
            final Method run = regionScheduler.getClass().getMethod(
                    "run", Plugin.class, World.class, int.class, int.class, Consumer.class);
            run.invoke(regionScheduler, plugin, world, chunkX, chunkZ,
                    (Consumer<Object>) t -> cleanupChunk(world, chunkX, chunkZ));
        } catch (final Throwable ignored) {
            // 调度失败静默跳过，避免刷错误日志
        }
    }

    private void cleanupChunk(final World world, final int chunkX, final int chunkZ) {
        if (!enabled || !world.isChunkLoaded(chunkX, chunkZ)) return;

        final Entity[] entities;
        try {
            entities = world.getChunkAt(chunkX, chunkZ).getEntities();
        } catch (final Throwable ignored) {
            return; // 区块加载竞态，跳过本轮
        }

        int targetCount = 0;
        for (final Entity e : entities) {
            if (e instanceof ExperienceOrb || e instanceof Boat) {
                targetCount++;
            }
        }

        if (targetCount <= maxEntitiesPerChunk) return;

        int toRemove = targetCount - maxEntitiesPerChunk;
        int removed = 0;
        for (final Entity e : entities) {
            if ((e instanceof ExperienceOrb || e instanceof Boat) && removed < toRemove) {
                try {
                    e.remove();
                    removed++;
                } catch (final Throwable ignored) {
                    // 实体可能已失效，静默跳过
                }
            }
        }
        if (removed > 0) {
            removedEntityCount.addAndGet(removed);
        }
    }

    /* ================================================================
     * 配置加载 / 持久化
     * ================================================================ */

    public void reloadConfig() {
        final ConfigFile config = ConfigManager.getConfigFile();

        this.enabled = config.getBoolean("sadac.lag.enabled", true);
        this.redstoneIntervalMs = config.getInt("sadac.lag.redstone-interval-ms", 50);
        this.maxEntitiesPerChunk = config.getInt("sadac.lag.max-entities-per-chunk", 50);
        this.scanRadiusChunks = config.getInt("sadac.lag.scan-radius", 3);
        if (this.scanRadiusChunks < 1) this.scanRadiusChunks = 1;
        if (this.scanRadiusChunks > 10) this.scanRadiusChunks = 10;

        if (this.enabled) {
            scheduleEntityCheck();
        } else if (entityCheckTaskId != null) {
            SchedulerHelper.cancelTask(entityCheckTaskId);
            entityCheckTaskId = null;
        }
    }

    /** 将当前运行时配置写回 config.yml（/sad 命令修改后持久化） */
    private void persistConfig() {
        try {
            final ConfigFile config = ConfigManager.getConfigFile();
            config.set("sadac.lag.enabled", enabled);
            config.set("sadac.lag.redstone-interval-ms", redstoneIntervalMs);
            config.set("sadac.lag.max-entities-per-chunk", maxEntitiesPerChunk);
            config.set("sadac.lag.scan-radius", scanRadiusChunks);
            config.save(new File(plugin.getDataFolder(), "config.yml"));
        } catch (final Throwable ignored) {
            // 持久化失败静默
        }
    }

    // 运行时设值（/sad lag set / on / off）— 立即生效并持久化
    public void setRedstoneIntervalMs(final int ms) {
        this.redstoneIntervalMs = Math.max(1, ms);
        persistConfig();
    }

    public void setMaxEntitiesPerChunk(final int max) {
        this.maxEntitiesPerChunk = Math.max(1, max);
        persistConfig();
    }

    public void setScanRadiusChunks(final int radius) {
        this.scanRadiusChunks = Math.max(1, Math.min(10, radius));
        persistConfig();
    }

    public void setEnabled(final boolean en) {
        this.enabled = en;
        if (en) {
            scheduleEntityCheck();
        } else if (entityCheckTaskId != null) {
            SchedulerHelper.cancelTask(entityCheckTaskId);
            entityCheckTaskId = null;
        }
        persistConfig();
    }

    public boolean isEnabled() { return enabled; }
    public int getRedstoneIntervalMs() { return redstoneIntervalMs; }
    public int getMaxEntitiesPerChunk() { return maxEntitiesPerChunk; }
    public int getScanRadiusChunks() { return scanRadiusChunks; }
    public long getRemovedRedstoneCount() { return removedRedstoneCount.get(); }
    public long getRemovedEntityCount() { return removedEntityCount.get(); }

    /* ── 工具 ── */

    private static String blockKey(final Block block) {
        return block.getWorld().getName() + "_" + block.getX() + "_" + block.getY() + "_" + block.getZ();
    }
}
