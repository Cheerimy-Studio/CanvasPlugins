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
 * 鑷潃鍛戒护锛?suicide锛堝埆鍚?514锛夛紙鏉冮檺锛歝ore.suicide锛岄粯璁や粎 OP锛?
 * 涓嶅啀娉ㄥ唽 /kill锛堜笌鍘熺増鍐茬獊瀵艰嚧鍒悕 514 澶辨晥锛?
 * 鎵€鏈夌帺瀹讹紙鍚?OP锛夌粺涓€鐢?EntityScheduler 璁剧疆 health = 0锛孎olia 绾跨▼瀹夊叏
 */
object Suicide {
    @Awake(LifeCycle.ENABLE)
    fun init() {
        simpleCommand(
            "suicide",
            aliases = arrayListOf("514"),
            permission = "2b2tcore.suicide",
            permissionDefault = PermissionDefault.OP
        ) { sender, _ ->
            if (!config.getBoolean("suicide-enable", true)) return@simpleCommand
            if (sender !is Player) return@simpleCommand

            sender.scheduler.run(BukkitPlugin.getInstance(), { _ ->
                sender.health = 0.0
            }, null)
            config.getString("messages.suicide", "")?.takeIf { it.isNotEmpty() }?.let { sender.msg(it) }
        }
    }
}

