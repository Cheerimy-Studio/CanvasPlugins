package luminus.acng.features.gameplay.duplications

import luminus.acng.Main.config
import luminus.acng.msg
import org.bukkit.ChatColor
import org.bukkit.NamespacedKey
import org.bukkit.block.BlockState
import org.bukkit.block.ShulkerBox
import org.bukkit.block.TileState
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta
import org.bukkit.inventory.meta.BundleMeta
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Bukkit
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.platform.BukkitPlugin
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 复制品标记系统。
 *
 * 核心规则：
 * - 所有复制产生的物品都带「复制品」词条（lore + PDC 2b2tcore:replica）
 * - 复制品只能「使用」或「放置」，不可二次更改（交易/锻造/重命名/合成/磨石/附魔，
 *   由 ItemTag flag 拦截），不可二次复制（checkCanDupe 拦截）
 * - 潜影盒 / 收纳袋内部物品递归打标（最多 5 层）
 * - 拥有 2b2tcore.dupe.original 权限的玩家复制出的物品为原版（不带词条与 flag）
 * - 产物继承：熔炉/营火/烟熏器/高炉烧炼产物自动继承复制品标记（见 ReplicaBlockListener）
 * - 方块持久化：
 *   - TileState 方块（潜影盒/熔炉等）放置时写入 PDC，挖掘掉落物恢复标记（重启不丢）
 *   - 普通方块（钻石块/海绵等）放置时坐标写入内存 + 分世界分区块硬盘缓存
 *     （data/replica-blocks/<world>/<cx>.<cz>.txt），挖掘恢复标记（重启不丢）
 *
 * ItemTag flag 由 ItemTag 插件的监听器拦截，2B2TCore 不重复实现拦截逻辑。
 */
object Replica {

    val replicaKey = NamespacedKey("2b2tcore", "replica")
    const val ORIGINAL_PERMISSION = "2b2tcore.dupe.original"
    private const val MAX_NESTING = 5

    // ==================== 普通方块位置记录（内存 Set + 分世界分区块硬盘缓存） ====================

    /** 区块键：world + 区块坐标（区分区块，便于按区块落盘） */
    private data class ChunkKey(val world: String, val cx: Int, val cz: Int)

    /** 内存权威：区块 -> 该区块内复制品普通方块坐标集（"x:y:z"） */
    private val replicaBlockLocations = ConcurrentHashMap<ChunkKey, MutableSet<String>>()

    /** 脏区块：有未落盘变更的区块（debounce 后按区块增量写盘） */
    private val dirtyChunks = ConcurrentHashMap.newKeySet<ChunkKey>()

    /** 数据目录：plugins/2B2TCore/data/replica-blocks/<world>/<cx>.<cz>.txt */
    private val dataDir: File by lazy {
        File(BukkitPlugin.getInstance().dataFolder, "data/replica-blocks")
    }

    /** debounce flush 任务（延迟 1 秒合并多次变更后写盘） */
    private var flushTask: ScheduledTask? = null

    private fun chunkKeyOf(world: String, x: Int, z: Int) = ChunkKey(world, x shr 4, z shr 4)

    private fun coordKey(x: Int, y: Int, z: Int) = "$x:$y:$z"

    private fun chunkFile(key: ChunkKey) = File(dataDir, "${key.world}/${key.cx}.${key.cz}.txt")

    /** 启动时加载硬盘缓存到内存（遍历分世界分区块文件） */
    @Awake(LifeCycle.ENABLE)
    fun loadLocations() {
        if (!dataDir.exists()) return
        runCatching {
            dataDir.listFiles()?.forEach { worldDir ->
                if (!worldDir.isDirectory) return@forEach
                val world = worldDir.name
                worldDir.listFiles()?.forEach { file ->
                    if (!file.isFile || !file.name.endsWith(".txt")) return@forEach
                    val parts = file.nameWithoutExtension.split(".")
                    if (parts.size != 2) return@forEach
                    val cx = parts[0].toIntOrNull() ?: return@forEach
                    val cz = parts[1].toIntOrNull() ?: return@forEach
                    val key = ChunkKey(world, cx, cz)
                    val set = replicaBlockLocations.getOrPut(key) { ConcurrentHashMap.newKeySet() }
                    file.readLines().forEach { line -> if (line.isNotBlank()) set.add(line) }
                }
            }
        }
    }

    /** 卸载时强制落盘 */
    @Awake(LifeCycle.DISABLE)
    fun flushOnDisable() {
        flushTask?.cancel()
        saveDirtyChunks()
    }

