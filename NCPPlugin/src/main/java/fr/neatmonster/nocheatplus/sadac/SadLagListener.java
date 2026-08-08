/*
 * SadAC Lag Detection — ported from Nukkit to Paper/Folia API
 * 
 * 功能:
 * 1. 高频红石检测：监听 BlockRedstoneEvent，频率超过阈值自动移除红石元件
 * 2. 区块实体清理：定期扫描玩家附近区块，清理多余的经验球和船
 *
 * Folia 26.2 兼容:
 * - 使用 GlobalRegionScheduler 替代 BukkitRunnable
 * - ConcurrentHashMap 线程安全
 */

package fr.neatmonster.nocheatplus.sadac;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
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

import fr.neatmonster.nocheatplus.compat.SchedulerHelper;
import fr.neatmonster.nocheatplus.config.ConfigFile;
import fr.neatmonster.nocheatplus.config.ConfigManager;
import fr.neatmonster.nocheatplus.logging.StaticLog;
import fr.neatmonster.nocheatplus.logging.Streams;

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

    // ── 高频红石跟踪 ──
    private final Map<String, Long> redstoneUpdateTimes = new ConcurrentHashMap<>(256);

    // ── 定时任务 ID ──
    private Object entityCheckTaskId = null;

    /* ================================================================
     * 高频红石检测 — BlockRedstoneEvent
     * Paper 原生事件，Folia 兼容
     * ================================================================ */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRedstone(final BlockRedstoneEvent event) {
        if (!enabled) return;

        final Block block = event.getBlock();
        final Material type = block.getType();

        // 只检测红石相关方块
        if (type != Material.REDSTONE_TORCH
                && type != Material.REDSTONE_WALL_TORCH
                && type != Material.COMPARATOR
                && type != Material.REPEATER
                && type != Material.REDSTONE_WIRE) {
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

        // 高频红石 —— 移除
        final Location loc = block.getLocation();
        block.setType(Material.AIR);
        StaticLog.logInfo("[NCP+SadAC] 移除高频红石 " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());
    }

    /* ================================================================
     * 区块实体清理 — 定期扫描 (Folia: GlobalRegionScheduler)
     * 每 30 秒 (1200 ticks) 扫描一次
     * ================================================================ */

    private void scheduleEntityCheck() {
        if (entityCheckTaskId != null) {
            SchedulerHelper.cancelTask(entityCheckTaskId);
        }
        // 使用 SchedulerHelper（Folia 兼容：自动选择 GlobalRegionScheduler 或 BukkitScheduler）
        entityCheckTaskId = SchedulerHelper.runSyncRepeatingTask(
                Bukkit.getPluginManager().getPlugin("NoCheatPlus"),
                (task) -> checkChunkEntities(),
                600L, 600L
        );
    }

    private void checkChunkEntities() {
        if (!enabled) return;

        for (final Player player : Bukkit.getOnlinePlayers()) {
            final World world = player.getWorld();
            final int cx = player.getLocation().getBlockX() >> 4;
            final int cz = player.getLocation().getBlockZ() >> 4;

            // 扫描玩家附近 10×10=100 个区块
            for (int dx = -10; dx <= 10; dx++) {
                for (int dz = -10; dz <= 10; dz++) {
                    if (!world.isChunkLoaded(cx + dx, cz + dz)) continue;

                    final Entity[] entities = world.getChunkAt(cx + dx, cz + dz).getEntities();
                    int targetCount = 0;

                    // 统计经验球 + 船的数量
                    for (final Entity e : entities) {
                        if (e instanceof ExperienceOrb || e instanceof Boat) {
                            targetCount++;
                        }
                    }

                    // 超过阈值则清理
                    if (targetCount > maxEntitiesPerChunk) {
                        int toRemove = targetCount - maxEntitiesPerChunk;
                        int removed = 0;
                        for (final Entity e : entities) {
                            if ((e instanceof ExperienceOrb || e instanceof Boat) && removed < toRemove) {
                                e.remove();
                                removed++;
                            }
                        }
                        if (removed > 0) {
                            StaticLog.logInfo("[NCP+SadAC] 清理区块 " + (cx + dx) + "," + (cz + dz)
                                    + " 多余实体 " + removed + " 个");
                        }
                    }
                }
            }
        }
    }

    /* ================================================================
     * 配置热更新 — 兼容 /sad lag set <param> <value>
     * ================================================================ */

    public void reloadConfig() {
        final ConfigFile config = ConfigManager.getConfigFile();

        this.enabled = config.getBoolean("sadac.lag.enabled", true);
        this.redstoneIntervalMs = config.getInt("sadac.lag.redstone-interval-ms", 50);
        this.maxEntitiesPerChunk = config.getInt("sadac.lag.max-entities-per-chunk", 50);

        if (this.enabled) {
            scheduleEntityCheck();
        } else if (entityCheckTaskId != null) {
            SchedulerHelper.cancelTask(entityCheckTaskId);
            entityCheckTaskId = null;
        }
    }

    // 运行时设值（/sad lag set）
    public void setRedstoneIntervalMs(int ms) { this.redstoneIntervalMs = ms; }
    public void setMaxEntitiesPerChunk(int max) { this.maxEntitiesPerChunk = max; }
    public void setEnabled(boolean en) {
        this.enabled = en;
        if (en) scheduleEntityCheck();
        else if (entityCheckTaskId != null) { SchedulerHelper.cancelTask(entityCheckTaskId); entityCheckTaskId = null; }
    }
    public boolean isEnabled() { return enabled; }
    public int getRedstoneIntervalMs() { return redstoneIntervalMs; }
    public int getMaxEntitiesPerChunk() { return maxEntitiesPerChunk; }

    /* ── 工具 ── */

    private static String blockKey(final Block block) {
        return block.getWorld().getName() + "_" + block.getX() + "_" + block.getY() + "_" + block.getZ();
    }
}
