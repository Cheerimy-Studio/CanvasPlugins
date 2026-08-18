package luminus.acng.features.gameplay.duplications

import luminus.acng.Main.config
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.TileState
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBurnEvent
import org.bukkit.event.block.BlockCookEvent
import org.bukkit.event.block.BlockDropItemEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockFadeEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.block.LeavesDecayEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.inventory.FurnaceSmeltEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.persistence.PersistentDataType
import taboolib.common.platform.event.SubscribeEvent

/**
 * 复制品方块持久化 + 产物继承 + 坐标同步监听器。
 *
 * 1. 方块持久化（BlockPlaceEvent + BlockDropItemEvent）：
 *    - TileState 方块（潜影盒/熔炉/木桶等）：放置时 PDC 写入方块（随区块持久化，重启不丢），
 *      掉落时恢复标记
 *    - 普通方块（钻石块/海绵/书架等无 TileState）：放置时坐标写入内存 + 硬盘缓存
 *      （分世界分区块，重启不丢），掉落时恢复标记
 *    - 非复制品放置时自动清理同位置的陈旧记录（防止爆炸/燃烧等残留导致误标）
 *
 * 2. 产物继承：
 *    - 熔炉/烟熏器/高炉烧炼（FurnaceSmeltEvent）：源材料是复制品 → 产物继承标记
 *    - 营火烹饪（BlockCookEvent）：同上
 *    - 工具改变方块（PlayerInteractEvent：锄头/铲子/斧头等）：工具是复制品 →
 *      改变后的方块坐标写入跟踪，挖起掉落物继承标记
 *
 * 3. 坐标同步（活塞推动/方块销毁）：
 *    - 活塞推动/拉回（BlockPistonExtendEvent/RetractEvent）：迁移坐标跟踪
 *    - 爆炸（EntityExplodeEvent/BlockExplodeEvent）：清除被毁方块的跟踪
 *    - 燃烧（BlockBurnEvent）：清除跟踪
 *    - 褪色（BlockFadeEvent）：消失（空气/水）清除跟踪；原地变形保持跟踪
 *    - 落叶（LeavesDecayEvent）：清除跟踪
 *    - 形成/生长/扩散（BlockFormEvent/BlockGrowEvent/BlockSpreadEvent）：
 *      原地变形坐标不变，跟踪自动保持；新位置不继承，无需处理
 *
 * 独立于 mine-and-place 功能，只要 replica.enable=true 即生效。
 */
object ReplicaBlockListener {

    private fun enabled(): Boolean = config.getBoolean("duplication.replica.enable", true)

    // ==================== 方块持久化 ====================

    /** 放置复制品时写入方块 PDC（TileState）或内存+硬盘缓存（普通方块）；
     *  放置非复制品时清理同位置陈旧记录 */
    @SubscribeEvent
    fun onPlace(event: BlockPlaceEvent) {
        if (!enabled()) return
        val item = event.itemInHand
        val block = event.blockPlaced

        if (Replica.isReplica(item)) {
            val state = block.state
            if (state is TileState) {
                state.persistentDataContainer.set(Replica.replicaKey, PersistentDataType.BYTE, 1.toByte())
                state.update()
            } else {
                Replica.recordBlockLocation(block.world.name, block.x, block.y, block.z)
            }
        } else {
            // 非复制品放置：清理同位置陈旧跟踪（爆炸/燃烧残留等）
            Replica.forgetBlockLocation(block.world.name, block.x, block.y, block.z)
        }
    }

    /** 方块掉落时恢复复制品标记（TileState PDC 命中 或 内存跟踪命中） */
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

    // ==================== 产物继承 ====================

    /** 熔炉/烟熏器/高炉烧炼产物继承 */
    @SubscribeEvent
    fun onSmelt(event: FurnaceSmeltEvent) {
        if (!enabled()) return
        val result = event.result ?: return
        if (Replica.isReplica(event.source)) {
            event.result = Replica.mark(result)
        }
    }

