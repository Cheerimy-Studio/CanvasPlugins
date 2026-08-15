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

    /** 楦＄殑 UUID -> 鏄惁涓烘湁鏁堬紙鍙埛鐗╋級鐘舵€?*/
    private val chickenActiveMap = ConcurrentHashMap<UUID, Boolean>()

    /**
     * Xin 妯″紡锛氭瘡鍙瓨鏈夌墿鍝佺殑楦″湪鑷韩鍖哄煙绾跨▼锛圗ntityScheduler锛変笂鍛ㄦ湡妫€娴嬶紝
     * 姣忛殧 interval-minutes 鍒嗛挓鎺夎惤鐗╁搧銆?
     * 姣忎釜鍖哄潡鏈€澶?max-per-chunk 鍙湁鏁堥浮锛岃秴鍑虹殑鏄剧ず娴撶儫鏁堟灉锛堜笉鍒风墿锛夈€?
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
            if (!event.player.hasPermission("2b2tcore.dupe.chicken.xin")) return
            if (event.rightClicked !is Chicken) return

            val player = event.player
            val chicken = event.rightClicked as Chicken
            val item = player.inventory.itemInMainHand

            if (!item.type.name.contains("SHULKER_BOX")) return
            // 复制品（或内含复制品的潜影盒）不可再次喂入鸡刷
            if (Replica.checkCanDupe(player, item)) return

            val displayName = item.itemMeta?.displayName ?: item.type.name
            chicken.customName = ChatColor.translateAlternateColorCodes('&', "&6&l") + displayName
            chicken.isCustomNameVisible = true
            // 存储的物品默认打上复制品词条；拥有 2b2tcore.dupe.original 权限的玩家存原版
            chicken.saveItem(Replica.output(player, item.clone()))
            chicken.world.dropItemNaturally(chicken.location, item)
            player.inventory.setItemInMainHand(ItemStack(Material.AIR))
            ensureTimer(chicken)
            // 鍒锋柊鍖哄潡鐘舵€侀渶瑕佸湪楦℃墍鍦ㄥ尯鍩熺嚎绋嬫墽琛岋紙鐜╁鍖哄煙鍙兘涓庨浮涓嶅悓锛?
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
         * 缁戝畾鐨勯浮鍏嶇柅鐏劙浼ゅ锛堟湁鏁堥浮鐫€鐏紝瓒呴檺楦℃祿鐑燂紝閮戒笉鍙椾激锛?
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

            // 鎺夌墿瀹氭椂鍣細鍦ㄩ浮鐨勫尯鍩熺嚎绋嬩笂鍛ㄦ湡鎬ф帀钀界墿鍝?
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

            // 瑙嗚鏁堟灉瀹氭椂鍣細姣?绉掑埛鏂扮伀鐒?娴撶儫绮掑瓙
            val visualTask = chicken.scheduler.runAtFixedRate(
                BukkitPlugin.getInstance(),
                Consumer { _: ScheduledTask ->
                    val loc = chicken.location
                    if (chickenActiveMap[chicken.uniqueId] == true) {
                        // 鏈夋晥楦★細鐫€鐏?+ 鐏劙绮掑瓙
                        chicken.fireTicks = 200
                        chicken.world.spawnParticle(Particle.FLAME, loc, 5, 0.2, 0.2, 0.2, 0.01)
                    } else {
                        // 瓒呴檺楦★細娴撶儫绮掑瓙锛堜笉鍒风墿锛?
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
         * 鍒锋柊鍖哄潡鍐呮墍鏈夌粦瀹氶浮鐨勬椿璺冪姸鎬?
         * 鍓?maxPerChunk 鍙负鏈夋晥锛堢潃鐏級锛屽叾浣欎负鏃犳晥锛堟祿鐑燂級
         * 浠呰缃姸鎬佹爣璁帮紝瑙嗚鏁堟灉鐢卞畾鏃跺櫒澶勭悊
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
            if (!event.player.hasPermission("2b2tcore.dupe.chicken.click")) return
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
            // 复制品不可被二次复制
            if (Replica.checkCanDupe(player, item)) return
            item.amount = 1
            // 掉落物默认打上复制品词条；拥有 2b2tcore.dupe.original 权限的玩家得到原版
            chicken.world.dropItemNaturally(chicken.location, Replica.output(player, item))
            cooldown[player.uniqueId] = currentTime
        }
    }
}

