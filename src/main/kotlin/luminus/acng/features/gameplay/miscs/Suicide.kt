package luminus.acng.features.gameplay.miscs

import luminus.acng.Main.config
import luminus.acng.msg
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.platform.BukkitPlugin

/**
 * 自杀命令：/suicide（别名 /514）
 *
 * 权限：2b2tcore.suicide（默认所有人可用）。
 * 不再注册 /kill（与原版冲突导致别名失效）。
 *
 * Folia 26.2 线程安全：
 * - https://folia.earth/faq#bukkit-scheduler: Folia 在玩家命令执行时已处于
 *   entity region 线程，直接操作 health / damage 是安全的。
 * - 回退方案通过 GlobalRegionScheduler 反射调用（兼容旧版 Paper/Spigot）。
 */
object Suicide {

    @Awake(LifeCycle.ENABLE)
    fun init() {
        val plugin = BukkitPlugin.getInstance()
        val cmd = plugin.getCommand("suicide") ?: run {
            plugin.logger.warning("2B2TCore: /suicide 命令未在 plugin.yml 中注册！")
            return
        }

        cmd.setExecutor { sender, _, _, _ ->
            if (!config.getBoolean("suicide-enable", true)) return@setExecutor true
            if (sender !is Player) return@setExecutor true
            if (!sender.hasPermission("2b2tcore.suicide")) return@setExecutor true

            // 执行自杀（Folia 下命令在 entity region 线程执行，setHealth 安全）
            killPlayer(sender, plugin)

            val msg = config.getString("messages.suicide", "")
            if (!msg.isNullOrEmpty()) {
                sender.sendMessage(msg.replace('&', '§'))
            }
            true
        }
    }

    /**
     * 多层回退自杀实现，确保在任何 Paper/Spigot/Folia 环境下都能工作。
     */
    private fun killPlayer(player: Player, plugin: BukkitPlugin) {
        // 方案 1：直接设置血量（Folia 命令线程 = entity region 线程）
        try {
            player.health = 0.0
            return
        } catch (_: Exception) { }

        // 方案 2：damage 高额伤害
        try {
            player.damage(99999.0)
            return
        } catch (_: Exception) { }

        // 方案 3：GlobalRegionScheduler 反射（旧版 Paper 回退）
        try {
            val server = Bukkit.getServer()
            val getScheduler = server.javaClass.getMethod("getGlobalRegionScheduler")
            val scheduler = getScheduler.invoke(server)
            scheduler.javaClass.getMethod("run", org.bukkit.plugin.Plugin::class.java,
                java.util.function.Consumer::class.java)
                .invoke(scheduler, plugin, java.util.function.Consumer<Any?> {
                    player.health = 0.0
                })
            return
        } catch (_: Exception) { }

        // 方案 4：Bukkit.dispatchCommand（Spigot 回退）
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "kill ${player.name}")
        } catch (_: Exception) { }

        // 方案 5：setHealth 最后尝试（同步调度）
        try {
            Bukkit.getScheduler().runTask(plugin, Runnable { player.health = 0.0 })
        } catch (_: Exception) {
            plugin.logger.warning("2B2TCore: /suicide 所有方案均失败（${player.name}）")
        }
    }
}
