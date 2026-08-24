package luminus.acng.features.gameplay.teleport

import luminus.acng.Main.config
import luminus.acng.msg
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent

/**
 * 起源石使用监听器。
 *
 * - 本体右键：降低耐久 1，显示剩余耐久；耐久耗尽后提示
 * - 复制品右键：消耗 1 个，传送到主世界 (0, 319, 0)
 */
object OriginStoneListener : Listener {

    @EventHandler
    fun onUse(event: PlayerInteractEvent) {
        if (!config.getBoolean("origin-stone.enable", true)) return
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        val item = event.item ?: return
        val player = event.player

        when {
            // 复制品：消耗，传送到主世界 (0, 319, 0)
            OriginStone.isReplicaItem(item) -> {
                if (OriginStone.consume(player)) {
                    val hand = event.hand
                    item.amount -= 1
                    if (item.amount <= 0 && hand != null) {
                        player.inventory.setItem(hand, null)
                    }
                    player.msg("&a已传送至起源！")
                }
                event.isCancelled = true
            }
            // 本体：降低耐久 1
            OriginStone.isBody(item) -> {
                if (OriginStone.decreaseDurability(item)) {
                    val dur = OriginStone.getDurability(player.inventory.itemInMainHand)
                    if (dur <= 0) {
                        player.msg("&c起源石耐久已耗尽！")
                    }
                }
                event.isCancelled = true
            }
        }
    }
}
