package luminus.acng.features.gameplay.duplications

import luminus.acng.Main.config
import org.bukkit.Bukkit
import org.bukkit.entity.AbstractHorse
import org.bukkit.entity.Boat
import org.bukkit.entity.Entity
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.Inventory
import taboolib.common.platform.event.SubscribeEvent
import taboolib.platform.BukkitPlugin
import java.util.function.Consumer

object DonkeyDupe {
    /**
     * Xin 模式：击杀驮兽时掉落其库存
     */
    object XinMode {
        @SubscribeEvent
        fun onKill(event: EntityDeathEvent) {
            if (!config.getBoolean("duplication.donkey.xin-mode", false)) return
            if (event.entity !is AbstractHorse) return
            val killer = event.entity.killer ?: return
            if (!killer.hasPermission("core.dupe.donkey.xin")) return

            val entity = event.entity as AbstractHorse
            entity.inventory.contents.forEach { item ->
                if (item != null && !item.type.isAir) {
                    entity.world.dropItemNaturally(entity.location, item)
                }
            }
        }
    }

    /**
     * Org 模式：骑乘驮兽下线时复制其库存
     */
    object OrgMode {
        @SubscribeEvent
        fun onPlayerQuit(event: PlayerQuitEvent) {
            if (!config.getBoolean("duplication.donkey.org-mode", false)) return
            val player = event.player
            if (!player.hasPermission("core.dupe.donkey.org")) return
            val vehicle = player.vehicle ?: return

            if (config.getBoolean("duplication.donkey.org-mode-allow-boat-chain", false)) {
                processVehicleChain(vehicle)
            } else {
                processAbstractHorse(vehicle)
            }
        }

        /**
         * 处理船链：船上的乘客可能是驮兽
         */
        private fun processVehicleChain(entity: Entity) {
            if (entity is Boat) {
                entity.passengers.forEach { passenger ->
                    if (passenger is AbstractHorse) {
                        processAbstractHorse(passenger)
                    }
                }
            } else if (entity.vehicle is Boat) {
                (entity.vehicle as Boat).passengers.forEach { passenger ->
                    if (passenger is AbstractHorse) {
                        processAbstractHorse(passenger)
                    }
                }
            } else {
                processAbstractHorse(entity)
            }
        }

        private fun processAbstractHorse(entity: Entity) {
            if (entity !is AbstractHorse) return
            entity.duplicateInventoryForViewers()
        }

        /**
         * 复制驮兽库存给所有正在查看的玩家
         * closeInventory / openInventory 通过 EntityScheduler 调度到玩家线程
         */
        private fun AbstractHorse.duplicateInventoryForViewers() {
            val originalInventory = inventory
            val viewers = originalInventory.viewers.toList()
            if (viewers.isEmpty()) return

            val clonedInventory = createDuplicatedInventory(originalInventory)

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

        private fun createDuplicatedInventory(source: Inventory): Inventory {
            val cloned = Bukkit.createInventory(null, source.type, "Duplicated Inventory")
            source.contents
                .withIndex()
                .filter { (_, item) -> item != null }
                .forEach { (slot, item) ->
                    cloned.setItem(slot, item!!.clone())
                }
            return cloned
        }
    }
}
