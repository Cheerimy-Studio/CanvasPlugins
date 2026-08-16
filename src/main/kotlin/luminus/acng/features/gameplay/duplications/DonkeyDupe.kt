package luminus.acng.features.gameplay.duplications

import luminus.acng.Main.config
import luminus.acng.features.gameplay.teleport.TeleportStone
import org.bukkit.Bukkit
import org.bukkit.entity.AbstractHorse
import org.bukkit.entity.Boat
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.Inventory
import taboolib.common.platform.event.SubscribeEvent
import taboolib.platform.BukkitPlugin
import java.util.function.Consumer

object DonkeyDupe {
    /**
     * Xin 模式：击杀驴兽时掉落其库存
     */
    object XinMode {
        @SubscribeEvent
        fun onKill(event: EntityDeathEvent) {
            if (!config.getBoolean("duplication.donkey.xin-mode", false)) return
            if (event.entity !is AbstractHorse) return
            val killer = event.entity.killer ?: return
            if (!killer.hasPermission("2b2tcore.dupe.donkey.xin")) return

            val entity = event.entity as AbstractHorse
            entity.inventory.contents.forEach { item ->
                if (item != null && !item.type.isAir) {
                    // 复制品（或内含复制品的容器）不可被二次复制；传送石只能展示框复制
                    if (Replica.containsReplica(item) || TeleportStone.isStone(item)) return@forEach
                    // 掉落物默认打上复制品词条；拥有 2b2tcore.dupe.original 权限的击杀者得到原版
                    entity.world.dropItemNaturally(entity.location, Replica.output(killer, item.clone()))
                }
            }
        }
    }

    /**
     * Org 模式：骑乘驴兽下线时复制其库存
     */
    object OrgMode {
        @SubscribeEvent
        fun onPlayerQuit(event: PlayerQuitEvent) {
            if (!config.getBoolean("duplication.donkey.org-mode", false)) return
            val player = event.player
            if (!player.hasPermission("2b2tcore.dupe.donkey.org")) return
            val vehicle = player.vehicle ?: return

            if (config.getBoolean("duplication.donkey.org-mode-allow-boat-chain", false)) {
                processVehicleChain(vehicle, player)
            } else {
                processAbstractHorse(vehicle, player)
            }
        }

        /**
         * 处理船链：船上的乘客可能是驴兽
         */
        private fun processVehicleChain(entity: Entity, player: Player) {
            if (entity is Boat) {
                entity.passengers.forEach { passenger ->
                    if (passenger is AbstractHorse) {
                        processAbstractHorse(passenger, player)
                    }
                }
            } else if (entity.vehicle is Boat) {
                (entity.vehicle as Boat).passengers.forEach { passenger ->
                    if (passenger is AbstractHorse) {
                        processAbstractHorse(passenger, player)
                    }
                }
            } else {
                processAbstractHorse(entity, player)
            }
        }

        private fun processAbstractHorse(entity: Entity, player: Player) {
            val horse = entity as? AbstractHorse ?: return
            horse.duplicateInventoryForViewers(player)
        }

        /**
         * 复制驴兽库存给所有正在查看的玩家
         * closeInventory / openInventory 通过 EntityScheduler 调度到玩家线程
         */
        private fun AbstractHorse.duplicateInventoryForViewers(player: Player) {
            val originalInventory = inventory
            val viewers = originalInventory.viewers.toList()
            if (viewers.isEmpty()) return

            val clonedInventory = createDuplicatedInventory(originalInventory, player)

            viewers.forEach { viewer ->
                viewer.scheduler.run(
                    BukkitPlugin.getInstance(),
                    Consumer { _: ScheduledTask ->
                        viewer.closeInventory()
                    },
                    null
                )
                viewer.scheduler.runDelayed(
                    BukkitPlugin.getInstance(),
                    Consumer { _: ScheduledTask ->
                        viewer.openInventory(clonedInventory)
                    },
                    Runnable {},
                    2L
                )
            }
        }

        private fun createDuplicatedInventory(source: Inventory, duper: Player): Inventory {
            val cloned = Bukkit.createInventory(null, source.type, "Duplicated Inventory")
            source.contents
                .withIndex()
                .filter { (_, item) -> item != null && !Replica.containsReplica(item) && !TeleportStone.isStone(item) }
                .forEach { (slot, item) ->
                    // 复制品（或内含复制品的容器）不复制；传送石只能展示框复制；输出默认带复制品词条，原版权限玩家得原版
                    cloned.setItem(slot, Replica.output(duper, item!!.clone()))
                }
            return cloned
        }
    }
}
