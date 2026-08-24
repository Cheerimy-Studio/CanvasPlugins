package luminus.acng.features.gameplay.teleport

import luminus.acng.features.gameplay.duplications.Replica
import luminus.acng.msg
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.inventory.CraftingInventory

/**
 * 传送石 / 起源石合成验证。
 *
 * - 传送碎片：所有材料（龙蛋 + 8 种块）不可为复制品
 * - 传送核心：9 个传送碎片（带复制品词条，但允许作为材料，不拦截）
 * - 传送石：传送核心允许，其余 8 种材料不可为复制品；
 *   合成成功后替换为带新随机唯一 ID 的传送石本体
 * - 起源石：传送核心允许，其余 8 种土方块不可为复制品；
 *   合成成功后替换为带新随机唯一 ID 的起源石本体
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
                // 传送碎片：所有材料不可为复制品
                if (matrix.any { it != null && !it.type.isAir && Replica.containsReplica(it) }) {
                    event.isCancelled = true
                    player.msg("&c合成传送碎片的材料不能是复制品！")
                }
            }
            TeleportItems.isCore(result) -> {
                // 传送核心：9 个传送碎片（带复制品词条但允许作为合成材料）
            }
            TeleportStone.isStone(result) -> {
                // 传送石：传送核心允许，其余不可为复制品；合成后替换新 ID
                if (matrix.any {
                        it != null && !it.type.isAir && !TeleportItems.isCore(it) && Replica.containsReplica(it)
                    }) {
                    event.isCancelled = true
                    player.msg("&c合成传送石的材料不能是复制品！")
                } else {
                    inventory.result = TeleportStone.createBody()
                }
            }
            OriginStone.isStone(result) -> {
                // 起源石：传送核心允许，其余 8 种土方块不可为复制品；合成后替换新 ID
                if (matrix.any {
                        it != null && !it.type.isAir && !TeleportItems.isCore(it) && Replica.containsReplica(it)
                    }) {
                    event.isCancelled = true
                    player.msg("&c合成起源石的材料不能是复制品！")
                } else {
                    inventory.result = OriginStone.createBody()
                }
            }
        }
    }
}
