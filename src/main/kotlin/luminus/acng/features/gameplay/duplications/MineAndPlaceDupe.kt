package luminus.acng.features.gameplay.duplications

import luminus.acng.Main.config
import luminus.acng.msg
import org.bukkit.block.ShulkerBox
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta
import taboolib.common.platform.event.SubscribeEvent
import java.util.concurrent.ConcurrentHashMap

/**
 * 破坏放置复制：累计破坏潜影盒到指定次数时复制一份（权限：core.dupe.mine-and-place）
 * BlockBreakEvent 已在方块所在区域线程触发，掉落物品同步执行，Folia 安全
 */
object MineAndPlaceDupe {
    // 玩家名 -> 已破坏次数。使用 ConcurrentHashMap 保证并发安全
    val map: ConcurrentHashMap<String, Int> = ConcurrentHashMap()

    @SubscribeEvent
    fun onMine(event: BlockBreakEvent) {
        if (!config.getBoolean("duplication.mine-and-place.enable")) return
        if (!event.player.hasPermission("core.dupe.mine-and-place")) return
        if (!event.block.type.toString().lowercase().contains("shulker")) return

        val name = event.player.name
        // compute 是 ConcurrentHashMap 的原子操作，避免并发计数错乱
        val current = map.compute(name) { _, v -> (v ?: 0) + 1 } ?: return

        if (current >= config.getInt("duplication.mine-and-place.amount", 10)) {
            val shulkerBox = event.block.state as ShulkerBox
            val shulkerItem = ItemStack(event.block.type)
            val blockStateMeta = shulkerItem.itemMeta as BlockStateMeta
            blockStateMeta.blockState = shulkerBox
            shulkerItem.setItemMeta(blockStateMeta)
            shulkerBox.world.dropItem(shulkerBox.location, shulkerItem)
            map.remove(name)
            config.getString("messages.success-dupe", "Successfully duped")?.let { event.player.msg(it) }
        }
    }

    /** 清空计数缓存（由 /core clearcache 调用） */
    fun clear() {
        map.clear()
    }
}
