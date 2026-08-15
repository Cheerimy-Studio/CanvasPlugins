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
import taboolib.common.platform.function.info
import taboolib.platform.BukkitPlugin
import java.util.concurrent.ThreadLocalRandom

/**
 * 地狱顶层（Y > 128）限制器。
 *
 * - 开启后，无权限 `2b2tcore.runmax` 的玩家在地狱 Y > 128 时会被传送到 Y <= 128 的最近安全位置，
 *   并清理周围已加载区块 Y > 128 的方块与非玩家实体（不产生掉落物）。
 * - 有权限的玩家在 Y > 128 时若继续上升到 Y >= 256，会被自动传送到下方最近安全处（不清理方块）。
 *
 * Folia 线程安全：
 * - 所有方块/实体操作均调度到对应 chunk 的 RegionScheduler 执行；
 * - teleport 优先使用 teleportAsync；
 * - 清理前检查 isChunkLoaded，避免强制加载未加载区块。
 */
object NetherRoofListener : Listener {

    private const val PERMISSION = "2b2tcore.runmax"
    private const val NETHER_WORLD_NAME = "world_nether"
    private const val CLEANUP_HEIGHT = 128.0
    private const val FORCE_TELEPORT_HEIGHT = 256.0

    private var registered = false

    @Awake(LifeCycle.ENABLE)
    fun register() {
        if (!config.getBoolean("nether-roof.enable", false)) return
        if (registered) return
        BukkitPlugin.getInstance().server.pluginManager.registerEvents(this, BukkitPlugin.getInstance())
        registered = true
    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        if (!config.getBoolean("nether-roof.enable", false)) return
        val to = event.to
        if (to == null) return
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
        // Folia 安全：优先 teleportAsync
        try {
            player.teleportAsync(safe)
        } catch (_: NoSuchMethodError) {
            player.teleport(safe)
        }
    }

    /** 在 (x,z) 处查找 Y <= 128 的最近安全落脚位置 */
    private fun findSafeLocationBelow(world: World, x: Int, z: Int): Location {
        val solidY = findSolidY(world, x, z)
        if (solidY != null) {
            return Location(world, x + 0.5, solidY.toDouble(), z + 0.5)
        }
        // 找不到固体方块：使用 world 自然最高方块
        return world.getHighestBlockAt(x, z).location.add(0.5, 1.0, 0.5)
    }

    /** 判断方块是否为安全落脚点（实心、上方2格为空气、非基岩/岩浆/火） */
    private fun isSafeStandingBlock(block: Block): Boolean {
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

    /** 清理玩家所在区块及周边（仅已加载区块）Y > 128 的方块与实体 */
    private fun cleanupAbove(player: Player, center: Location) {
        val world = center.world ?: return
        val chunkRadius = config.getInt("nether-roof.cleanup-radius-chunks", 1).coerceAtLeast(0).coerceAtMost(2)
        val minY = 129
        val maxY = minOf(world.maxHeight, 255)

        val centerChunkX = center.blockX shr 4
        val centerChunkZ = center.blockZ shr 4

        val chunkPositions = mutableListOf<Pair<Int, Int>>()
        for (dx in -chunkRadius..chunkRadius) {
            for (dz in -chunkRadius..chunkRadius) {
                val cx = centerChunkX + dx
                val cz = centerChunkZ + dz
                if (world.isChunkLoaded(cx, cz)) {
                    chunkPositions.add(cx to cz)
                }
            }
        }
        chunkPositions.shuffle(ThreadLocalRandom.current())

        var scheduled = 0
        for ((cx, cz) in chunkPositions) {
            // 每个区块调度到它自己的区域执行，避免 Folia 跨区域访问异常
            runInRegion(world, cx, cz) {
                try {
                    val chunk = world.getChunkAt(cx, cz)
                    val cleaned = cleanupChunk(chunk, minY, maxY)
                    val removed = cleanupEntities(chunk)
                    if (cleaned > 0 || removed > 0) {
                        info("[2B2TCore] 地狱顶层清理：方块×$cleaned 实体×$removed（chunk=$cx,$cz，玩家=${player.name}）")
                    }
                } catch (ex: Exception) {
                    info("[2B2TCore] 地狱顶层清理失败 chunk=$cx,$cz: ${ex.message}")
                }
            }
            scheduled++
        }

        if (scheduled == 0) {
            info("[2B2TCore] 地狱顶层清理：玩家 ${player.name} 周围无已加载区块可清理")
        }
    }

    /** 调度 runnable 到指定 chunk 所在区域执行（Folia 安全） */
    private fun runInRegion(world: World, cx: Int, cz: Int, runnable: Runnable) {
        val location = Location(world, (cx shl 4).toDouble(), 128.0, (cz shl 4).toDouble())
        try {
            // Folia / Paper 1.19.4+
            val server = Bukkit.getServer()
            val getRegionScheduler = server.javaClass.getMethod("getRegionScheduler")
            val regionScheduler = getRegionScheduler.invoke(server)
            regionScheduler.javaClass.getMethod("execute", org.bukkit.plugin.Plugin::class.java, Location::class.java, Runnable::class.java)
                .invoke(regionScheduler, BukkitPlugin.getInstance(), location, runnable)
        } catch (_: NoSuchMethodError) {
            // 旧版 Paper/Spigot：直接运行
            runnable.run()
        } catch (ex: Exception) {
            // 反射失败回退
            runnable.run()
        }
    }

    /**
     * 清理单个区块 Y 在 [minY, maxY] 范围的方块。
     * setType(Material.AIR, false) — 不再掉落物品。
     * @return 实际清理的方块数量
     */
    private fun cleanupChunk(chunk: Chunk, minY: Int, maxY: Int): Int {
        var count = 0
        for (x in 0..15) {
            for (z in 0..15) {
                for (y in minY..maxY) {
                    val block = try {
                        chunk.getBlock(x, y, z)
                    } catch (_: Exception) {
                        continue
                    }
                    if (!block.isEmpty && !block.isLiquid) {
                        try {
                            block.setType(Material.AIR, false)
                            count++
                        } catch (_: Exception) {
                            // 跨区域或区域锁冲突 → 跳过
                        }
                    }
                }
            }
        }
        return count
    }

    /** 清理该区块中 Y > 128 的非玩家实体（生物、掉落物、矿车等） */
    private fun cleanupEntities(chunk: Chunk): Int {
        var count = 0
        try {
            val deadEntities = chunk.entities.filter { it !is Player && it.location.y > CLEANUP_HEIGHT }
            for (entity in deadEntities) {
                try {
                    entity.remove()
                    count++
                } catch (_: Exception) {
                    // 实体可能已被移除
                }
            }
        } catch (_: Exception) {
            // 区块实体列表不可用
        }
        return count
    }
}