    /** 营火烹饪产物继承 */
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
     * 把目标方块坐标写入跟踪，挖起掉落物继承标记。
     *
     * 覆盖：锄头（草/土→耕地、砂土→泥土）、铲子（草→草径）、
     * 斧头（原木/木头→去皮）等。
     */
    @SubscribeEvent
    fun onToolUse(event: PlayerInteractEvent) {
        if (!enabled()) return
        if (event.action != Action.RIGHT_CLICK_BLOCK) return

        val tool = event.item ?: return
        if (!Replica.isReplica(tool)) return

        val clicked = event.clickedBlock ?: return
        if (!isModifiableBy(tool.type, clicked.type)) return

        Replica.recordBlockLocation(clicked.world.name, clicked.x, clicked.y, clicked.z)
    }

    // ==================== 坐标同步 ====================

    /** 活塞推动：迁移所有被推方块的坐标跟踪 */
    @SubscribeEvent
    fun onPistonExtend(event: BlockPistonExtendEvent) {
        if (!enabled() || event.isCancelled) return
        handlePistonMove(event.blocks, event.direction)
    }

    /** 活塞拉回：迁移所有被拉方块的坐标跟踪 */
    @SubscribeEvent
    fun onPistonRetract(event: BlockPistonRetractEvent) {
        if (!enabled() || event.isCancelled) return
        handlePistonMove(event.blocks, event.direction)
    }

    private fun handlePistonMove(blocks: List<Block>, direction: BlockFace) {
        blocks.forEach { block ->
            val target = block.getRelative(direction)
            Replica.moveBlockLocation(
                block.world.name, block.x, block.y, block.z,
                target.x, target.y, target.z
            )
        }
    }

    /** 爆炸（TNT/苦力怕等）：清除被毁方块的跟踪 */
    @SubscribeEvent
    fun onEntityExplode(event: EntityExplodeEvent) {
        if (!enabled() || event.isCancelled) return
        cleanupBlocks(event.blockList())
    }

    /** 方块爆炸（床/重生锚）：清除被毁方块的跟踪 */
    @SubscribeEvent
    fun onBlockExplode(event: BlockExplodeEvent) {
        if (!enabled() || event.isCancelled) return
        cleanupBlocks(event.blockList())
    }

    private fun cleanupBlocks(blocks: List<Block>) {
        blocks.forEach { block ->
            Replica.forgetBlockLocation(block.world.name, block.x, block.y, block.z)
        }
    }

    /** 燃烧：清除被毁方块的跟踪 */
    @SubscribeEvent
    fun onBurn(event: BlockBurnEvent) {
        if (!enabled() || event.isCancelled) return
        Replica.forgetBlockLocation(event.block.world.name, event.block.x, event.block.y, event.block.z)
    }

    /**
     * 褪色（草→泥土、菌丝→灰化土、冰→水、雪融化等）。
     *
     * 如果新方块消失（空气、水、岩浆），清除跟踪。
     * 如果原地变形（farmland→dirt 等），坐标不变，跟踪保持，挖起掉落物仍带标记。
     */
    @SubscribeEvent
    fun onFade(event: BlockFadeEvent) {
        if (!enabled() || event.isCancelled) return
        val newType = event.newState.type
        if (newType.isAir || newType == Material.WATER || newType == Material.LAVA) {
            Replica.forgetBlockLocation(event.block.world.name, event.block.x, event.block.y, event.block.z)
        }
    }

    /**
     * 方块形成/生长/扩散：原地变形（混凝土硬化、作物生长）坐标不变，跟踪自动保持；
     * 新位置形成（水结冰、草扩散）不继承标记，无需处理。
     *
     * 不注册为事件监听器（无 @SubscribeEvent），仅保留方法签名供文档引用。
     */

    /** 落叶：清除跟踪 */
    @SubscribeEvent
    fun onDecay(event: LeavesDecayEvent) {
        if (!enabled() || event.isCancelled) return
        Replica.forgetBlockLocation(event.block.world.name, event.block.x, event.block.y, event.block.z)
    }

    // ==================== 工具判定 ====================

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
