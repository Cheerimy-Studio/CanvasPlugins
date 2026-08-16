package luminus.acng.features.gameplay.duplications

import luminus.acng.Main.config
import org.bukkit.block.ShulkerBox
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta
import taboolib.common.platform.event.SubscribeEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 破坏放置复制：累计破坏潜影盒到指定次数时复制一份（权限：2b2tcore.dupe.mine-and-place）
 * 复制品（或内含复制品的潜影盒）不可被二次复制；输出默认带复制品词条，
 * 拥有 2b2tcore.dupe.original 权限的玩家得到原版。
 */
object MineAndPlaceDupe {
    private val map: ConcurrentHashMap<UUID, Int> = ConcurrentHashMap()

    /** 已放置的复制品潜影盒位置（Folia 跨区域安全：ConcurrentHashMap） */
    private val replicaBlocks: ConcurrentHashMap<BlockKey, Boolean> = ConcurrentHashMap()

    private data class BlockKey(val world: String, val x: Int, val y: Int, val z: Int)

    /** 记录放置的复制品潜影盒位置，用于破坏时拦截二次复制 */
    @SubscribeEvent
    fun onPlace(event: BlockPlaceEvent) {
        if (!config.getBoolean("duplication.mine-and-place.enable")) return
        if (!event.blockPlaced.type.name.lowercase().contains("shulker")) return
        if (Replica.containsReplica(event.itemInHand)) {
            val b = event.blockPlaced
            replicaBlocks[BlockKey(b.world.name, b.x, b.y, b.z)] = true
        }
    }

    @SubscribeEvent
    fun onMine(event: BlockBreakEvent) {
        if (!config.getBoolean("duplication.mine-and-place.enable")) return
        if (!event.player.hasPermission("2b2tcore.dupe.mine-and-place")) return
        if (!event.block.type.toString().lowercase().contains("shulker")) return

        val block = event.block
        val key = BlockKey(block.world.name, block.x, block.y, block.z)
        // 复制品（或内含复制品的潜影盒）不可被二次复制，不计数
        if (replicaBlocks.remove(key) == true || Replica.blockContainsReplica(block.state)) {
            Replica.deny(event.player)
            return
        }

        val uuid = event.player.uniqueId
        val current = map.compute(uuid) { _, v -> (v ?: 0) + 1 } ?: return

        if (current >= config.getInt("duplication.mine-and-place.amount", 10)) {
            val state = block.state
            if (state !is ShulkerBox) {
                map.remove(uuid)
                return
            }
            val shulkerBox = state
            val shulkerItem = ItemStack(block.type)
            val blockStateMeta = shulkerItem.itemMeta as BlockStateMeta
            blockStateMeta.blockState = shulkerBox
            shulkerItem.setItemMeta(blockStateMeta)
            // 掉落物默认打上复制品词条；拥有 2b2tcore.dupe.original 权限的玩家得到原版
            block.world.dropItem(block.location, Replica.output(event.player, shulkerItem))
            map.remove(uuid)
        }
    }

    fun clear() {
        map.clear()
        replicaBlocks.clear()
    }
}
