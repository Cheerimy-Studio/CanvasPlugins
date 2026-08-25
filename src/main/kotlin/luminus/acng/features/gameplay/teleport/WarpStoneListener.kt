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

        // 必须是瞬移石才拦截
        if (!WarpStone.isStone(item)) return

        val player = event.player

        // 无条件取消
        event.isCancelled = true
        event.setUseItemInHand(Event.Result.DENY)

        // 冷却检查（本体和复制品共享冷却）
        if (WarpStone.isOnCooldown(player)) {
            player.msg("&c冷却中，请等待 ${WarpStone.getRemainingCooldown(player)} 秒！")
            return
        }

        when {
            WarpStone.isReplicaItem(item) -> {
                // 复制品：消耗 + 瞬移
                val hand = event.hand
                item.amount -= 1
                if (item.amount <= 0 && hand != null) player.inventory.setItem(hand, null)
                player.msg("&a瞬移成功！")
                WarpStone.executeWarp(player)
            }
            WarpStone.isBody(item) -> {
                // 本体：检查耐久 + 消耗 + 瞬移
                if (WarpStone.getDurability(item) <= 0) {
                    player.msg("&c瞬移石耐久已耗尽！")
                    return
                }
                WarpStone.decreaseDurability(item)
                player.msg("&a瞬移成功！剩余耐久: ${WarpStone.getDurability(item)}")
                WarpStone.executeWarp(player)
            }
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        WarpStone.cleanupCooldown(event.player)
    }
}
