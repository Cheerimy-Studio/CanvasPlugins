package luminus.acng.features.gameplay.duplications

import luminus.acng.Main.config
import luminus.acng.msg
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Chicken
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.world.EntitiesLoadEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataAdapterContext
import org.bukkit.persistence.PersistentDataType
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.console
import taboolib.platform.BukkitPlugin
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

object ChickenDupe {
    /**
     * Xin 模式（Folia 安全实现）：
     * 原版在异步线程遍历全服实体并跨线程掉落，Folia 下不安全。
     * 现改为：每只存有物品的鸡在自身区域线程（EntityScheduler）上周期检测，
     * 命中配置的分钟数时在区域线程内掉落物品，完全符合 Folia 线程规则。
     */
    object XinMode {
        private val timers = ConcurrentHashMap<UUID, ScheduledTask>()
        private val targetMinutes get() = config.getIntegerList("duplication.chicken.xin-mode-periods")

        @SubscribeEvent
        fun onEntitiesLoad(event: EntitiesLoadEvent) {
            if (!config.getBoolean("duplication.chicken.xin-mode")) return
            event.entities.forEach { e ->
                if (e is Chicken && e.loadItem() != null) {
                    ensureTimer(e)
                }
            }
        }

        @SubscribeEvent
        fun onClick(event: PlayerInteractEntityEvent) {
            if (!config.getBoolean("duplication.chicken.xin-mode")) return
            if (!event.player.hasPermission("core.dupe.chicken.xin")) return
            // 简化冗余条件
            if (event.rightClicked !is Chicken) return

            val player = event.player
            val chicken = event.rightClicked as Chicken
            val item = player.inventory.itemInMainHand

            if (!item.type.name.contains("SHULKER_BOX")) return

            // 修复：displayName 可能为 null，拼接会得到 "&6&lnull"
            val displayName = item.itemMeta?.displayName ?: item.type.name
            chicken.customName = ChatColor.translateAlternateColorCodes('&', "&6&l") + displayName
            chicken.saveItem(item.clone())
            chicken.world.dropItemNaturally(chicken.location, item)
            player.inventory.setItemInMainHand(ItemStack(Material.AIR))
            ensureTimer(chicken)
        }

        @SubscribeEvent
        fun onDeath(event: EntityDeathEvent) {
            if (event.entity is Chicken) {
                val chicken = event.entity as Chicken
                timers.remove(chicken.uniqueId)?.cancel()
                chicken.persistentDataContainer.remove(itemKey)
            }
        }

        private fun ensureTimer(chicken: Chicken) {
            if (timers.containsKey(chicken.uniqueId)) return
            val task = chicken.scheduler.runAtFixedRate(
                BukkitPlugin.getInstance(),
                Consumer { _: ScheduledTask ->
                    val minute = LocalDateTime.now().minute
                    if (targetMinutes.contains(minute)) {
                        val item = chicken.loadItem()
                        if (item != null) {
                            chicken.world.dropItemNaturally(chicken.location, item.clone())
                            console().sendMessage("§a[2B2TCore] 鸡刷复制：§e${chicken.uniqueId} §a已掉落存储物品")
                        }
                    }
                },
                Runnable { timers.remove(chicken.uniqueId) },
                100L, 1200L
            )
            timers[chicken.uniqueId] = task ?: return
        }

        fun reload() {
            timers.values.forEach { it.cancel() }
            timers.clear()
            console().sendMessage("§a[2B2TCore] 鸡刷复制计时器已清空，将在实体重新加载时重建")
        }

        private fun Chicken.saveItem(item: ItemStack) {
            persistentDataContainer.set(itemKey, ItemStackPersistentDataType, item)
        }

        private fun Chicken.loadItem(): ItemStack? {
            return persistentDataContainer.get(itemKey, ItemStackPersistentDataType)
        }

        private val itemKey = NamespacedKey("anarchycore-nextgen", "stored_item")

        object ItemStackPersistentDataType : PersistentDataType<ByteArray, ItemStack> {

            override fun getPrimitiveType(): Class<ByteArray> = ByteArray::class.java
            override fun getComplexType(): Class<ItemStack> = ItemStack::class.java

            override fun toPrimitive(
                complex: ItemStack,
                context: PersistentDataAdapterContext
            ): ByteArray {
                ByteArrayOutputStream().use { byteOut ->
                    java.io.ObjectOutputStream(byteOut).use { objectOut ->
                        objectOut.writeObject(complex.serialize())
                    }
                    return byteOut.toByteArray()
                }
            }

            override fun fromPrimitive(
                primitive: ByteArray,
                context: PersistentDataAdapterContext
            ): ItemStack {
                ByteArrayInputStream(primitive).use { byteIn ->
                    java.io.ObjectInputStream(byteIn).use { objectIn ->
                        @Suppress("UNCHECKED_CAST")
                        val map = objectIn.readObject() as Map<String, Any>
                        return ItemStack.deserialize(map)
                    }
                }
            }
        }
    }

    object ClickMode {
        private val cooldown = ConcurrentHashMap<UUID, Long>()

        @SubscribeEvent
        fun onClick(event: PlayerInteractEntityEvent) {
            if (!config.getBoolean("duplication.chicken.click-mode")) return
            if (!event.player.hasPermission("core.dupe.chicken.click")) return
            if (event.rightClicked !is Chicken) return

            val player = event.player
            val currentTime = System.currentTimeMillis()
            val lastTime = cooldown[player.uniqueId]

            if (lastTime != null && currentTime - lastTime < config.getInt("duplication.chicken.click-mode-cooldown") * 1000L) {
                return
            }

            val chicken = event.rightClicked as Chicken
            val item = player.inventory.itemInMainHand.clone()
            item.amount = 1

            chicken.world.dropItemNaturally(chicken.location, item)
            cooldown[player.uniqueId] = currentTime

            config.getString("messages.success-dupe", "Successfully duped")?.let { player.msg(it) }
        }
    }
}
