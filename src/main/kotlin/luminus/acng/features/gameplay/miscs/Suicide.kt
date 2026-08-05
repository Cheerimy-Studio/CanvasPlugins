package luminus.acng.features.gameplay.miscs

import luminus.acng.Main.config
import luminus.acng.msg
import org.bukkit.entity.Player
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.simpleCommand
import taboolib.platform.util.kill

/**
 * 自杀命令：/suicide（别名 514、kill）（权限：core.suicide，默认允许）
 * simpleCommand 在玩家区域线程执行，kill() 操作玩家自身，Folia 安全
 */
object Suicide {
    @Awake(LifeCycle.ENABLE)
    fun init() {
        simpleCommand(
            "kill",
            aliases = arrayListOf("514", "suicide"),
            permission = "core.suicide",
            permissionDefault = PermissionDefault.TRUE
        ) { sender, args ->
            if (!config.getBoolean("suicide-enable", true)) return@simpleCommand
            if (sender !is Player) return@simpleCommand

            if (sender.isOp) {
                // OP 走原版 kill 命令，支持选择器参数
                val arg = args.joinToString(" ").trim()
                sender.performCommand(if (arg.isEmpty()) "minecraft:kill @s" else "minecraft:kill $arg")
                return@simpleCommand
            }
            // 修复：原版 !! 在配置缺失时 NPE
            sender.kill()
            config.getString("messages.suicide", "")?.takeIf { it.isNotEmpty() }?.let { sender.msg(it) }
        }
    }
}
