package cc.baka9.catseedlogin.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;

/**
 * Folia 兼容调度工具类。
 * <p>
 * Paper 1.19.4+ 的 BukkitScheduler 在 Folia 上不可用，改用 Folia 的
 * GlobalRegionScheduler / AsyncScheduler / EntityScheduler。
 * 通过反射调用以避免与旧 Paper 版本硬绑定，同时保持 Spigot/Paper/Folia 三端兼容。
 */
public final class Scheduler {

    private Scheduler() {
    }

    /** 全局主线程调度（不依赖任何区域） */
    public static void runGlobal(Plugin plugin, Runnable task) {
        try {
            // Folia: Bukkit.getGlobalRegionScheduler().execute(plugin, task)
            Object globalScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            globalScheduler.getClass().getMethod("execute", Plugin.class, Runnable.class)
                    .invoke(globalScheduler, plugin, task);
        } catch (Exception fallback) {
            // Spigot/Paper 旧版: 主线程
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /** 全局主线程延迟调度 */
    public static void runGlobalLater(Plugin plugin, Runnable task, long delayTicks) {
        try {
            // Folia: Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task, delayTicks)
            Object globalScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            globalScheduler.getClass().getMethod("runDelayed", Plugin.class, Consumer.class, long.class)
                    .invoke(globalScheduler, plugin, (Consumer<Object>) ignored -> task.run(), delayTicks);
        } catch (Exception fallback) {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    /** 区域线程调度（按实体所在区域） */
    public static <T extends Entity> void runEntity(T entity, Plugin plugin, Consumer<T> task) {
        try {
            // Folia: entity.getScheduler().run(plugin, task, null)
            Object entityScheduler = entity.getClass().getMethod("getScheduler").invoke(entity);
            entityScheduler.getClass().getMethod("run", Plugin.class, Consumer.class, Runnable.class)
                    .invoke(entityScheduler, plugin, task, null);
        } catch (Exception fallback) {
            task.accept(entity);
        }
    }

    /** 区域线程调度（按位置） */
    public static void runRegion(Location location, Plugin plugin, Runnable task) {
        try {
            Object regionScheduler = Bukkit.class.getMethod("getRegionScheduler").invoke(null);
            regionScheduler.getClass().getMethod("execute", Plugin.class, Location.class, Runnable.class)
                    .invoke(regionScheduler, plugin, location, task);
        } catch (Exception fallback) {
            task.run();
        }
    }

    /** 异步线程调度（数据库/IO 操作） */
    public static void runAsync(Plugin plugin, Runnable task) {
        try {
            // Folia: Bukkit.getAsyncScheduler().runNow(plugin, task)
            Object asyncScheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
            asyncScheduler.getClass().getMethod("runNow", Plugin.class, Consumer.class)
                    .invoke(asyncScheduler, plugin, (Consumer<Object>) ignored -> task.run());
        } catch (Exception fallback) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    /** 世界区域线程调度（定时任务） */
    public static void runGlobalTimer(Plugin plugin, Runnable task, long initialDelayTicks, long periodTicks) {
        try {
            Object globalScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            globalScheduler.getClass().getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class)
                    .invoke(globalScheduler, plugin, (Consumer<Object>) ignored -> task.run(), initialDelayTicks, periodTicks);
        } catch (Exception fallback) {
            Bukkit.getScheduler().runTaskTimer(plugin, task, initialDelayTicks, periodTicks);
        }
    }

    /** 获取默认世界（Folia 可能无主世界概念，取第一个世界） */
    public static World getDefaultWorld() {
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }

    /** Folia 安全传送：优先 teleportAsync（跨世界不阻塞），回退同步 teleport */
    public static void safeTeleport(org.bukkit.entity.Player player, Location location) {
        if (location == null || player == null) return;
        try {
            player.teleportAsync(location);
        } catch (NoSuchMethodError e) {
            player.teleport(location);
        }
    }
}
