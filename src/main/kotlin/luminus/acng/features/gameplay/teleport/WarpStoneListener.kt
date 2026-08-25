package luminus.acng.features.gameplay.teleport

import luminus.acng.Main.config
import luminus.acng.msg
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent

/** 瞬移石右键使用。本体消耗耐久 + 瞬移；复制品消耗 + 瞬移。 */
object WarpStoneListener : Listener {

    @EventHandler
    fun onUse(event: PlayerInteractEvent) {
        if (!config.getBoolean("warp-stone.enable", true)) return
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        val item = event.item ?: return
        val player = event.player

        when {
            WarpStone.isReplicaItem(item) -> {
                if (WarpStone.isOnCooldown(player)) {
                    player.msg("&c冷却中，请等待 ${WarpStone.getRemainingCooldown(player)} 秒！")
                    event.isCancelled = true
                    event.setUseItemInHand(Event.Result.DENY)
                    return
                }
                WarpStone.executeWarp(player)
                val hand = event.hand
                item.amount -= 1
                if (item.amount <= 0 && hand != null) player.inventory.setItem(hand, null)
                player.msg("&a瞬移成功！")
                event.isCancelled = true
                event.setUseItemInHand(Event.Result.DENY)
            }
            WarpStone.isBody(item) -> {
                if (WarpStone.isOnCooldown(player)) {
                    player.msg("&c冷却中，请等待 ${WarpStone.getRemainingCooldown(player)} 秒！")
                    event.isCancelled = true
                    event.setUseItemInHand(Event.Result.DENY)
                    return
                }
                if (WarpStone.getDurability(item) <= 0) {
                    player.msg("&c瞬移石耐久已耗尽！")
                    event.isCancelled = true
                    event.setUseItemInHand(Event.Result.DENY)
                    return
                }
                WarpStone.executeWarp(player)
                WarpStone.decreaseDurability(item)
                player.msg("&a瞬移成功！剩余耐久: ${WarpStone.getDurability(item)}")
                event.isCancelled = true
                event.setUseItemInHand(Event.Result.DENY)
            }
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        WarpStone.cleanupCooldown(event.player)
    }
}
