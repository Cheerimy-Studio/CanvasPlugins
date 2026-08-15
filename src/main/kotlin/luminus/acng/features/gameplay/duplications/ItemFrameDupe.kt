package luminus.acng.features.gameplay.duplications

import luminus.acng.Main.config
import luminus.acng.features.gameplay.teleport.TeleportStone
import luminus.acng.msg
import org.bukkit.entity.ItemFrame
import taboolib.common.platform.event.SubscribeEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import java.util.concurrent.ThreadLocalRandom

/**
 * 物品展示框复制：旋转展示框时有概率掉落其内物品（权限：2b2tcore.dupe.item-frame）
 *
 * 传送石特殊规则（唯一允许复制传送石的方式）：
 * - 本体：掉落继承 ID 的复制品，本体耐久 -1；耐久耗尽无法复制
 * - 复制品：不可二次复制
 */
object ItemFrameDupe {

    @SubscribeEvent
    fun onRotate(event: PlayerInteractEntityEvent) {
        if (!config.getBoolean("duplication.item-frame.enable")) return
        if (!event.player.hasPermission("2b2tcore.dupe.item-frame")) return

        val entity = event.rightClicked
        if (entity !is ItemFrame) return

        val item = entity.item
        if (item.type.isAir) return

        // 传送石特殊处理
        if (TeleportStone.isStone(item)) {
            if (TeleportStone.isReplica(item)) {
                // 复制品不可二次复制
                Replica.deny(event.player)
                return
            }
            // 本体：检查耐久
            if (TeleportStone.getDurability(item) <= 0) {
                event.player.msg("&c传送石耐久已耗尽，无法复制！")
                return
            }
            if (ThreadLocalRandom.current().nextInt(100) < config.getInt("duplication.item-frame.possibility", 10)) {
                // 掉落复制品（继承 ID）
                entity.world.dropItem(entity.location, TeleportStone.makeReplica(item))
                // 本体耐久 -1，写回展示框
                if (TeleportStone.decreaseDurability(item)) {
                    entity.setItem(item)
                }
            }
            return
        }

        // 普通物品：复制品不可被二次复制
        if (Replica.checkCanDupe(event.player, item)) return

        if (ThreadLocalRandom.current().nextInt(100) < config.getInt("duplication.item-frame.possibility", 10)) {
            // 掉落物默认打上复制品词条；拥有 2b2tcore.dupe.original 权限的玩家得到原版
            entity.world.dropItem(entity.location, Replica.output(event.player, item.clone()))
        }
    }
}