    /** 立即写盘（仅写脏区块，空区块删除文件） */
    private fun saveDirtyChunks() {
        if (dirtyChunks.isEmpty()) return
        val dirty = dirtyChunks.toList()
        dirtyChunks.clear()
        dirty.forEach { key ->
            val set = replicaBlockLocations[key]
            val file = chunkFile(key)
            runCatching {
                if (set == null || set.isEmpty()) {
                    file.delete()
                } else {
                    file.parentFile?.mkdirs()
                    file.writeText(set.joinToString("\n"))
                }
            }
        }
    }

    /** 变更后延迟 1 秒写盘（合并高频变更，避免频繁 I/O） */
    private fun scheduleFlush() {
        flushTask?.cancel()
        flushTask = Bukkit.getGlobalRegionScheduler().runDelayed(
            BukkitPlugin.getInstance(), { _ -> saveDirtyChunks() }, 20L
        )
    }

    /** 放置普通方块复制品时记录坐标 */
    fun recordBlockLocation(world: String, x: Int, y: Int, z: Int) {
        val key = chunkKeyOf(world, x, z)
        val set = replicaBlockLocations.getOrPut(key) { ConcurrentHashMap.newKeySet() }
        if (set.add(coordKey(x, y, z))) {
            dirtyChunks.add(key)
            scheduleFlush()
        }
    }

    /** 检查并移除坐标记录（挖掘时调用，命中=该掉落物应恢复标记，同时及时卸载） */
    fun isRecordedBlock(world: String, x: Int, y: Int, z: Int): Boolean {
        val key = chunkKeyOf(world, x, z)
        val set = replicaBlockLocations[key] ?: return false
        if (set.remove(coordKey(x, y, z))) {
            if (set.isEmpty()) replicaBlockLocations.remove(key)
            dirtyChunks.add(key)
            scheduleFlush()
            return true
        }
        return false
    }

    /** 清除坐标记录（非复制品放置/爆炸/燃烧/褪色时清理陈旧记录，防止误标） */
    fun forgetBlockLocation(world: String, x: Int, y: Int, z: Int) {
        val key = chunkKeyOf(world, x, z)
        val set = replicaBlockLocations[key] ?: return
        if (set.remove(coordKey(x, y, z))) {
            if (set.isEmpty()) replicaBlockLocations.remove(key)
            dirtyChunks.add(key)
            scheduleFlush()
        }
    }

    /**
     * 原子迁移坐标（活塞推动/重力方块落地时调用）：移除旧坐标 + 记录新坐标，
     * 只触发一次 debounce flush。旧坐标无记录时静默忽略。
     */
    fun moveBlockLocation(world: String, fx: Int, fy: Int, fz: Int, tx: Int, ty: Int, tz: Int) {
        val fromKey = chunkKeyOf(world, fx, fz)
        val fromSet = replicaBlockLocations[fromKey] ?: return
        if (fromSet.remove(coordKey(fx, fy, fz))) {
            dirtyChunks.add(fromKey)
            if (fromSet.isEmpty()) replicaBlockLocations.remove(fromKey)
            val toKey = chunkKeyOf(world, tx, tz)
            val toSet = replicaBlockLocations.getOrPut(toKey) { ConcurrentHashMap.newKeySet() }
            if (toSet.add(coordKey(tx, ty, tz))) dirtyChunks.add(toKey)
            scheduleFlush()
        }
    }

    /**
     * ItemTag 禁止「二次更改」flag（仅打 ItemTag 实际支持的 flag）。
     * 不打 placeable / usable：复制品允许放置和使用。
     * 不打 smelt / furnacefuel：熔炉产物通过 ReplicaBlockListener 继承标记。
     * PDC 键 itemtag:<flag>，INTEGER 0 = 禁止，key 存在即被 ItemTag 拦截。
     */
    private val itemTagFlags = listOf(
        "tradeable",        // 不可与村民交易
        "smithing_table",   // 不可在锻造台使用
        "renamable",        // 不可在铁砧上重命名
        "craft_ingredient", // 不可用于合成
        "grindable",        // 不可在磨石上分解
        "enchantable",      // 不可在附魔台上附魔
    )

    private val loreLine: String
        get() = ChatColor.translateAlternateColorCodes(
            '&', config.getString("duplication.replica.lore", "&7复制品") ?: "&7复制品"
        )

    private val plainLoreLine: String
        get() = ChatColor.stripColor(loreLine) ?: "复制品"

