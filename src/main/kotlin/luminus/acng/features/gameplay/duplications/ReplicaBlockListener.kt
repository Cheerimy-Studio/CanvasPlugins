package luminus.acng.features.gameplay.duplications

import luminus.acng.Main.config
import luminus.acng.msg
import org.bukkit.block.TileState
import org.bukkit.event.block.BlockDropItemEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.persistence.PersistentDataType
import taboolib.common.platform.event.SubscribeEvent

/**
 * 复制品方块标签持久化监听器。
 *
 * 问题：复制品物品放置为方块后，ItemStack ItemMeta 上的复制品 PDC 不会自动
 * 转移到方块上，挖掘时默认掉落物不含复制品标记 → 标签丢失。
 *
 * 方案（不记录位置）：
 * - 普通方块（下界合金块、钻石块等，无 TileState）：方块本身无法存 PDC，
 *   直接从根源禁止放置 —— onPlace 取消放置并提示，杜绝「放置→挖掘洗标记」。
 * - TileState 方块（潜影盒、熔炉、木桶等）：允许放置，放置时将复制品 PDC
 *   写入方块 TileState（随区块数据持久化，重启不丢失）；掉落时对掉落物
 *   重新打上复制品标记（BlockDropItemEvent 覆盖玩家挖掘、爆炸等所有破坏方式）。
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
        } else {
            // 普通方块：无法持久化标记，直接禁止放置
            event.isCancelled = true
            event.player.msg("&c复制品普通方块无法放置！")
        }
    }

    @SubscribeEvent
    fun onDrop(event: BlockDropItemEvent) {
        if (!config.getBoolean("duplication.replica.enable", true)) return
        // 只有 TileState 方块能携带复制品 PDC（普通方块已被禁止放置）
        if (!Replica.isReplicaBlock(event.blockState)) return
        event.items.forEach { entity ->
            entity.itemStack = Replica.mark(entity.itemStack)
        }
    }
}
