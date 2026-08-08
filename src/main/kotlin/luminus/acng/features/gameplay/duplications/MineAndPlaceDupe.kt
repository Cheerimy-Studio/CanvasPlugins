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
 * 鐮村潖鏀剧疆澶嶅埗锛氱疮璁＄牬鍧忔綔褰辩洅鍒版寚瀹氭鏁版椂澶嶅埗涓€浠斤紙鏉冮檺锛歝ore.dupe.mine-and-place锛?
 */
object MineAndPlaceDupe {
    private val map: ConcurrentHashMap<UUID, Int> = ConcurrentHashMap()

    @SubscribeEvent
    fun onMine(event: BlockBreakEvent) {
        if (!config.getBoolean("duplication.mine-and-place.enable")) return
        if (!event.player.hasPermission("2b2tcore.dupe.mine-and-place")) return
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