    private fun enabled(): Boolean = config.getBoolean("duplication.replica.enable", true)
    private fun itemTagFlagsEnabled(): Boolean = config.getBoolean("duplication.replica.itemtag-flags", true)

    // ==================== 判断 ====================

    fun isReplica(item: ItemStack?): Boolean {
        if (item == null || item.type.isAir) return false
        val meta = item.itemMeta ?: return false
        if (meta.persistentDataContainer.has(replicaKey, PersistentDataType.BYTE)) return true
        val lore = meta.lore ?: return false
        return lore.any { ChatColor.stripColor(it) == plainLoreLine }
    }

    fun containsReplica(item: ItemStack?): Boolean = containsReplica(item, 0)

    private fun containsReplica(item: ItemStack?, depth: Int): Boolean {
        if (item == null || item.type.isAir) return false
        if (isReplica(item)) return true
        if (depth >= MAX_NESTING) return false
        val meta = item.itemMeta ?: return false
        if (meta is BlockStateMeta && meta.blockState is ShulkerBox) {
            val inv = (meta.blockState as ShulkerBox).inventory
            inv.contents.forEach { c -> if (c != null && !c.type.isAir && containsReplica(c, depth + 1)) return true }
        }
        if (meta is BundleMeta) {
            meta.items.forEach { c -> if (!c.type.isAir && containsReplica(c, depth + 1)) return true }
        }
        return false
    }

    fun isReplicaBlock(state: BlockState): Boolean {
        val pdc = (state as? TileState)?.persistentDataContainer ?: return false
        return pdc.has(replicaKey, PersistentDataType.BYTE)
    }

    fun blockContainsReplica(state: BlockState): Boolean {
        if (isReplicaBlock(state)) return true
        if (state is ShulkerBox) {
            state.inventory.contents.forEach { c -> if (c != null && !c.type.isAir && containsReplica(c)) return true }
        }
        return false
    }

    // ==================== 打标 ====================

    fun mark(item: ItemStack): ItemStack = mark(item, 0)

    private fun mark(item: ItemStack, depth: Int): ItemStack {
        if (item.type.isAir) return item
        val meta = item.itemMeta ?: return item
        applyReplicaMeta(meta)

        // 潜影盒内部递归
        if (depth < MAX_NESTING && meta is BlockStateMeta) {
            val state = meta.blockState
            if (state is ShulkerBox) {
                val inv = state.inventory
                for (i in inv.contents.indices) {
                    val c = inv.getItem(i) ?: continue
                    if (!c.type.isAir) inv.setItem(i, mark(c, depth + 1))
                }
                meta.blockState = state
            }
        }
        // 收纳袋内部递归
        if (depth < MAX_NESTING && meta is BundleMeta && meta.items.isNotEmpty()) {
            meta.setItems(meta.items.map { mark(it, depth + 1) })
        }

        item.itemMeta = meta
        return item
    }

    private fun applyReplicaMeta(meta: ItemMeta) {
        meta.persistentDataContainer.set(replicaKey, PersistentDataType.BYTE, 1.toByte())
        if (itemTagFlagsEnabled()) {
            itemTagFlags.forEach { flag ->
                meta.persistentDataContainer.set(NamespacedKey("itemtag", flag), PersistentDataType.INTEGER, 0)
            }
        }
        val lore = meta.lore?.toMutableList() ?: mutableListOf()
        if (lore.none { ChatColor.stripColor(it) == plainLoreLine }) {
            lore.add(loreLine)
        }
        meta.lore = lore
    }

    // ==================== 输出与拦截 ====================

    fun output(player: Player?, item: ItemStack): ItemStack {
        if (enabled() && (player == null || !player.hasPermission(ORIGINAL_PERMISSION))) {
            mark(item)
        }
        return item
    }

    fun checkCanDupe(player: Player?, item: ItemStack?): Boolean {
        if (!enabled()) return false
        if (!containsReplica(item)) return false
        deny(player)
        return true
    }

    private val denyCooldowns = ConcurrentHashMap<UUID, Long>()

    private fun denyCooldownMillis(): Long =
        config.getLong("duplication.replica.deny-cooldown-seconds", 60) * 1000

    fun deny(player: Player?) {
        if (player == null) return
        val now = System.currentTimeMillis()
        val last = denyCooldowns[player.uniqueId]
        if (last != null && now - last < denyCooldownMillis()) return
        denyCooldowns[player.uniqueId] = now
        player.msg(config.getString("messages.deny-redupe", "&c复制品无法再次复制！") ?: "&c复制品无法再次复制！")
    }
}
