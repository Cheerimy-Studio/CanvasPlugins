package luminus.acng.features.gameplay.duplications

import luminus.acng.Main.config
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.ChatColor
import org.bukkit.Chunk
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.entity.Chicken
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.world.EntitiesLoadEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataAdapterContext
import org.bukkit.persistence.PersistentDataType
import taboolib.common.platform.event.SubscribeEvent
import taboolib.platform.BukkitPlugin
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

object ChickenDupe {

    /** 鸡的 UUID -> 是否为有效（可刷物）状态 */
    private val chickenActiveMap = ConcurrentHashMap<UUID, Boolean>()

    /**
     * Xin 模式：每只存有物品的鸡在自身区域线程（EntityScheduler）上周期检测，
     * 每隔 interval-minutes 分钟掉落物品。
     * 每个区块最多 maxPerChunk 只有效鸡（超出的显示浓烟，不刷物）。
     */
    object XinMode {
        private val timers = ConcurrentHashMap<UUID, ScheduledTask>()

        private val intervalMinutes get() = config.getInt("duplication.chicken.xin-mode-interval", 15)
        private val maxPerChunk get() = config.getInt("duplication.chicken.xin-mode-max-per-chunk", 3)

        @SubscribeEvent
        fun onEntitiesLoad(event: EntitiesLoadEvent) {
            if (!config.getBoolean("duplication.chicken.xin-mode")) return
            val boundChickens = event.entities.filterIsInstance<Chicken>().filter { it.loadItem() != null }
            boundChickens.forEach { ensureTimer(it) }
            // 按区块分组刷新有效状态，确保同一事件内所有区块的特效正确
            boundChickens.groupBy { it.chunk }.forEach { (chunk, _) ->
                refreshChunkActiveStates(chunk)
            }
        }

        @SubscribeEvent
        fun onClick(event: PlayerInteractEntityEvent) {
            if (!config.getBoolean("duplication.chicken.xin-mode")) return
            if (event.hand != org.bukkit.inventory.EquipmentSlot.HAND) return
            if (!event.player.hasPermission("core.dupe.chicken.xin")) return
            if (event.rightClicked !is Chicken) return

            val player = event.player
            val chicken = event.rightClicked as Chicken
            val item = player.inventory.itemInMainHand

            if (!item.type.name.contains("SHULKER_BOX")) return

            val displayName = item.itemMeta?.displayName ?: item.type.name
            chicken.customName = ChatColor.translateAlternateColorCodes('&', "&6&l") + displayName
            chicken.isCustomNameVisible = true
            chicken.saveItem(item.clone())
            chicken.world.dropItemNaturally(chicken.location, item)
            player.inventory.setItemInMainHand(ItemStack(Material.AIR))
            ensureTimer(chicken)
            refreshChunkActiveStates(chicken.chunk)
        }

        @SubscribeEvent
        fun onDeath(event: EntityDeathEvent) {
            if (event.entity is Chicken) {
                val chicken = event.entity as Chicken
                val chunk = chicken.chunk
                timers.remove(chicken.uniqueId)?.cancel()
                chickenActiveMap.remove(chicken.uniqueId)
                chicken.persistentDataContainer.remove(STORED_ITEM_KEY)
                // 死亡后刷新区块，让被压制的鸡可能变为有效
                refreshChunkActiveStates(chunk)
            }
        }

        /**
         * 绑定的鸡免疫火焰伤害（有效鸡着火，超限鸡浓烟，都不受伤）
         */
        @SubscribeEvent
        fun onChickenDamage(event: EntityDamageEvent) {
            if (event.entity !is Chicken) return
            val chicken = event.entity as Chicken
            if (chicken.loadItem() != null &&
                (event.cause == EntityDamageEvent.DamageCause.FIRE ||
                 event.cause == EntityDamageEvent.DamageCause.FIRE_TICK ||
                 event.cause == EntityDamageEvent.DamageCause.LAVA)) {
                event.isCancelled = true
                // 恢复视觉效果
                if (chickenActiveMap[chicken.uniqueId] == true) {
                    chicken.fireTicks = Int.MAX_VALUE
                } else {
                    chicken.fireTicks = 0
                }
            }
        }

