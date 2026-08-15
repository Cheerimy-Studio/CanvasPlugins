package luminus.acng.features.gameplay.teleport

import luminus.acng.features.gameplay.duplications.Replica
import luminus.acng.msg
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.inventory.CraftingInventory

/**
 * 传送石合成验证。
 *
 * - 传送碎片：所有材料（龙蛋 + 8 种块）不可为复制品
 * - 传送核心：9 个传送碎片（带复制品词条，但允许作为材料，不拦截）
 * - 传送石：传送核心允许，其余 8 种材料不可为复制品
 */
object TeleportListener : Listener {

    @EventHandler
    fun onCraft(event: CraftItemEvent) {
        val inventory = event.inventory as? CraftingInventory ?: return
        val result = inventory.result ?: return
        val player = event.whoClicked as? Player ?: return
        val matrix = inventory.matrix

        when {
            TeleportItems.isShard(result) -> {
                if (matrix.any { it != null && !it.type.isAir && Replica.containsReplica(it) }) {
                    event.isCancelled = true
                    player.msg("&c合成传送碎片的材料不能是复制品！")
                }
            }
            TeleportItems.isCore(result) -> {
                // 9 个传送碎片：带复制品词条但允许作为合成材料
            }
            TeleportItems.isStone(result) -> {
                if (matrix.any {
                        it != null && !it.type.isAir && !TeleportItems.isCore(it) && Replica.containsReplica(it)
                    }) {
                    event.isCancelled = true
                    player.msg("&c合成传送石的材料不能是复制品！")
                }
            }
        }
    }
}
