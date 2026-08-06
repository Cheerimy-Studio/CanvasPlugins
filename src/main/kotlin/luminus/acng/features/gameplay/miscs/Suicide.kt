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
 * 自杀命令：/suicide（别名 514）（权限：core.suicide，默认允许）
 * simpleCommand 在玩家区域线程执行，kill() 操作玩家自身，Folia 安全
 */
object Suicide {
    @Awake(LifeCycle.ENABLE)
    fun init() {
        simpleCommand(
            "suicide",
            aliases = arrayListOf("514"),
            permission = "core.suicide",
            permissionDefault = PermissionDefault.TRUE
        ) { sender, _ ->
            if (!config.getBoolean("suicide-enable", true)) return@simpleCommand
            if (sender !is Player) return@simpleCommand

            sender.kill()
            config.getString("messages.suicide", "")?.takeIf { it.isNotEmpty() }?.let { sender.msg(it) }
        }
    }
}
