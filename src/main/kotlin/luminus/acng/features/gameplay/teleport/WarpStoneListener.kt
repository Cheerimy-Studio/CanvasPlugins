package luminus.acng.features.gameplay.teleport

import luminus.acng.Main.config
import luminus.acng.msg
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent

/**
 * 瞬移石使用监听器。
 *
 * - 本体右键：瞬移 + 消耗 1 耐久；冷却 10 秒
 * - 复制品右键：瞬移 + 消耗（单次）
 */
object WarpStoneListener : Listener {

    @EventHandler
    fun onUse(event: PlayerInteractEvent) {
        if (!config.getBoolean("warp-stone.enable", true)) return
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        val item = event.item ?: return
        val player = event.player

        when {
            // 复制品：消耗，瞬移
            WarpStone.isReplicaItem(item) -> {
                if (WarpStone.isOnCooldown(player)) {
                    player.msg("&c冷却中，请等待 ${WarpStone.getRemainingCooldown(player)} 秒！")
                    event.isCancelled = true
                    return
                }
                WarpStone.executeWarp(player)
                val hand = event.hand
                item.amount -= 1
                if (item.amount <= 0 && hand != null) {
                    player.inventory.setItem(hand, null)
                }
                player.msg("&a瞬移成功！")
                event.isCancelled = true
            }
            // 本体：瞬移，消耗耐久
            WarpStone.isBody(item) -> {
                if (WarpStone.isOnCooldown(player)) {
                    player.msg("&c冷却中，请等待 ${WarpStone.getRemainingCooldown(player)} 秒！")
                    event.isCancelled = true
                    return
                }
                if (WarpStone.getDurability(item) <= 0) {
                    player.msg("&c瞬移石耐久已耗尽！")
                    event.isCancelled = true
                    return
                }
                WarpStone.executeWarp(player)
                WarpStone.decreaseDurability(item)
                player.msg("&a瞬移成功！剩余耐久: ${WarpStone.getDurability(item)}")
                event.isCancelled = true
            }
        }
    }

    /** 玩家退出时清理冷却记录 */
    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        WarpStone.cleanupCooldown(event.player)
    }
}
