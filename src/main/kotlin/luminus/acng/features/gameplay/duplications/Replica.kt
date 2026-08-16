package luminus.acng.features.gameplay.duplications

import luminus.acng.Main.config
import luminus.acng.msg
import org.bukkit.ChatColor
import org.bukkit.Material
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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 复制品标记工具：
 * - 所有复制插件复制出的物品都会被打上「复制品」词条（可见 lore + PDC 标记）
 * - 复制品同时带上 ItemTag 的禁止交互 flag（不可交易/合成/附魔/铁砧/磨石/锻造/死亡消失）
 * - 潜影盒内部物品递归打上同样的「复制品」词条与禁止交互特性
 * - 收纳袋（Bundle）内部物品同样递归打标；潜影盒内物品里的收纳袋也会被打标
 * - 复制品（或内含复制品的容器）不可被二次复制
 * - 拥有 2b2tcore.dupe.original 权限的玩家复制出的物品为原版（不带词条与 flag）
 *
 * 复制品标记写入 ItemStack PersistentDataContainer（2b2tcore:replica，随 NBT 持久保存）。
 * 禁止交互特性复用 ItemTag 的 flag 机制（PDC 键 itemtag:<flag>，INTEGER 0 表示禁止），
 * 由 ItemTag 插件的监听器拦截对应行为；ItemEdit 可直接编辑复制品的 lore 词条。
 */
object Replica {

    /** 复制品 PDC 标记 key */
    val replicaKey = NamespacedKey("2b2tcore", "replica")

    /** 原版复制权限：拥有该权限的玩家复制出的物品不带复制品词条 */
    const val ORIGINAL_PERMISSION = "2b2tcore.dupe.original"

    /** 容器（潜影盒）递归最大层数 */
    private const val MAX_NESTING = 5

    /**
     * ItemTag 禁止交互 flag（复用 ItemTag 的监听器拦截行为）。
     * 布尔 flag 使用 INTEGER 0 表示「禁止」，key 存在即被 ItemTag 拦截。
     * 键格式为 "itemtag:<flag>"。
     */
    private val itemTagFlags = listOf(
        "tradeable",         // 不可与村民交易
        "smithing_table",    // 不可在锻造台使用
        "renamable",         // 不可在铁砧上重命名
        "craft_ingredient",  // 不可用于合成
        "grindable",         // 不可在磨石上分解
        "enchantable",       // 不可在附魔台上二次附魔
        "vanishcurse",       // 死亡时消失（不掉落）
    )

    /**
     * 稀有方块（传送石合成材料等珍贵方块），复制品禁止放置。
     * 普通方块复制品仍允许放置（放置后标记丢失可接受）。
     * 通过 itemtag:placeable flag 由 ItemTag 拦截放置。
     */
    private val rarePlaceBlockMaterials = setOf(
        Material.DRAGON_EGG,       // 龙蛋
        Material.COAL_BLOCK,       // 煤炭块
        Material.IRON_BLOCK,       // 铁块
        Material.GOLD_BLOCK,       // 金块
        Material.LAPIS_BLOCK,      // 青金石块
        Material.DIAMOND_BLOCK,    // 钻石块
        Material.NETHERITE_BLOCK,  // 下界合金块
        Material.EMERALD_BLOCK,    // 绿宝石块
        Material.REDSTONE_BLOCK,   // 红石块
    )

    /** 复制品词条（lore），支持 & 颜色代码 */
    private val loreLine: String
        get() = ChatColor.translateAlternateColorCodes(
            '&', config.getString("duplication.replica.lore", "&7复制品") ?: "&7复制品"
        )

    private val plainLoreLine: String
        get() = ChatColor.stripColor(loreLine) ?: "复制品"

    private fun enabled(): Boolean = config.getBoolean("duplication.replica.enable", true)

    /** 是否写入 ItemTag 禁止交互 flag（需安装 ItemTag 插件） */
    private fun itemTagFlagsEnabled(): Boolean =
        config.getBoolean("duplication.replica.itemtag-flags", true)

    /** 判断物品是否为复制品（PDC 标记 + lore 词条兜底） */
    fun isReplica(item: ItemStack?): Boolean {
        if (item == null || item.type.isAir) return false
        val meta = item.itemMeta ?: return false
        if (meta.persistentDataContainer.has(replicaKey, PersistentDataType.BYTE)) return true
        val lore = meta.lore ?: return false
        return lore.any { ChatColor.stripColor(it) == plainLoreLine }
    }

    /** 判断物品或其内部（潜影盒嵌套）是否含有复制品 */
    fun containsReplica(item: ItemStack?): Boolean = containsReplica(item, 0)

