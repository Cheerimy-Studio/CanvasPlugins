package luminus.acng.features.gameplay.duplications

import luminus.acng.Main.config
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.TileState
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.event.block.BlockDropItemEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.persistence.PersistentDataType
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.SubscribeEvent
import taboolib.platform.BukkitPlugin
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 复制品方块标签持久化监听器。
 *
 * 问题：复制品物品放置为方块后，ItemStack ItemMeta 上的复制品 PDC 不会自动
 * 转移到方块上，挖掘时默认掉落物不含复制品标记 → 标签丢失。
 *
 * 修复（按方块类型分两种机制）：
 * - TileState 方块（潜影盒、熔炉、木桶等）：放置时将复制品 PDC 写入方块
 *   TileState（随区块数据持久化，重启不丢失）。
 * - 普通方块（下界合金块、钻石块等，无 TileState）：无法在方块上存 PDC，
 *   改用「内存 Map + YAML 文件」记录位置与方块材质，持久化到
 *   plugins/2B2TCore/replica-blocks.yml，重启后重新加载。
 *
 * 掉落拦截统一用 BlockDropItemEvent（覆盖玩家挖掘、爆炸等所有产生掉落物的
 * 破坏方式）：对掉落物 ItemStack 直接打上复制品标记，潜影盒库存自动保留。
 *
 * 独立于 mine-and-place 功能，只要 replica.enable=true 即生效。
 */
object ReplicaBlockListener {

    private data class BlockKey(val world: String, val x: Int, val y: Int, val z: Int)

    /** 普通方块（无 TileState）复制品位置记录：位置 -> 方块材质（用于验证有效性） */
    private val plainBlocks = ConcurrentHashMap<BlockKey, Material>()

    private val dataFile: File
        get() = File(BukkitPlugin.getInstance().dataFolder, "replica-blocks.yml")

    @Awake(LifeCycle.ENABLE)
    fun init() {
        load()
    }

    /** 放置复制品物品为方块时，记录复制品标记（TileState 写 PDC，普通方块写文件） */
    @SubscribeEvent
    fun onPlace(event: BlockPlaceEvent) {
        if (!config.getBoolean("duplication.replica.enable", true)) return
        val item = event.itemInHand
        if (!Replica.isReplica(item)) return

        val block = event.blockPlaced
        val state = block.state
        if (state is TileState) {
            state.persistentDataContainer.set(Replica.replicaKey, PersistentDataType.BYTE, 1.toByte())
            state.update()
        } else {
            plainBlocks[keyOf(block)] = block.type
            save()
        }
    }

    /** 方块掉落物品时，若方块为复制品方块，则对掉落物打上复制品标记 */
    @SubscribeEvent
    fun onDrop(event: BlockDropItemEvent) {
        if (!config.getBoolean("duplication.replica.enable", true)) return
        val state = event.blockState
        val isReplica = if (Replica.isReplicaBlock(state)) {
            true
        } else {
            // 普通方块：查位置记录并校验方块材质，避免残留记录误伤
            val key = keyOf(state.world.name, state.x, state.y, state.z)
            val recorded = plainBlocks.remove(key)
            val valid = recorded != null && recorded == state.type
            if (recorded != null) save()
            valid
        }

        if (!isReplica) return
        event.items.forEach { entity ->
            entity.itemStack = Replica.mark(entity.itemStack)
        }
    }

    private fun keyOf(block: Block): BlockKey = keyOf(block.world.name, block.x, block.y, block.z)

    private fun keyOf(world: String, x: Int, y: Int, z: Int): BlockKey = BlockKey(world, x, y, z)

    /** 从 YAML 文件加载普通方块复制品位置记录 */
    private fun load() {
        plainBlocks.clear()
        if (!dataFile.exists()) return
        try {
            val cfg = YamlConfiguration.loadConfiguration(dataFile)
            for (entry in cfg.getStringList("blocks")) {
                val parts = entry.split(';')
                if (parts.size != 5) continue
                val material = Material.matchMaterial(parts[4]) ?: continue
                plainBlocks[BlockKey(parts[0], parts[1].toInt(), parts[2].toInt(), parts[3].toInt())] = material
            }
        } catch (_: Throwable) {
            // 文件损坏时忽略，不阻塞启动
        }
    }

    /** 将普通方块复制品位置记录写回 YAML 文件 */
    private fun save() {
        val cfg = YamlConfiguration()
        cfg.set(
            "blocks",
            plainBlocks.entries.map { "${it.key.world};${it.key.x};${it.key.y};${it.key.z};${it.value.name}" }
        )
        try {
            dataFile.parentFile?.mkdirs()
            cfg.save(dataFile)
        } catch (_: Throwable) {
            // 写入失败时内存记录仍生效，仅影响重启后的恢复
        }
    }
}
