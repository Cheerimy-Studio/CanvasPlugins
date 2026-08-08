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

        if (ThreadLocalRandom.current().nextInt(100) >= config.getInt("duplication.item-frame.possibility", 10)) return

        entity.world.dropItem(entity.location, item.clone())
    }
}

