package luminus.acng.features.gameplay.duplications

import luminus.acng.Main.config
import org.bukkit.block.ShulkerBox
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta
import taboolib.common.platform.event.SubscribeEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 破坏放置复制：累计破坏潜影盒到指定次数时复制一份（权限：core.dupe.mine-and-place）
 */
object MineAndPlaceDupe {
    private val map: ConcurrentHashMap<UUID, Int> = ConcurrentHashMap()

    @SubscribeEvent
    fun onMine(event: BlockBreakEvent) {
        if (!config.getBoolean("duplication.mine-and-place.enable")) return
        if (!event.player.hasPermission("core.dupe.mine-and-place")) return
        if (!event.block.type.toString().lowercase().contains("shulker")) return

        val uuid = event.player.uniqueId
        val current = map.compute(uuid) { _, v -> (v ?: 0) + 1 } ?: return

        if (current >= config.getInt("duplication.mine-and-place.amount", 10)) {
            val shulkerBox = event.block.state as ShulkerBox
            val shulkerItem = ItemStack(event.block.type)
            val blockStateMeta = shulkerItem.itemMeta as BlockStateMeta
            blockStateMeta.blockState = shulkerBox
            shulkerItem.setItemMeta(blockStateMeta)
            shulkerBox.world.dropItem(shulkerBox.location, shulkerItem)
            map.remove(uuid)
        }
    }

    fun clear() {
        map.clear()
    }
}
