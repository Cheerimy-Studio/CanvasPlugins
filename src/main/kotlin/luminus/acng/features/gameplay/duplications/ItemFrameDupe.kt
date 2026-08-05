package luminus.acng.features.gameplay.duplications

import luminus.acng.Main.config
import luminus.acng.msg
import org.bukkit.entity.ItemFrame
import taboolib.common.platform.event.SubscribeEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import java.util.concurrent.ThreadLocalRandom

/**
 * 物品展示框复制：旋转展示框时有概率掉落其内物品（权限：core.dupe.item-frame）
 * PlayerInteractEntityEvent 已在展示框所在区域线程触发，掉落同步执行，Folia 安全
 */
object ItemFrameDupe {

    @SubscribeEvent
    fun onRotate(event: PlayerInteractEntityEvent) {
        if (!config.getBoolean("duplication.item-frame.enable")) return
        if (!event.player.hasPermission("core.dupe.item-frame")) return

        // 兼容 ITEM_FRAME 与 GLOW_ITEM_FRAME
        val entity = event.rightClicked
        if (entity !is ItemFrame) return

        val item = entity.item
        // 修复：原版未检查展示框内是否为空，会掉落空气（新版 API 中 ItemStack 不可空）
        if (item.type.isAir) return

        // 修复：原版消息在概率判断外发送，未触发也提示成功
        if (ThreadLocalRandom.current().nextInt(100) >= config.getInt("duplication.item-frame.possibility", 10)) return

        entity.world.dropItem(entity.location, item.clone())
        config.getString("messages.success-dupe", "Successfully duped")?.let { event.player.msg(it) }
    }
}
