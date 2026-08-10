package luminus.acng.features.gameplay.miscs

import luminus.acng.Main.config
import luminus.acng.msg
import org.bukkit.entity.Player
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.simpleCommand
import taboolib.platform.BukkitPlugin

/**
 * 自杀命令：/suicide（别名 /514）（权限：2b2tcore.suicide，默认仅 OP）
 * 不再注册 /kill（与原版冲突导致别名 514 失效）
 * 所有玩家（含 OP）统一用 EntityScheduler 设置 health = 0，Folia 线程安全
 */
object Suicide {
    @Awake(LifeCycle.ENABLE)
    fun init() {
        simpleCommand(
            "suicide",
            aliases = arrayListOf("514"),
            permission = "2b2tcore.suicide",
            permissionDefault = PermissionDefault.TRUE
        ) { sender, _ ->
            if (!config.getBoolean("suicide-enable", true)) return@simpleCommand
            if (sender !is Player) return@simpleCommand

            sender.scheduler.run(BukkitPlugin.getInstance(), { _ ->
                sender.damage(99999.0)
            }, null)
            config.getString("messages.suicide", "")?.takeIf { it.isNotEmpty() }?.let { sender.msg(it) }
        }
    }
}