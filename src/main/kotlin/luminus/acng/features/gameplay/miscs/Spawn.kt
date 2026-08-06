package luminus.acng.features.gameplay.miscs

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import taboolib.common.platform.event.SubscribeEvent
import taboolib.platform.BukkitPlugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom

/**
 * 出生点功能：
 * - 首次加入/重生时在出生点半径 500 格内随机传送到安全位置
 * - 重生后 10 秒无敌保护期
 *
 * Folia 线程安全说明：
 * - 首次加入在出生点所在区域线程内执行寻路与异步传送
 * - 重生事件本身位于重生点区域线程，可直接同步设置重生位置
 */
object Spawn {

    private const val RADIUS = 500
    private const val INVINCIBILITY_SECONDS = 10
    private val invinciblePlayers = ConcurrentHashMap<UUID, Long>()
    private val SPAWN_RANDOMIZED_KEY by lazy { NamespacedKey("anarchycore-nextgen", "spawn-randomized") }

    /**
     * 首次加入：在出生点区域线程内寻找安全位置并异步传送
     */
    @SubscribeEvent
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        val pdc = player.persistentDataContainer
        if (pdc.get(SPAWN_RANDOMIZED_KEY, PersistentDataType.BYTE) == null) {
            pdc.set(SPAWN_RANDOMIZED_KEY, PersistentDataType.BYTE, 1)
            val spawnCenter = player.world.spawnLocation
            Bukkit.getRegionScheduler().execute(BukkitPlugin.getInstance(), spawnCenter) {
                if (!player.isOnline) return@execute
                val spawnLoc = getRandomSafeSpawn(spawnCenter)
                player.teleportAsync(spawnLoc).thenRun {
                    player.scheduler.runDelayed(
                        BukkitPlugin.getInstance(),
                        { applyInvincibility(player) },
                        {},
                        1L
                    )
                }
            }
        }
    }

    /**
     * 重生：在出生点区域线程中计算安全位置，同步设置重生点 + 10 秒无敌
     */
    @SubscribeEvent
    fun onRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        val spawnCenter = player.world.spawnLocation
        // PlayerRespawnEvent 在玩家当前区域线程触发，
        // getRandomSafeSpawn 需要访问出生点区域的方块数据，必须在出生点区域线程执行。
        // 但 respawnLocation 必须在事件处理器内同步设置，不能异步。
        // 因此这里用 spawnCenter 作为默认值，传送后再计算精确位置。
        event.respawnLocation = Location(spawnCenter.world, spawnCenter.x + 0.5, spawnCenter.y + 1.0, spawnCenter.z + 0.5)
        Bukkit.getRegionScheduler().execute(BukkitPlugin.getInstance(), spawnCenter) {
            if (!player.isOnline) return@execute
            val safeLoc = getRandomSafeSpawn(spawnCenter)
            player.teleportAsync(safeLoc).thenRun {
                player.scheduler.runDelayed(
                    BukkitPlugin.getInstance(),
                    { applyInvincibility(player) },
                    {},
                    1L
                )
            }
        }
    }

    /**
     * 无敌期间取消所有伤害
     */
    @SubscribeEvent
    fun onDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        val expireTime = invinciblePlayers[player.uniqueId] ?: return
        if (System.currentTimeMillis() < expireTime) {
            event.isCancelled = true
        } else {
            invinciblePlayers.remove(player.uniqueId)
        }
    }

    /**
     * 在出生点半径内找到安全的随机位置
     */
    private fun getRandomSafeSpawn(center: Location): Location {
        val world = center.world ?: Bukkit.getWorlds()[0]
        val random = ThreadLocalRandom.current()
        val maxY = world.maxHeight

        repeat(50) {
            val offsetX = random.nextInt(-RADIUS, RADIUS + 1)
            val offsetZ = random.nextInt(-RADIUS, RADIUS + 1)
            val x = center.x + offsetX
            val z = center.z + offsetZ
            val y = world.getHighestBlockYAt(x.toInt(), z.toInt()).toDouble()

            if (y < 1 || y > maxY - 3) return@repeat

            val above1 = world.getBlockAt(x.toInt(), y.toInt() + 1, z.toInt())
            val above2 = world.getBlockAt(x.toInt(), y.toInt() + 2, z.toInt())
            val below = world.getBlockAt(x.toInt(), y.toInt() - 1, z.toInt())
            val atFeet = world.getBlockAt(x.toInt(), y.toInt(), z.toInt())

            if (!below.type.isSolid) return@repeat
            if (below.type == Material.LAVA || below.type == Material.MAGMA_BLOCK) return@repeat
            if (above1.type != Material.AIR || above2.type != Material.AIR) return@repeat
            if (atFeet.type == Material.CACTUS || atFeet.type == Material.FIRE) return@repeat

            return Location(world, x + 0.5, y + 1.0, z + 0.5)
        }

        return Location(world, center.x + 0.5, center.y + 1.0, center.z + 0.5)
    }

    private fun applyInvincibility(player: Player) {
        invinciblePlayers[player.uniqueId] = System.currentTimeMillis() + INVINCIBILITY_SECONDS * 1000L
        player.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, INVINCIBILITY_SECONDS * 20, 255, false, false, false))
        player.addPotionEffect(PotionEffect(PotionEffectType.FIRE_RESISTANCE, INVINCIBILITY_SECONDS * 20, 0, false, false, false))
        player.addPotionEffect(PotionEffect(PotionEffectType.GLOWING, INVINCIBILITY_SECONDS * 20, 0, false, false, false))
    }
}
