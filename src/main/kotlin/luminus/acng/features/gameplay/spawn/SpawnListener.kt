package luminus.acng.features.gameplay.spawn

import luminus.acng.Main.config
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerRespawnEvent
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.platform.BukkitPlugin
import java.util.concurrent.ThreadLocalRandom

/**
 * 随机出生点 + 死亡随机重生 + 无敌时间
 *
 * - 首次加入：随机传送到出生点周围
 * - 死亡重生：未设置床/锚重生点时也随机重生
 * - 两者均授予配置的无敌时间
 *
 * 全部用玩家 EntityScheduler + teleportAsync 保证 Folia 线程安全。
 */
object SpawnListener : Listener {

    private var registered = false

    @Awake(LifeCycle.ENABLE)
    fun register() {
        if (!config.getBoolean("spawn.enable", true)) return
        if (registered) return
        BukkitPlugin.getInstance().server.pluginManager.registerEvents(this, BukkitPlugin.getInstance())
        registered = true
    }

    /** 首次加入游戏：随机传送 + 无敌 */
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        if (!config.getBoolean("spawn.enable", true)) return
        val player = event.player
        if (player.hasPlayedBefore()) return

        randomTeleport(player)
        grantInvulnerability(player)
    }

    /**
     * 死亡重生：未设置床/锚重生点时直接修改 respawnLocation 为随机安全点 + 无敌。
     * isBedSpawn 为 true 表示玩家有有效床或重生锚，不干预。
     * 直接修改 event.respawnLocation 避免重生后先出现在出生点再传送的视觉闪烁。
     */
    @EventHandler
    fun onRespawn(event: PlayerRespawnEvent) {
        if (!config.getBoolean("spawn.enable", true)) return
        if (!config.getBoolean("spawn.respawn-random", true)) return
        // 已设置床/重生锚 → 尊重原重生点，不随机
        if (event.isBedSpawn) return

        val player = event.player
        val world = player.world
        val safeLoc = findRandomSafeLocation(world) ?: world.spawnLocation
        // 直接设置重生位置，避免先出生再传送的闪烁
        event.respawnLocation = safeLoc
        // 重生后延迟到玩家实体线程执行无敌
        player.scheduler.run(BukkitPlugin.getInstance(), { _ ->
            grantInvulnerability(player)
        }, null)
    }

    /** 随机传送到世界出生点周围（调度到玩家实体线程） */
    private fun randomTeleport(player: Player) {
        player.scheduler.run(BukkitPlugin.getInstance(), { _ ->
            doRandomTeleport(player)
        }, null)
    }

    /** 找到一个随机安全落点（不走调度器，供 onRespawn 直接调用） */
    private fun findRandomSafeLocation(world: World): Location? {
        val radius = config.getInt("spawn.radius", 500)
        val worldSpawn = world.spawnLocation
        val rnd = ThreadLocalRandom.current()
        val maxAttempts = config.getInt("spawn.max-attempts", 50)

        for (attempt in 1..maxAttempts) {
            val x = worldSpawn.blockX + rnd.nextInt(-radius, radius + 1)
            val z = worldSpawn.blockZ + rnd.nextInt(-radius, radius + 1)
            val loc = findSafeLocation(world, x, z)
            if (loc != null) return loc
        }
        return null
    }

    /** 实际执行随机传送（必须在玩家区域线程调用） */
    private fun doRandomTeleport(player: Player) {
        val radius = config.getInt("spawn.radius", 500)
        val world = player.world
        val worldSpawn = world.spawnLocation
        val rnd = ThreadLocalRandom.current()
        val maxAttempts = config.getInt("spawn.max-attempts", 50)

        for (attempt in 1..maxAttempts) {
            val x = worldSpawn.blockX + rnd.nextInt(-radius, radius + 1)
            val z = worldSpawn.blockZ + rnd.nextInt(-radius, radius + 1)
            val loc = findSafeLocation(world, x, z)
            if (loc != null) {
                player.teleportAsync(loc)
                return
            }
        }
        // 找不到安全位置，fallback 到世界出生点
        player.teleportAsync(world.spawnLocation)
    }

    /** 寻找安全落脚点：实心方块上方两格空气，且不是危险方块 */
    private fun findSafeLocation(world: World, x: Int, z: Int): Location? {
        try {
            val highest = world.getHighestBlockAt(x, z)
            if (highest.type == Material.AIR || highest.isLiquid) return null
            val type = highest.type
            if (type == Material.LAVA || type == Material.FIRE || type == Material.SWEET_BERRY_BUSH ||
                type == Material.CACTUS || type == Material.WATER || type == Material.POWDER_SNOW) return null
            if (highest.getRelative(0, 1, 0).type != Material.AIR) return null
            if (highest.getRelative(0, 2, 0).type != Material.AIR) return null
            return Location(world, x + 0.5, highest.y + 1.0, z + 0.5)
        } catch (_: Exception) {
            // Folia 跨区域读取方块可能抛异常，安全跳过
            return null
        }
    }

    /** 授予无敌时间（使用 setInvulnerable + 无敌帧双保险） */
    private fun grantInvulnerability(player: Player) {
        val invulnerableSeconds = config.getInt("spawn.invulnerability-seconds", 10)
        if (invulnerableSeconds > 0) {
            player.noDamageTicks = invulnerableSeconds * 20
            try {
                player.isInvulnerable = true
                // 在玩家实体调度器上延迟取消无敌，避免跨线程访问
                player.scheduler.runDelayed(
                    BukkitPlugin.getInstance(),
                    { player.isInvulnerable = false },
                    null,
                    invulnerableSeconds * 20L
                )
            } catch (_: Throwable) {
                // 旧版本不支持 isInvulnerable，仅依赖 noDamageTicks
            }
        }
    }
}
