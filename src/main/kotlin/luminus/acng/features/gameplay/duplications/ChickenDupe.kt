package luminus.acng.features.gameplay.duplications

import luminus.acng.Main.config
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Bukkit
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
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.persistence.PersistentDataType
import taboolib.common.platform.event.SubscribeEvent
import taboolib.platform.BukkitPlugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

object ChickenDupe {

    /** 鸡的 UUID -> 是否为有效（可刷物）状态 */
    private val chickenActiveMap = ConcurrentHashMap<UUID, Boolean>()

    /**
     * Xin 模式：每只存有物品的鸡在自身区域线程（EntityScheduler）上周期检测，
     * 每隔 interval-minutes 分钟掉落物品。
     * 每个区块最多 max-per-chunk 只有效鸡，超出的显示浓烟效果（不刷物）。
     */
    object XinMode {
        private val timers = ConcurrentHashMap<UUID, ScheduledTask>()
        private val visualTimers = ConcurrentHashMap<UUID, ScheduledTask>()

        private val intervalMinutes get() = config.getInt("duplication.chicken.xin-mode-interval", 15)
        private val maxPerChunk get() = config.getInt("duplication.chicken.xin-mode-max-per-chunk", 3)

        private val itemKey = NamespacedKey("anarchycore-nextgen", "stored_item")

        @SubscribeEvent
        fun onEntitiesLoad(event: EntitiesLoadEvent) {
            if (!config.getBoolean("duplication.chicken.xin-mode")) return
            val chickens = event.entities.filterIsInstance<Chicken>()
            chickens.forEach { chicken ->
                if (chicken.loadItem() != null) {
                    ensureTimer(chicken)
                }
            }
            if (chickens.isNotEmpty()) {
                refreshChunkActiveStates(chickens.first().chunk)
            }
        }

        @SubscribeEvent
        fun onClick(event: PlayerInteractEntityEvent) {
            if (!config.getBoolean("duplication.chicken.xin-mode")) return
            if (event.hand != EquipmentSlot.HAND) return
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
            // 刷新区块状态需要在鸡所在区域线程执行（玩家区域可能与鸡不同）
            Bukkit.getRegionScheduler().execute(BukkitPlugin.getInstance(), chicken.location) {
                refreshChunkActiveStates(chicken.chunk)
            }
        }

        @SubscribeEvent
        fun onDeath(event: EntityDeathEvent) {
            if (event.entity !is Chicken) return
            val chicken = event.entity as Chicken
            if (!timers.containsKey(chicken.uniqueId) && chicken.loadItem() == null) return

            val chunk = chicken.chunk
            timers.remove(chicken.uniqueId)?.cancel()
            visualTimers.remove(chicken.uniqueId)?.cancel()
            chickenActiveMap.remove(chicken.uniqueId)
            chicken.persistentDataContainer.remove(itemKey)
            refreshChunkActiveStates(chunk)
        }

        /**
         * 绑定的鸡免疫火焰伤害（有效鸡着火，超限鸡浓烟，都不受伤）
         */
        @SubscribeEvent
        fun onChickenDamage(event: EntityDamageEvent) {
            if (event.entity !is Chicken) return
            val chicken = event.entity as Chicken
            if (chicken.loadItem() == null) return
            if (event.cause == EntityDamageEvent.DamageCause.FIRE ||
                event.cause == EntityDamageEvent.DamageCause.FIRE_TICK ||
                event.cause == EntityDamageEvent.DamageCause.LAVA) {
                event.isCancelled = true
            }
        }

        private fun ensureTimer(chicken: Chicken) {
            if (timers.containsKey(chicken.uniqueId)) return
            chickenActiveMap[chicken.uniqueId] = true

            val intervalTicks = intervalMinutes * 60L * 20L

            // 掉物定时器：在鸡的区域线程上周期性掉落物品
            val dropTask = chicken.scheduler.runAtFixedRate(
                BukkitPlugin.getInstance(),
                Consumer { _: ScheduledTask ->
                    if (chickenActiveMap[chicken.uniqueId] != true) return@Consumer
                    val item = chicken.loadItem() ?: return@Consumer
                    chicken.world.dropItemNaturally(chicken.location, item.clone())
                },
                Runnable {
                    timers.remove(chicken.uniqueId)
                    chickenActiveMap.remove(chicken.uniqueId)
                    visualTimers.remove(chicken.uniqueId)?.cancel()
                },
                100L, intervalTicks
            ) ?: return
            timers[chicken.uniqueId] = dropTask

            // 视觉效果定时器：每2秒刷新火焰/浓烟粒子
            val visualTask = chicken.scheduler.runAtFixedRate(
                BukkitPlugin.getInstance(),
                Consumer { _: ScheduledTask ->
                    val loc = chicken.location
                    if (chickenActiveMap[chicken.uniqueId] == true) {
                        // 有效鸡：着火 + 火焰粒子
                        chicken.fireTicks = 200
                        chicken.world.spawnParticle(Particle.FLAME, loc, 5, 0.2, 0.2, 0.2, 0.01)
                    } else {
                        // 超限鸡：浓烟粒子（不刷物）
                        chicken.world.spawnParticle(Particle.LARGE_SMOKE, loc, 3, 0.2, 0.2, 0.2, 0.01)
                    }
                },
                Runnable {},
                20L, 40L
            )
            if (visualTask != null) {
                visualTimers[chicken.uniqueId] = visualTask
            }
        }

        /**
         * 刷新区块内所有绑定鸡的活跃状态
         * 前 maxPerChunk 只为有效（着火），其余为无效（浓烟）
         * 仅设置状态标记，视觉效果由定时器处理
         */
        private fun refreshChunkActiveStates(chunk: Chunk?) {
            if (chunk == null) return
            val boundChickens = chunk.entities.filterIsInstance<Chicken>()
                .filter { it.loadItem() != null }
                .sortedBy { it.uniqueId.toString() }

            boundChickens.forEachIndexed { index, chicken ->
                chickenActiveMap[chicken.uniqueId] = index < maxPerChunk
            }
        }

        fun reload() {
            timers.values.forEach { it.cancel() }
            timers.clear()
            visualTimers.values.forEach { it.cancel() }
            visualTimers.clear()
            chickenActiveMap.clear()
        }

        private fun Chicken.saveItem(item: ItemStack) {
            persistentDataContainer.set(itemKey, PersistentDataType.BYTE_ARRAY, item.serializeAsBytes())
        }

        private fun Chicken.loadItem(): ItemStack? {
            val bytes = persistentDataContainer.get(itemKey, PersistentDataType.BYTE_ARRAY) ?: return null
            return try {
                ItemStack.deserializeBytes(bytes)
            } catch (_: Throwable) {
                null
            }
        }
    }

    object ClickMode {
        private val cooldown = ConcurrentHashMap<UUID, Long>()

        @SubscribeEvent
        fun onClick(event: PlayerInteractEntityEvent) {
            if (!config.getBoolean("duplication.chicken.click-mode")) return
            if (event.hand != EquipmentSlot.HAND) return
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
            if (item.type.isAir) return
            item.amount = 1
            chicken.world.dropItemNaturally(chicken.location, item)
            cooldown[player.uniqueId] = currentTime
        }
    }
}