    private fun containsReplica(item: ItemStack?, depth: Int): Boolean {
        if (item == null || item.type.isAir) return false
        if (isReplica(item)) return true
        if (depth >= MAX_NESTING) return false
        val meta = item.itemMeta
        if (meta is BlockStateMeta && meta.blockState is ShulkerBox) {
            val inventory = (meta.blockState as ShulkerBox).inventory
            inventory.contents.forEach { content ->
                if (content != null && !content.type.isAir && containsReplica(content, depth + 1)) {
                    return true
                }
            }
        }
        // 收纳袋（Bundle）内部物品
        if (meta is BundleMeta) {
            meta.items.forEach { content ->
                if (!content.type.isAir && containsReplica(content, depth + 1)) {
                    return true
                }
            }
        }
        return false
    }

    /** 判断方块状态是否为复制品（放置的复制品潜影盒，PDC 随方块实体保留时有效） */
    fun isReplicaBlock(state: BlockState): Boolean {
        val pdc = (state as? TileState)?.persistentDataContainer ?: return false
        return pdc.has(replicaKey, PersistentDataType.BYTE)
    }

    /** 判断潜影盒方块或其内部是否含有复制品 */
    fun blockContainsReplica(state: BlockState): Boolean {
        if (isReplicaBlock(state)) return true
        if (state is ShulkerBox) {
            state.inventory.contents.forEach { content ->
                if (content != null && !content.type.isAir && containsReplica(content)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * 给物品打上复制品标记（幂等）：
     * - 复制品 PDC 标记 + 「复制品」词条
     * - ItemTag 禁止交互 flag
     * - 潜影盒内部物品递归打标
     */
    fun mark(item: ItemStack): ItemStack = mark(item, 0)

    private fun mark(item: ItemStack, depth: Int): ItemStack {
        val meta = item.itemMeta ?: return item
        applyReplicaMeta(meta)

        // 稀有方块：额外打 placeable flag，禁止放置（依赖 ItemTag 拦截）
        if (itemTagFlagsEnabled() && item.type in rarePlaceBlockMaterials) {
            meta.persistentDataContainer.set(NamespacedKey("itemtag", "placeable"), PersistentDataType.INTEGER, 0)
        }

        // 潜影盒内部物品递归打标
        if (depth < MAX_NESTING && meta is BlockStateMeta) {
            val state = meta.blockState
            if (state is ShulkerBox) {
                val inventory = state.inventory
                for (i in inventory.contents.indices) {
                    val content = inventory.getItem(i) ?: continue
                    if (content.type.isAir) continue
                    inventory.setItem(i, mark(content, depth + 1))
                }
                meta.blockState = state
            }
        }
        // 收纳袋（Bundle）内部物品递归打标
        if (depth < MAX_NESTING && meta is BundleMeta) {
            val items = meta.items
            if (items.isNotEmpty()) {
                meta.setItems(items.map { mark(it, depth + 1) })
            }
        }

        item.itemMeta = meta
        return item
    }

    /** 给 ItemMeta 打上复制品标记 + ItemTag 禁止交互 flag + 「复制品」词条 */
    private fun applyReplicaMeta(meta: ItemMeta) {
        meta.persistentDataContainer.set(replicaKey, PersistentDataType.BYTE, 1.toByte())
        if (itemTagFlagsEnabled()) {
            itemTagFlags.forEach { flag ->
                meta.persistentDataContainer.set(NamespacedKey("itemtag", flag), PersistentDataType.INTEGER, 0)
            }
        }
        val line = loreLine
        val lore = meta.lore?.toMutableList() ?: mutableListOf()
        if (lore.none { ChatColor.stripColor(it) == plainLoreLine }) {
            lore.add(line)
        }
        meta.lore = lore
    }

    /**
     * 生成复制输出物品：
     * 玩家拥有 2b2tcore.dupe.original 权限 → 原版（不标记）；否则打上复制品标记。
     */
    fun output(player: Player?, item: ItemStack): ItemStack {
        if (enabled() && (player == null || !player.hasPermission(ORIGINAL_PERMISSION))) {
            mark(item)
        }
        return item
    }

    /**
     * 复制源检查：复制品（或内含复制品的容器）不可被二次复制。
     * 返回 true 表示已拦截（并已向玩家发送提示）。
     */
    fun checkCanDupe(player: Player?, item: ItemStack?): Boolean {
        if (!enabled()) return false
        if (!containsReplica(item)) return false
        deny(player)
        return true
    }

    /** 玩家 UUID -> 上次收到拦截提示的时间（毫秒），用于提示冷却 */
    private val denyCooldowns = ConcurrentHashMap<UUID, Long>()

    private fun denyCooldownMillis(): Long =
        config.getLong("duplication.replica.deny-cooldown-seconds", 60) * 1000

    /** 发送复制品拦截提示（每名玩家间隔 deny-cooldown-seconds 秒内最多提醒一次） */
    fun deny(player: Player?) {
        if (player == null) return
        val now = System.currentTimeMillis()
        val last = denyCooldowns.put(player.uniqueId, now)
        if (last != null && now - last < denyCooldownMillis()) return
        player.msg(config.getString("messages.deny-redupe", "&c复制品无法再次复制！") ?: "&c复制品无法再次复制！")
    }
}
