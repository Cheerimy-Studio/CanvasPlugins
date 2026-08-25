package luminus.acng.features.gameplay.teleport

import luminus.acng.Main.config
import luminus.acng.msg
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent

/** 起源石右键使用。本体消耗耐久；复制品消耗 + 传送至 (0,319,0)。 */
object OriginStoneListener : Listener {

    @EventHandler
    fun onUse(event: PlayerInteractEvent) {
        if (!config.getBoolean("origin-stone.enable", true)) return
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        val item = event.item ?: return

        // 必须是起源石才拦截，否则不处理
        if (!OriginStone.isStone(item)) return

        val player = event.player

        // 无条件取消：阻止原版花放置 + 手持使用
        event.isCancelled = true
        event.setUseItemInHand(Event.Result.DENY)

        when {
            OriginStone.isReplicaItem(item) -> {
                // 复制品：消耗 + 传送至 (0, 319, 0)
                val hand = event.hand
                item.amount -= 1
                if (item.amount <= 0 && hand != null) player.inventory.setItem(hand, null)
                val overworld = org.bukkit.Bukkit.getWorlds().first()
                val target = overworld.spawnLocation.clone().apply {
                    x = 0.5; y = 319.0; z = 0.5
                }
                player.teleportAsync(target).thenAccept { success ->
                    if (success) player.msg("&a已传送至起源 (0, 319, 0)！")
                    else player.msg("&c传送失败，请重试！")
                }
            }
            OriginStone.isBody(item) -> {
                // 本体：消耗耐久 + 显示信息（耐久耗尽也提示）
                if (OriginStone.decreaseDurability(item)) {
                    val dur = OriginStone.getDurability(item)
                    if (dur > 0) {
                        player.msg("&d起源石耐久: &e$dur/${config.getInt("origin-stone.durability", 100)}")
                    } else {
                        player.msg("&c起源石耐久已耗尽！")
                    }
                } else {
                    // decreaseDurability 返回 false = 耐久已经为 0
                    player.msg("&c起源石耐久已耗尽！")
                }
            }
        }
    }
}
