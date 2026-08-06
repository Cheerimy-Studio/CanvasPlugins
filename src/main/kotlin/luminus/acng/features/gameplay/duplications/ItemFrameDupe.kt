package luminus.acng.features.gameplay.duplications

import luminus.acng.Main.config
import org.bukkit.entity.ItemFrame
import taboolib.common.platform.event.SubscribeEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import java.util.concurrent.ThreadLocalRandom

/**
 * 物品展示框复制：旋转展示框时有概率掉落其内物品（权限：core.dupe.item-frame）
 */
object ItemFrameDupe {

    @SubscribeEvent
    fun onRotate(event: PlayerInteractEntityEvent) {
        if (!config.getBoolean("duplication.item-frame.enable")) return
        if (!event.player.hasPermission("core.dupe.item-frame")) return

        val entity = event.rightClicked
        if (entity !is ItemFrame) return

        val item = entity.item
        if (item.type.isAir) return

        if (ThreadLocalRandom.current().nextInt(100) >= config.getInt("duplication.item-frame.possibility", 10)) return

        entity.world.dropItem(entity.location, item.clone())
    }
}
