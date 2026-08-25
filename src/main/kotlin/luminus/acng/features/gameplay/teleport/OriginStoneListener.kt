package luminus.acng.features.gameplay.teleport

import luminus.acng.Main.config
import luminus.acng.msg
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent

/** 起源石右键使用。本体消耗耐久；复制品消耗 + 传送至起源。 */
object OriginStoneListener : Listener {

    @EventHandler
    fun onUse(event: PlayerInteractEvent) {
        if (!config.getBoolean("origin-stone.enable", true)) return
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        val item = event.item ?: return
        val player = event.player

        when {
            OriginStone.isReplicaItem(item) -> {
                val hand = event.hand
                item.amount -= 1
                if (item.amount <= 0 && hand != null) player.inventory.setItem(hand, null)
                val overworld = org.bukkit.Bukkit.getWorlds().first()
                player.teleportAsync(overworld.spawnLocation).thenAccept { success ->
                    if (success) player.msg("&a已传送至起源！")
                }
                event.isCancelled = true
                event.setUseItemInHand(Event.Result.DENY)
            }
            OriginStone.isBody(item) -> {
                if (OriginStone.decreaseDurability(item)) {
                    val dur = OriginStone.getDurability(item)
                    if (dur > 0) {
                        player.msg("&d起源石耐久: &e$dur/${config.getInt("origin-stone.durability", 100)}")
                    } else {
                        player.msg("&c起源石耐久已耗尽！")
                    }
                }
                event.isCancelled = true
                event.setUseItemInHand(Event.Result.DENY)
            }
        }
    }
}
