package luminus.acng.features.gameplay.duplications

import luminus.acng.Main.config
import org.bukkit.block.TileState
import org.bukkit.event.block.BlockDropItemEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.inventory.FurnaceSmeltEvent
import org.bukkit.persistence.PersistentDataType
import taboolib.common.platform.event.SubscribeEvent

/**
 * 复制品方块持久化 + 产物继承监听器。
 *
 * 1. 方块持久化（BlockPlaceEvent + BlockDropItemEvent）：
 *    - 放置时：复制品物品的 PDC 写入方块 TileState（随区块数据持久化，重启不丢）
 *    - 掉落时：对掉落物重新打上复制品标记（覆盖玩家挖掘、爆炸等所有破坏方式）
 *
 * 2. 产物继承（FurnaceSmeltEvent）：
 *    - 熔炉烧炼：源材料是复制品 → 产物自动继承复制品标记
 *
 * 独立于 mine-and-place 功能，只要 replica.enable=true 即生效。
 */
object ReplicaBlockListener {

    /** 放置复制品时写入方块 TileState PDC */
    @SubscribeEvent
    fun onPlace(event: BlockPlaceEvent) {
        if (!config.getBoolean("duplication.replica.enable", true)) return
        val item = event.itemInHand
        if (!Replica.isReplica(item)) return

        val state = event.blockPlaced.state
        if (state is TileState) {
            state.persistentDataContainer.set(Replica.replicaKey, PersistentDataType.BYTE, 1.toByte())
            state.update()
        }
    }

    /** 方块掉落时恢复复制品标记 */
    @SubscribeEvent
    fun onDrop(event: BlockDropItemEvent) {
        if (!config.getBoolean("duplication.replica.enable", true)) return
        if (!Replica.isReplicaBlock(event.blockState)) return
        event.items.forEach { entity ->
            entity.itemStack = Replica.mark(entity.itemStack)
        }
    }

    /** 熔炉烧炼产物继承：源材料是复制品 → 产物自动打标 */
    @SubscribeEvent
    fun onSmelt(event: FurnaceSmeltEvent) {
        if (!config.getBoolean("duplication.replica.enable", true)) return
        if (Replica.isReplica(event.source)) {
            event.result = Replica.mark(event.result)
        }
    }
}
