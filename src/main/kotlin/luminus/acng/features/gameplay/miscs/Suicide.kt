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
 * 自杀命令：/suicide（别名 514、kill）（权限：core.suicide，默认仅 OP）
 * 普通玩家使用 EntityScheduler 设置 health = 0，确保 Folia 线程安全
 * OP 走原版 kill 命令以支持选择器参数
 */
object Suicide {
    @Awake(LifeCycle.ENABLE)
    fun init() {
        simpleCommand(
            "kill",
            aliases = arrayListOf("514", "suicide"),
            permission = "core.suicide",
            permissionDefault = PermissionDefault.OP
        ) { sender, args ->
            if (!config.getBoolean("suicide-enable", true)) return@simpleCommand
            if (sender !is Player) return@simpleCommand

            if (sender.isOp) {
                // OP 走原版 kill 命令，支持选择器参数
                val arg = args.joinToString(" ").trim()
                sender.performCommand(if (arg.isEmpty()) "minecraft:kill @s" else "minecraft:kill $arg")
                return@simpleCommand
            }
            // 修复：原版 kill() 扩展在 Folia 上可能不生效，改用 EntityScheduler
            sender.scheduler.run(BukkitPlugin.getInstance(), { _ ->
                sender.health = 0.0
            }, null)
            config.getString("messages.suicide", "")?.takeIf { it.isNotEmpty() }?.let { sender.msg(it) }
        }
    }
}
