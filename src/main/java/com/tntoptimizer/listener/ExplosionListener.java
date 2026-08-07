package com.tntoptimizer.listener;

import com.tntoptimizer.TNTOptimizer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ExplosionPrimeEvent;

/**
 * TNT 爆炸事件监听器
 * <p>
 * 拦截 ExplosionPrimeEvent，取消原版同步爆炸处理，
 * 转交 AsyncExplosionHandler 做异步多线程处理。
 * <p>
 * 仅处理 TNTPrimed 实体，不影响苦力怕、床、凋零等爆炸。
 * 与 Canvas 的实体优化完全解耦，不冲突。
 */
public class ExplosionListener implements Listener {

    private final TNTOptimizer plugin;

    public ExplosionListener(TNTOptimizer plugin) {
        this.plugin = plugin;
    }

    /**
     * 拦截 TNT 引爆事件
     * <p>
     * 优先级 HIGHEST：确保在其他插件之前拦截，避免冲突。
     * ignoreCancelled = false：即使其他插件取消，我们也处理（但要尊重取消状态）。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplosionPrime(ExplosionPrimeEvent event) {
        // 只处理 TNT
        if (!(event.getEntity() instanceof TNTPrimed tnt)) return;

        // 检查插件是否启用
        if (!plugin.isOptimizationEnabled()) return;

        // 在移除实体前捕获位置和世界（移除后 getWorld() 可能返回 null）
        Location loc = tnt.getLocation().clone();
        World world = tnt.getWorld();
        float power = event.getRadius();

        // 取消原版爆炸（同步、阻塞区域线程）
        event.setCancelled(true);

        // 立即移除 TNT 实体 — 不再冻结 fuse（冻结会导致玩家看到 TNT 半天不爆）
        // EntityExplodeEvent 兼容性由 fireEntityExplodeEvent 临时生成实体解决
        tnt.remove();

        // 转交异步处理器（携带 UUID，写回时触发兼容事件）
        plugin.getExplosionHandler().handleExplosion(loc, world, power, tnt.getUniqueId());
    }
}