        private fun ensureTimer(chicken: Chicken) {
            if (timers.containsKey(chicken.uniqueId)) return
            chickenActiveMap[chicken.uniqueId] = true  // 默认有效，后续由 refreshChunkActiveStates 控制
            val intervalTicks = intervalMinutes * 60L * 20L  // 分钟转 ticks

            val task = chicken.scheduler.runAtFixedRate(
                BukkitPlugin.getInstance(),
                Consumer { _: ScheduledTask ->
                    // 只有有效鸡才刷物
                    if (chickenActiveMap[chicken.uniqueId] != true) return@Consumer
                    val item = chicken.loadItem() ?: return@Consumer
                    chicken.world.dropItemNaturally(chicken.location, item.clone())
                },
                Runnable {
                    timers.remove(chicken.uniqueId)
                    chickenActiveMap.remove(chicken.uniqueId)
                },
                100L, intervalTicks
            )
            timers[chicken.uniqueId] = task ?: return
        }

        /**
         * 刷新区块内所有绑定鸡的活跃状态
         * 前 maxPerChunk 只为有效（着火），其余为无效（浓烟）
         */
        private fun refreshChunkActiveStates(chunk: Chunk?) {
            if (chunk == null) return
            val boundChickens = chunk.entities.filterIsInstance<Chicken>()
                .filter { it.loadItem() != null }
                .sortedBy { it.uniqueId.toString() }  // 排序保证稳定性

            boundChickens.forEachIndexed { index, chicken ->
                if (index < maxPerChunk) {
                    // 有效：着火
                    if (chickenActiveMap[chicken.uniqueId] != true) {
                        chickenActiveMap[chicken.uniqueId] = true
                        chicken.fireTicks = Int.MAX_VALUE
                        chicken.world.spawnParticle(Particle.FLAME, chicken.location.add(0.0, 0.5, 0.0), 10, 0.3, 0.3, 0.3, 0.01)
                    }
                } else {
                    // 超限：浓烟
                    if (chickenActiveMap[chicken.uniqueId] != false) {
                        chickenActiveMap[chicken.uniqueId] = false
                        chicken.fireTicks = 0
                        chicken.world.spawnParticle(Particle.LARGE_SMOKE, chicken.location.add(0.0, 0.5, 0.0), 10, 0.3, 0.3, 0.3, 0.01)
                    }
                }
            }
        }

        fun reload() {
            timers.values.forEach { it.cancel() }
            timers.clear()
            chickenActiveMap.clear()
        }

        private fun Chicken.saveItem(item: ItemStack) {
            persistentDataContainer.set(STORED_ITEM_KEY, ItemStackPersistentDataType, item)
        }
    }

    /** 共享的 PDC key，XinMode 和 ClickMode 均可访问 */
    private val STORED_ITEM_KEY = NamespacedKey("anarchycore-nextgen", "stored_item")

    /** 检查鸡是否绑定了物品 */
    internal fun Chicken.loadItem(): ItemStack? {
        return persistentDataContainer.get(STORED_ITEM_KEY, ItemStackPersistentDataType)
    }

    object ItemStackPersistentDataType : PersistentDataType<ByteArray, ItemStack> {
        override fun getPrimitiveType(): Class<ByteArray> = ByteArray::class.java
        override fun getComplexType(): Class<ItemStack> = ItemStack::class.java

        override fun toPrimitive(complex: ItemStack, context: PersistentDataAdapterContext): ByteArray {
            ByteArrayOutputStream().use { byteOut ->
                java.io.ObjectOutputStream(byteOut).use { objectOut ->
                    objectOut.writeObject(complex.serialize())
                }
                return byteOut.toByteArray()
            }
        }

        override fun fromPrimitive(primitive: ByteArray, context: PersistentDataAdapterContext): ItemStack {
            ByteArrayInputStream(primitive).use { byteIn ->
                java.io.ObjectInputStream(byteIn).use { objectIn ->
                    @Suppress("UNCHECKED_CAST")
                    val map = objectIn.readObject() as Map<String, Any>
                    return ItemStack.deserialize(map)
                }
            }
        }
    }

    object ClickMode {
        private val cooldown = ConcurrentHashMap<UUID, Long>()

        @SubscribeEvent
        fun onClick(event: PlayerInteractEntityEvent) {
            if (!config.getBoolean("duplication.chicken.click-mode")) return
            if (event.hand != org.bukkit.inventory.EquipmentSlot.HAND) return
            if (!event.player.hasPermission("core.dupe.chicken.click")) return
            if (event.rightClicked !is Chicken) return
            // 如果 Xin 模式已启用且该鸡有存储物品，由 Xin 模式处理
            if (config.getBoolean("duplication.chicken.xin-mode") && event.player.hasPermission("core.dupe.chicken.xin")) {
                val chicken = event.rightClicked as Chicken
                if (chicken.loadItem() != null) return
            }

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
        }
    }
}
