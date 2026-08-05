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
import taboolib.common.platform.function.submit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom

/**
 * 出生点功能：
 * - 首次加入/重生时在出生点半径 500 格内随机传送到安全位置
 * - 重生后 10 秒无敌保护期
 */
object Spawn {

    private const val RADIUS = 500
    private const val INVINCIBILITY_SECONDS = 10
    private val invinciblePlayers = ConcurrentHashMap<UUID, Long>()
    private val SPAWN_RANDOMIZED_KEY by lazy { NamespacedKey("anarchycore-nextgen", "spawn-randomized") }

    /**
     * 首次加入：传送到随机安全出生点
     */
    @SubscribeEvent
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        val pdc = player.persistentDataContainer
        // 仅首次加入时随机传送（标记后不再重复）
        if (pdc.get(SPAWN_RANDOMIZED_KEY, PersistentDataType.BYTE) == null) {
            pdc.set(SPAWN_RANDOMIZED_KEY, PersistentDataType.BYTE, 1)
            // 延迟 1 tick 确保玩家完全加载
            submit(delay = 1L) {
                teleportToSafeSpawn(player)
            }
        }
    }

    /**
     * 重生：传送到随机安全出生点 + 10 秒无敌
     */
    @SubscribeEvent
    fun onRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        val spawnLoc = getRandomSafeSpawn(player.world.spawnLocation)
        event.respawnLocation = spawnLoc
        // 重生后给予无敌保护
        submit(delay = 1L) {
            applyInvincibility(player)
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

    private fun teleportToSafeSpawn(player: Player) {
        val spawnLoc = getRandomSafeSpawn(player.world.spawnLocation)
        player.teleport(spawnLoc)
        applyInvincibility(player)
    }

    /**
     * 在出生点半径内找到安全的随机位置
     * 安全标准：脚下是实体方块，头顶 2 格无阻挡，不在岩浆/水中
     */
    private fun getRandomSafeSpawn(center: Location): Location {
        val world = center.world ?: Bukkit.getWorlds()[0]
        val random = ThreadLocalRandom.current()
        val maxY = world.maxHeight

        // 最多尝试 50 次找到安全位置
        repeat(50) {
            val offsetX = random.nextInt(-RADIUS, RADIUS + 1)
            val offsetZ = random.nextInt(-RADIUS, RADIUS + 1)
            val x = center.x + offsetX
            val z = center.z + offsetZ
            val y = world.getHighestBlockYAt(x.toInt(), z.toInt()).toDouble()

            if (y < 1 || y > maxY - 3) return@repeat

            val block = world.getBlockAt(x.toInt(), y.toInt(), z.toInt())
            val above1 = world.getBlockAt(x.toInt(), y.toInt() + 1, z.toInt())
            val above2 = world.getBlockAt(x.toInt(), y.toInt() + 2, z.toInt())
            val below = world.getBlockAt(x.toInt(), y.toInt() - 1, z.toInt())

            // 脚下必须是实体方块（非空气、非液体）
            if (!below.type.isSolid) return@repeat
            // 脚下不能是岩浆
            if (below.type == Material.LAVA || below.type == Material.MAGMA_BLOCK) return@repeat
            // 头顶 2 格必须为空气
            if (above1.type != Material.AIR || above2.type != Material.AIR) return@repeat
            // 脚下不能是仙人掌、火、熔岩等危险方块
            if (block.type == Material.CACTUS || block.type == Material.FIRE) return@repeat

            return Location(world, x + 0.5, y + 1.0, z + 0.5)
        }

        // 兜底：直接返回出生点上方
        return Location(world, center.x + 0.5, center.y + 1.0, center.z + 0.5)
    }

    /**
     * 给予 10 秒无敌保护（发光效果 + 抗性提升）
     */
    private fun applyInvincibility(player: Player) {
        invinciblePlayers[player.uniqueId] = System.currentTimeMillis() + INVINCIBILITY_SECONDS * 1000L
        player.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, INVINCIBILITY_SECONDS * 20, 255, false, false, false))
        player.addPotionEffect(PotionEffect(PotionEffectType.FIRE_RESISTANCE, INVINCIBILITY_SECONDS * 20, 0, false, false, false))
        player.addPotionEffect(PotionEffect(PotionEffectType.GLOWING, INVINCIBILITY_SECONDS * 20, 0, false, false, false))
        player.sendMessage("§e[2B2TCore] §a你有 ${INVINCIBILITY_SECONDS} 秒无敌保护期！")
    }
}
