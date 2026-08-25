package luminus.acng.features.gameplay.teleport

import luminus.acng.Main.config
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent

/** 传送石复制品使用监听器。右键传送到持有本体的在线玩家处，成功消耗 1 个。 */
object TeleportStoneListener : Listener {

    @EventHandler
    fun onUse(event: PlayerInteractEvent) {
        if (!config.getBoolean("teleport.enable", true)) return
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        val item = event.item ?: return
        if (!TeleportStone.isReplica(item)) return

        val player = event.player
        if (TeleportStone.consume(player, item)) {
            val hand = event.hand
            item.amount -= 1
            if (item.amount <= 0 && hand != null) {
                player.inventory.setItem(hand, null)
            }
        }
        event.isCancelled = true
        event.setUseItemInHand(Event.Result.DENY)
    }
}
