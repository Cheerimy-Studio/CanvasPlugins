package luminus.acng.features.gameplay.duplications

import luminus.acng.Main.config
import org.bukkit.block.Block
import org.bukkit.block.BlockState
import org.bukkit.block.ShulkerBox
import org.bukkit.block.TileState
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta
import org.bukkit.persistence.PersistentDataType
import taboolib.common.platform.event.SubscribeEvent

/**
 * 复制品方块标签持久化监听器。
 *
 * 问题：复制品物品放置为方块后，ItemStack ItemMeta 上的复制品 PDC 不会自动
 * 转移到方块的 TileState，挖掘时默认掉落物不含复制品标记 → 标签丢失。
 *
 * 修复：
 * - 放置时：将复制品 PDC 写入方块 TileState（随区块数据持久化，重启不丢失）
 * - 挖掘时：拦截默认掉落（isDropItems=false），手动生成带复制品标记的掉落物
 *   潜影盒保留库存内容，其他方块仅生成对应物品
 *
 * 独立于 mine-and-place 功能，只要 replica.enable=true 即生效。
 */
object ReplicaBlockListener {

    @SubscribeEvent
    fun onPlace(event: BlockPlaceEvent) {
        if (!config.getBoolean("duplication.replica.enable", true)) return
        val item = event.itemInHand
        if (!Replica.isReplica(item)) return

        val block = event.blockPlaced
        val state = block.state
        if (state is TileState) {
            state.persistentDataContainer.set(Replica.replicaKey, PersistentDataType.BYTE, 1.toByte())
            state.update()
        }
    }

    @SubscribeEvent
    fun onMine(event: BlockBreakEvent) {
        if (!config.getBoolean("duplication.replica.enable", true)) return
        val block = event.block
        val state = block.state

        if (!Replica.isReplicaBlock(state)) return

        // 防止默认掉落（默认掉落不含复制品标记）
        event.isDropItems = false

        // 手动创建带复制品标记的掉落物
        val dropItem = createDropFromBlock(block, state)
        if (dropItem != null) {
            Replica.mark(dropItem)
            block.world.dropItemNaturally(block.location, dropItem)
        }
    }

    /**
     * 从方块创建掉落物：
     * - 潜影盒：保留库存内容（BlockState 含 inventory）
     * - 其他 TileState 方块：仅创建对应物品类型
     */
    private fun createDropFromBlock(block: Block, state: BlockState): ItemStack? {
        return try {
            if (state is ShulkerBox) {
                val item = ItemStack(block.type)
                val meta = item.itemMeta as? BlockStateMeta ?: return ItemStack(block.type)
                meta.blockState = state
                item.itemMeta = meta
                item
            } else {
                ItemStack(block.type)
            }
        } catch (_: Throwable) {
            ItemStack(block.type)
        }
    }
}
