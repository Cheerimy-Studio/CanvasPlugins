package luminus.acng.features.gameplay.duplications

import luminus.acng.Main.config
import org.bukkit.Material
import org.bukkit.block.TileState
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockCookEvent
import org.bukkit.event.block.BlockDropItemEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.inventory.FurnaceSmeltEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.persistence.PersistentDataType
import taboolib.common.platform.event.SubscribeEvent

/**
 * 复制品方块持久化 + 产物继承监听器。
 *
 * 1. 方块持久化（BlockPlaceEvent + BlockDropItemEvent）：
 *    - TileState 方块（潜影盒/熔炉/木桶等）：放置时 PDC 写入方块（随区块持久化，重启不丢），
 *      掉落时恢复标记
 *    - 普通方块（钻石块/海绵/书架等无 TileState）：放置时坐标写入内存 Map，
 *      掉落时查 Map 命中则恢复标记。⚠️ 重启后 Map 清空，重启前放置的普通方块挖起变原版
 *
 * 2. 产物继承：
 *    - 熔炉/烟熏器/高炉烧炼（FurnaceSmeltEvent）：源材料是复制品 → 产物继承标记
 *    - 营火烹饪（BlockCookEvent）：同上
 *    - 工具改变方块（PlayerInteractEvent：锄头/铲子/斧头等）：工具是复制品 →
 *      改变后的方块坐标写入内存 Map，挖起掉落物继承标记
 *
 * 独立于 mine-and-place 功能，只要 replica.enable=true 即生效。
 */
object ReplicaBlockListener : Listener {

    private fun enabled(): Boolean = config.getBoolean("duplication.replica.enable", true)

    /** 放置复制品时写入方块 PDC（TileState）或内存 Map（普通方块） */
    @SubscribeEvent
    fun onPlace(event: BlockPlaceEvent) {
        if (!enabled()) return
        val item = event.itemInHand
        if (!Replica.isReplica(item)) return

        val block = event.blockPlaced
        val state = block.state
        if (state is TileState) {
            // TileState 方块：PDC 写入方块，随区块数据持久化
            state.persistentDataContainer.set(Replica.replicaKey, PersistentDataType.BYTE, 1.toByte())
            state.update()
        } else {
            // 普通方块：坐标写入内存 Map（无存储文件）
            Replica.recordBlockLocation(block.world.name, block.x, block.y, block.z)
        }
    }

    /** 方块掉落时恢复复制品标记（TileState PDC 命中 或 内存 Map 命中） */
    @SubscribeEvent
    fun onDrop(event: BlockDropItemEvent) {
        if (!enabled()) return
        val block = event.block
        val replicated = Replica.isReplicaBlock(event.blockState) ||
            Replica.isRecordedBlock(block.world.name, block.x, block.y, block.z)
        if (!replicated) return
        event.items.forEach { entity ->
            entity.itemStack = Replica.mark(entity.itemStack)
        }
    }

    /** 熔炉/烟熏器/高炉烧炼产物继承：源材料是复制品 → 产物继承标记 */
    @SubscribeEvent
    fun onSmelt(event: FurnaceSmeltEvent) {
        if (!enabled()) return
        val result = event.result ?: return
        if (Replica.isReplica(event.source)) {
            event.result = Replica.mark(result)
        }
    }

    /** 营火烹饪产物继承：源食物是复制品 → 产物继承标记 */
    @SubscribeEvent
    fun onCook(event: BlockCookEvent) {
        if (!enabled()) return
        val result = event.result ?: return
        if (Replica.isReplica(event.source)) {
            event.result = Replica.mark(result)
        }
    }

    /**
     * 工具改变方块继承：锄头/铲子/斧头等复制品工具右键改变方块时，
     * 把目标方块坐标写入内存 Map，挖起掉落物继承标记。
     *
     * 覆盖：锄头（草/土→耕地、砂土→泥土）、铲子（草→草径、泥土→草径）、
     * 斧头（去皮原木/去皮木头、铜块去氧化）等。
     */
    @EventHandler
    fun onToolUse(event: PlayerInteractEvent) {
        if (!enabled()) return
        if (event.action != Action.RIGHT_CLICK_BLOCK) return

        val tool = event.item ?: return
        if (!Replica.isReplica(tool)) return

        val clicked = event.clickedBlock ?: return
        // 只有「工具 × 目标方块」组合真正改变地形时才记录，避免误标普通方块
        if (!isModifiableBy(tool.type, clicked.type)) return

        Replica.recordBlockLocation(clicked.world.name, clicked.x, clicked.y, clicked.z)
    }

    /** 判断「工具 × 目标方块」组合是否会真正改变地形/方块类型 */
    private fun isModifiableBy(tool: Material, block: Material): Boolean {
        val t = tool.name
        val b = block.name
        return when {
            t.endsWith("_HOE") -> b in HOE_TARGETS
            t.endsWith("_SHOVEL") -> b in SHOVEL_TARGETS
            t.endsWith("_AXE") -> (b.contains("LOG") || b.contains("WOOD")) && !b.startsWith("STRIPPED_")
            else -> false
        }
    }

    /** 锄头可改变的目标方块（草/土/草径 → 耕地、砂土/缠根泥土 → 泥土） */
    private val HOE_TARGETS = setOf(
        "GRASS_BLOCK", "DIRT", "DIRT_PATH", "COARSE_DIRT", "ROOTED_DIRT"
    )

    /** 铲子可改变的目标方块（草/土/灰化土/菌丝 → 草径） */
    private val SHOVEL_TARGETS = setOf(
        "GRASS_BLOCK", "DIRT", "PODZOL", "MYCELIUM"
    )
}
