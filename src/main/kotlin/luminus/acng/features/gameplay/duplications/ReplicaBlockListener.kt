package luminus.acng.features.gameplay.duplications

import luminus.acng.Main.config
import org.bukkit.block.TileState
import org.bukkit.event.block.BlockDropItemEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.persistence.PersistentDataType
import taboolib.common.platform.event.SubscribeEvent

/**
 * 复制品方块标签持久化监听器。
 *
 * - TileState 方块（潜影盒、熔炉、木桶等）：放置时将复制品 PDC 写入方块
 *   TileState（随区块数据持久化，重启不丢失）；掉落时对掉落物重新打上
 *   复制品标记（BlockDropItemEvent 覆盖玩家挖掘、爆炸等所有破坏方式）。
 * - 普通方块：允许放置；稀有方块（龙蛋、下界合金块等）的复制品已由
 *   Replica 打上 itemtag:placeable flag，由 ItemTag 拦截放置，无需在此处理。
 *
 * 独立于 mine-and-place 功能，只要 replica.enable=true 即生效。
 */
object ReplicaBlockListener {

    @SubscribeEvent
    fun onPlace(event: BlockPlaceEvent) {
        if (!config.getBoolean("duplication.replica.enable", true)) return
        val item = event.itemInHand
        if (!Replica.isReplica(item)) return

        val state = event.blockPlaced.state
        if (state is TileState) {
            // TileState 方块：写入 PDC，随区块数据持久化
            state.persistentDataContainer.set(Replica.replicaKey, PersistentDataType.BYTE, 1.toByte())
            state.update()
        }
    }

    @SubscribeEvent
    fun onDrop(event: BlockDropItemEvent) {
        if (!config.getBoolean("duplication.replica.enable", true)) return
        if (!Replica.isReplicaBlock(event.blockState)) return
        event.items.forEach { entity ->
            entity.itemStack = Replica.mark(entity.itemStack)
        }
    }
}
