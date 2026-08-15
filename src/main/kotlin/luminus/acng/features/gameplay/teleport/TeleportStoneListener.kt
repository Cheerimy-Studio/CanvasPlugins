package luminus.acng.features.gameplay.teleport

import luminus.acng.Main.config
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent

/**
 * 传送石复制品使用监听器。
 *
 * 右键「吃掉」复制品：传送到持有本体的在线玩家处，成功则消耗 1 个复制品。
 * 持有本体的玩家不在线时传送失败，不消耗。
 */
object TeleportStoneListener : Listener {

    @EventHandler
    fun onUse(event: PlayerInteractEvent) {
        if (!config.getBoolean("teleport.enable", true)) return
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        val item = event.item ?: return
        if (!TeleportStone.isReplica(item)) return

        val player = event.player
        if (TeleportStone.consume(player, item)) {
            // 传送成功，消耗 1 个复制品
            item.amount -= 1
            val hand = event.hand ?: return
            if (item.amount <= 0) {
                player.inventory.setItem(hand, null)
            }
            event.isCancelled = true
        }
    }
}
