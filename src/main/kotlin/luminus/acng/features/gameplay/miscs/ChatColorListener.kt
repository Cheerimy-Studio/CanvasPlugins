package luminus.acng.features.gameplay.miscs

import luminus.acng.Main.config
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.pistonmaster.pistonchat.api.PistonChatReceiveEvent
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

/**
 * 聊天颜色监听器（依赖 PistonChat）。
 * 监听 PistonChatReceiveEvent，根据发送者权限选择名称颜色并覆盖 format 组件。
 * 本类仅在 PistonChat 存在时由 PistonChatHook 注册，避免 NoClassDefFoundError。
 */
object ChatColorListener : Listener {

    @EventHandler
    fun onReceive(event: PistonChatReceiveEvent) {
        if (!config.getBoolean("chat-color.enable", false)) return
        val sender = event.sender
        val color = resolveColor(sender) ?: return
        event.format = Component.text(sender.name).color(color)
            .append(Component.text(" >> "))
            .append(Component.text(event.message))
    }

    private fun resolveColor(player: Player): TextColor? {
        val groups = config.getConfigurationSection("chat-color.groups")
        if (groups != null) {
            for (key in groups.getKeys(false)) {
                val perm = groups.getString("$key.permission") ?: continue
                if (player.hasPermission(perm)) {
                    return parseColor(groups.getString("$key.color") ?: continue) ?: continue
                }
            }
        }
        return parseColor(config.getString("chat-color.default-color", "") ?: "")
    }

    private fun parseColor(str: String): TextColor? {
        val s = str.trim()
        if (s.isEmpty()) return null
        if (s.startsWith("#")) return TextColor.fromHexString(s)
        val code = s.removePrefix("&").removePrefix("§").lowercase()
        return LEGACY[code]
    }

    private val LEGACY: Map<String, NamedTextColor> = mapOf(
        "0" to NamedTextColor.BLACK,
        "1" to NamedTextColor.DARK_BLUE,
        "2" to NamedTextColor.DARK_GREEN,
        "3" to NamedTextColor.DARK_AQUA,
        "4" to NamedTextColor.DARK_RED,
        "5" to NamedTextColor.DARK_PURPLE,
        "6" to NamedTextColor.GOLD,
        "7" to NamedTextColor.GRAY,
        "8" to NamedTextColor.DARK_GRAY,
        "9" to NamedTextColor.BLUE,
        "a" to NamedTextColor.GREEN,
        "b" to NamedTextColor.AQUA,
        "c" to NamedTextColor.RED,
        "d" to NamedTextColor.LIGHT_PURPLE,
        "e" to NamedTextColor.YELLOW,
        "f" to NamedTextColor.WHITE
    )
}
