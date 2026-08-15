package luminus.acng.features.gameplay.duplications

import luminus.acng.Main.config
import org.bukkit.entity.ItemFrame
import taboolib.common.platform.event.SubscribeEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import java.util.concurrent.ThreadLocalRandom

/**
 * 鐗╁搧灞曠ず妗嗗鍒讹細鏃嬭浆灞曠ず妗嗘椂鏈夋鐜囨帀钀藉叾鍐呯墿鍝侊紙鏉冮檺锛歝ore.dupe.item-frame锛?
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
        // 复制品不可被二次复制
        if (Replica.checkCanDupe(event.player, item)) return

        if (ThreadLocalRandom.current().nextInt(100) < config.getInt("duplication.item-frame.possibility", 10)) {
            // 掉落物默认打上复制品词条；拥有 2b2tcore.dupe.original 权限的玩家得到原版
            entity.world.dropItem(entity.location, Replica.output(event.player, item.clone()))
        }
    }
}

