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
 * Folia 线程安全：
 * - 方块读取（getHighestBlockAt / getRelative）必须调度到目标区块所在区域线程，
 *   否则跨区域访问抛异常导致随机传送全部失败、fallback 到世界出生点（00 附近）。
 * - 所有传送使用 teleportAsync。
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
     * 死亡重生：未设置床/锚重生点时重生到出生点周围随机位置 + 无敌。
     * isBedSpawn 为 true 表示玩家有有效床或重生锚，不干预。
     *
     * Folia 关键：onRespawn 在玩家死亡位置的区域线程触发，直接读取出生点附近
     * 区块的方块会跨区域抛异常 → 全部尝试失败 → fallback 世界出生点（00 附近）。
     * 因此这里只做纯数学随机坐标设置 respawnLocation（先让玩家出现在随机范围），
     * 精确的安全落点修正由重生后的区域调度任务完成。
     * 世界取 event.respawnLocation.world（玩家实际重生世界），兼容下界/末地死亡。
     */
    @EventHandler
    fun onRespawn(event: PlayerRespawnEvent) {
        if (!config.getBoolean("spawn.enable", true)) return
        if (!config.getBoolean("spawn.respawn-random", true)) return
        if (event.isBedSpawn) return

        val player = event.player
        val world = event.respawnLocation.world ?: player.world
        val radius = config.getInt("spawn.radius", 500)
        val spawn = world.spawnLocation
        val rnd = ThreadLocalRandom.current()
        val x = spawn.blockX + rnd.nextInt(-radius, radius + 1)
        val z = spawn.blockZ + rnd.nextInt(-radius, radius + 1)
        // 尝试获取该坐标地形高度（可能跨区域抛异常，失败用出生点高度兜底）
        val y = try {
            (world.getHighestBlockYAt(x, z) + 1).toDouble()
        } catch (_: Exception) {
            spawn.y
        }
        event.respawnLocation = Location(world, x + 0.5, y, z + 0.5)

        // 重生后：安全修正（区域线程）+ 无敌（玩家实体线程）
        player.scheduler.run(BukkitPlugin.getInstance(), { _ ->
            teleportToSafeRandom(player, x, z)
            grantInvulnerability(player)
        }, null)
    }

    /** 随机传送到世界出生点周围（调度到玩家实体线程） */
    private fun randomTeleport(player: Player) {
        player.scheduler.run(BukkitPlugin.getInstance(), { _ ->
            doRandomTeleport(player)
        }, null)
    }

    /**
     * 在出生点周围随机选点并传送到安全位置。
     * 通过 RegionScheduler 调度到目标区块所在区域执行方块读取与传送，
     * 避免 Folia 跨区域访问异常（旧实现导致全部尝试失败而 fallback 出生点）。
     */
    private fun doRandomTeleport(player: Player) {
        val radius = config.getInt("spawn.radius", 500)
        val world = player.world
        val worldSpawn = world.spawnLocation
        val rnd = ThreadLocalRandom.current()
        val maxAttempts = config.getInt("spawn.max-attempts", 50)
        val chunkRadius = (radius / 16).coerceAtLeast(1)

        var attempts = 0
        fun tryRandomChunk() {
            if (attempts >= maxAttempts) {
                player.teleportAsync(world.spawnLocation)
                return
            }
            attempts++
            val cx = (worldSpawn.blockX shr 4) + rnd.nextInt(-chunkRadius, chunkRadius + 1)
            val cz = (worldSpawn.blockZ shr 4) + rnd.nextInt(-chunkRadius, chunkRadius + 1)
            Bukkit.getRegionScheduler().execute(BukkitPlugin.getInstance(), world, cx, cz) {
                // 在该区块内随机找安全点（同区域线程读取方块安全）
                for (i in 0 until 8) {
                    val bx = (cx shl 4) + rnd.nextInt(16)
                    val bz = (cz shl 4) + rnd.nextInt(16)
                    val loc = findSafeLocation(world, bx, bz)
                    if (loc != null) {
                        player.teleportAsync(loc)
                        return@execute
                    }
                }
                // 本区块无安全点，尝试下一个随机区块
                tryRandomChunk()
            }
        }
        tryRandomChunk()
    }

    /** 传送到指定坐标附近的安全点（内部调度到目标区块区域线程） */
    private fun teleportToSafeRandom(player: Player, x: Int, z: Int) {
        val world = player.world
        val cx = x shr 4
        val cz = z shr 4
        Bukkit.getRegionScheduler().execute(BukkitPlugin.getInstance(), world, cx, cz) {
            val loc = findSafeLocation(world, x, z)
            if (loc != null) {
                player.teleportAsync(loc)
            } else {
                // 目标点不安全（如岩浆/水），重新随机传送
                doRandomTeleport(player)
            }
        }
    }

    /** 寻找安全落脚点：实心方块上方两格空气，且不是危险方块（须在目标区域线程调用） */
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
            // 区域锁冲突等异常，安全跳过
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
