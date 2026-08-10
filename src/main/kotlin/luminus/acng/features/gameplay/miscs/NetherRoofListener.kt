package luminus.acng.features.gameplay.miscs

import luminus.acng.Main.config
import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.platform.BukkitPlugin
import java.util.concurrent.ThreadLocalRandom

/**
 * 地狱顶层（Y > 128）限制器。
 *
 * - 开启后，无权限 `2b2tcore.runmax` 的玩家在地狱 Y > 128 时会被传送到 Y <= 128 的最近安全位置，
 *   并清理当前区块 Y > 128 的方块与非玩家实体（不产生掉落物）。
 * - 有权限的玩家在 Y > 128 时若继续上升到 Y >= 256，会被自动传送到下方最近安全处（不清理方块）。
 *
 * 所有世界/方块/实体操作均在玩家区域线程执行，符合 Folia 线程安全规则。
 */
object NetherRoofListener : Listener {

    private const val PERMISSION = "2b2tcore.runmax"
    private const val NETHER_WORLD_NAME = "world_nether"
    private const val CLEANUP_HEIGHT = 128.0
    private const val FORCE_TELEPORT_HEIGHT = 256.0

    @Awake(LifeCycle.ENABLE)
    fun register() {
        if (!config.getBoolean("nether-roof.enable", false)) return
        BukkitPlugin.getInstance().server.pluginManager.registerEvents(this, BukkitPlugin.getInstance())
    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        if (!config.getBoolean("nether-roof.enable", false)) return
        val to = event.to ?: return
        if (!isNether(to.world)) return
        val player = event.player
        val y = to.y

        if (player.hasPermission(PERMISSION)) {
            // 有权限：超过 256 强制传送到下方安全处
            if (y >= FORCE_TELEPORT_HEIGHT) {
                teleportToSafeBelow(player, to)
            }
            return
        }

        // 无权限：超过 128 强制传送到下方安全处并清理周围
        if (y > CLEANUP_HEIGHT) {
            cleanupAbove(player, to)
            teleportToSafeBelow(player, to)
        }
    }

    private fun isNether(world: World?): Boolean {
        if (world == null) return false
        return world.environment == World.Environment.NETHER || world.name == NETHER_WORLD_NAME
    }

    /** 传送到 Y <= 128 的最近安全坐标（低于基岩层） */
    private fun teleportToSafeBelow(player: Player, from: Location) {
        val world = from.world ?: return
        val safe = findSafeLocationBelow(world, from.blockX, from.blockZ)
        try {
            player.teleportAsync(safe)
        } catch (_: NoSuchMethodError) {
            // 非 Folia 服务器回退同步 teleport
            player.teleport(safe)
        }
    }

    /** 在世界中寻找 Y <= 128 的安全站立位置 */
    private fun findSafeLocationBelow(world: World, x: Int, z: Int): Location {
        // 首先在 (x,z) 列从 128 向下找安全点
        val startY = 120.coerceAtMost(world.maxHeight.coerceAtMost(128))
        for (y in startY downTo 5) {
            val block = world.getBlockAt(x, y, z)
            if (isSafeStandingBlock(block)) {
                return Location(world, x + 0.5, y + 1.0, z + 0.5, 0f, 0f)
            }
        }
        // 找不到则返回世界出生点的安全高度
        val spawn = world.spawnLocation
        val safeY = findSolidY(world, spawn.blockX, spawn.blockZ) ?: 120
        return Location(world, spawn.x, safeY.toDouble(), spawn.z, spawn.yaw, spawn.pitch)
    }

    private fun isSafeStandingBlock(block: Block): Boolean {
        if (block.isEmpty || block.isLiquid) return false
        val type = block.type
        if (type == Material.BEDROCK || type == Material.LAVA || type == Material.FIRE) return false
        if (!block.getRelative(0, 1, 0).isEmpty) return false
        if (!block.getRelative(0, 2, 0).isEmpty) return false
        return true
    }

    private fun findSolidY(world: World, x: Int, z: Int): Int? {
        for (y in 120 downTo 5) {
            val block = world.getBlockAt(x, y, z)
            if (isSafeStandingBlock(block)) return y + 1
        }
        return null
    }

    /** 清理玩家所在区块及周边（同一 region 内）Y > 128 的方块与实体 */
    private fun cleanupAbove(player: Player, center: Location) {
        val world = center.world ?: return
        val chunkRadius = config.getInt("nether-roof.cleanup-radius-chunks", 1).coerceAtLeast(0).coerceAtMost(2)
        val minY = 129
        val maxY = minOf(world.maxHeight, 255)

        val centerChunk = world.getChunkAt(center)
        val chunks = mutableListOf<Chunk>()
        for (dx in -chunkRadius..chunkRadius) {
            for (dz in -chunkRadius..chunkRadius) {
                chunks.add(world.getChunkAt(centerChunk.x + dx, centerChunk.z + dz))
            }
        }

        // 打乱顺序避免总是从同一位置开始清理，减少卡顿峰值
        chunks.shuffle(ThreadLocalRandom.current())
        chunks.forEach { chunk ->
            cleanupChunk(chunk, minY, maxY)
        }

        // 清理这些 chunk 中 Y > 128 的非玩家实体
        chunks.flatMap { it.entities.asList() }
            .filter { it !is Player && it.location.y > CLEANUP_HEIGHT }
            .forEach { it.remove() }
    }

    private fun cleanupChunk(chunk: Chunk, minY: Int, maxY: Int) {
        for (x in 0..15) {
            for (z in 0..15) {
                for (y in minY..maxY) {
                    val block = chunk.getBlock(x, y, z)
                    if (!block.isEmpty && !block.isLiquid) {
                        block.type = Material.AIR
                    }
                }
            }
        }
    }
}
