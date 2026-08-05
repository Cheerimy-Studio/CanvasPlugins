package luminus.acng.features.gameplay.duplications
import luminus.acng.Main.config
import luminus.acng.msg
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
     * Xin 模式（Folia 安全实现）：
     * 原版在异步线程访问实体库存并跨线程掉落物品，Folia 下不安全。
     * 现改为：EntityDeathEvent 已在实体所在区域线程触发，直接同步掉落物品即可。
     */
    object XinMode {
        @SubscribeEvent
        fun onKill(event: EntityDeathEvent) {
            if (!config.getBoolean("duplication.donkey.xin-mode", false)) return
            if (event.entity !is AbstractHorse) return
            val killer = event.entity.killer ?: return
            if (!killer.hasPermission("core.dupe.donkey.xin")) return

            val entity = event.entity as AbstractHorse
            // 事件本身在实体区域线程触发，库存读取与掉落均在该线程内同步执行，Folia 安全
            entity.inventory.contents.forEach { item ->
                if (item != null && !item.type.isAir) {
                    entity.world.dropItemNaturally(entity.location, item)
                }
            }
            config.getString("messages.success-dupe", "Successfully duped")?.let { killer.msg(it) }
        }
    }

    /**
     * Org 模式（Folia 安全实现）：
     * 原版在异步线程操作库存与打开界面，Folia 下不安全。
     * 现改为：PlayerQuitEvent 已在玩家区域线程触发，库存复制与关闭同步执行；
     * 延迟打开库存使用 HumanEntity 的 EntityScheduler，确保在 viewers 自身区域线程执行。
     */
    object OrgMode {
        @SubscribeEvent
        fun onPlayerQuit(event: PlayerQuitEvent) {
            if (!config.getBoolean("duplication.donkey.org-mode", false)) return
            val player = event.player
            val vehicle = player.vehicle ?: return
            if (!player.hasPermission("core.dupe.donkey.org")) return

            if (config.getBoolean("duplication.donkey.org-mode-allow-boat-chain", false)) {
                processVehicleChain(vehicle)
            } else {
                processAbstractHorse(vehicle)
            }
            config.getString("messages.success-dupe", "Successfully duped")?.let { player.msg(it) }
        }

        private fun processVehicleChain(entity: Entity) {
            when {
                entity is Boat -> entity.passengers.forEach(OrgMode::processAbstractHorse)
                entity.vehicle is Boat -> (entity.vehicle as Boat).passengers.forEach(OrgMode::processAbstractHorse)
                else -> processAbstractHorse(entity)
            }
        }

        private fun processAbstractHorse(entity: Entity) {
            if (entity !is AbstractHorse) return
            entity.duplicateInventoryForViewers()
        }

        private fun AbstractHorse.duplicateInventoryForViewers() {
            val originalInventory = inventory
            val viewers = originalInventory.viewers.toList()

            if (viewers.isEmpty()) return

            val clonedInventory = createDuplicatedInventory(originalInventory)

            viewers.asReversed().forEach { viewer ->
                viewer.closeInventory()
                // 使用 EntityScheduler 在 viewer 自身区域线程延迟打开库存，Folia 安全
                viewer.scheduler.runDelayed(
                    BukkitPlugin.getInstance(),
                    Consumer { _: ScheduledTask -> viewer.openInventory(clonedInventory) },
                    Runnable { },
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
