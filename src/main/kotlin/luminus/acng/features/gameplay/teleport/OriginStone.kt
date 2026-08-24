package luminus.acng.features.gameplay.teleport

import luminus.acng.Main.config
import luminus.acng.features.gameplay.duplications.Replica
import luminus.acng.msg
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

/**
 * 起源石核心逻辑（本体 / 复制品）。
 *
 * - 本体：紫菘花 + 品红色名称 + 随机唯一 ID + 耐久（默认 100），无复制品称号
 * - 复制品：继承本体 ID + 复制品标记 +「复制品」词条，单次使用
 *
 * 使用本体（右键）：降低耐久 1
 * 展示框复制：本体耐久 -1，掉落复制品
 * 复制品使用（右键）：消耗，传送到主世界 (0, 319, 0)
 */
object OriginStone {

    /** 起源石唯一 ID 键（本体与复制品共享，用于配对） */
    val ID_KEY = NamespacedKey("2b2tcore", "origin_stone_id")

    /** 本体耐久键 */
    val DURABILITY_KEY = NamespacedKey("2b2tcore", "origin_stone_dur")

    /** 起源石传送目标：主世界 (0, 319, 0) */
    private val DESTINATION: Location
        get() = Location(Bukkit.getWorlds()[0], 0.5, 319.0, 0.5)

    private val maxDurability: Int
        get() = config.getInt("origin-stone.durability", 100)

    // ==================== 创建 ====================

    /** 创建起源石本体（带新随机唯一 ID + 满耐久） */
    fun createBody(): ItemStack = createBody(UUID.randomUUID().toString())

    /** 创建起源石本体（指定 ID + 满耐久，合成时替换用） */
    fun createBody(id: String): ItemStack {
        val item = ItemStack(Material.ALLIUM)
        val meta = item.itemMeta ?: return item
        applyBodyMeta(meta, id)
        item.itemMeta = meta
        return item
    }

    private fun applyBodyMeta(meta: org.bukkit.inventory.meta.ItemMeta, id: String) {
        meta.displayName(net.kyori.adventure.text.Component.text("起源石").color(net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE))
        meta.persistentDataContainer.set(ID_KEY, PersistentDataType.STRING, id)
        meta.persistentDataContainer.set(DURABILITY_KEY, PersistentDataType.INTEGER, maxDurability)
        meta.isUnbreakable = true
        meta.lore = buildBodyLore(id, maxDurability)
    }

    /** 创建复制品（继承 ID，单次使用，带复制品标记） */
    fun makeReplica(body: ItemStack): ItemStack {
        val id = getID(body) ?: return body
        val item = ItemStack(Material.ALLIUM)
        val meta = item.itemMeta ?: return item
        meta.displayName(net.kyori.adventure.text.Component.text("起源石").color(net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE))
        meta.persistentDataContainer.set(ID_KEY, PersistentDataType.STRING, id)
        meta.isUnbreakable = true
        meta.lore = buildReplicaLore(id)
        item.itemMeta = meta
        return Replica.mark(item)
    }

    // ==================== 判断 ====================

    /** 物品是否为起源石（本体或复制品） */
    fun isStone(item: ItemStack?): Boolean {
        if (item == null || item.type != Material.ALLIUM) return false
        return item.itemMeta?.persistentDataContainer?.has(ID_KEY, PersistentDataType.STRING) == true
    }

    /** 物品是否为起源石本体（有耐久键 = 本体） */
    fun isBody(item: ItemStack?): Boolean {
        if (!isStone(item)) return false
        return item!!.itemMeta!!.persistentDataContainer.has(DURABILITY_KEY, PersistentDataType.INTEGER)
    }

    /** 物品是否为起源石复制品（有复制品标记 = 复制品） */
    fun isReplicaItem(item: ItemStack?): Boolean {
        if (!isStone(item)) return false
        return Replica.isReplica(item)
    }

    // ==================== 属性 ====================

    fun getID(item: ItemStack): String? {
        return item.itemMeta?.persistentDataContainer?.get(ID_KEY, PersistentDataType.STRING)
    }

    fun getDurability(item: ItemStack): Int {
        return item.itemMeta?.persistentDataContainer?.get(DURABILITY_KEY, PersistentDataType.INTEGER) ?: 0
    }

    // ==================== 操作 ====================

    /**
     * 尝试消耗本体 1 点耐久（展示框复制或右键使用时调用）。
     * @return true 如果耐久足够并已扣除；false 如果耐久已耗尽。
     */
    fun decreaseDurability(item: ItemStack): Boolean {
        val meta = item.itemMeta ?: return false
        val pdc = meta.persistentDataContainer
        val dur = pdc.get(DURABILITY_KEY, PersistentDataType.INTEGER) ?: return false
        if (dur <= 0) return false
        val newDur = dur - 1
        pdc.set(DURABILITY_KEY, PersistentDataType.INTEGER, newDur)
        meta.lore = buildBodyLore(getID(item)!!, newDur)
        item.itemMeta = meta
        return true
    }

    /**
     * 复制品使用：传送到主世界 (0, 319, 0)，成功则消耗。
     * @return true 如果传送成功（复制品应被消耗）。
     */
    fun consume(player: Player): Boolean {
        val dest = DESTINATION
        if (dest.world == null) return false
        player.teleportAsync(dest)
        return true
    }

    // ==================== 词条 ====================

    private fun buildBodyLore(id: String, durability: Int): List<String> {
        val shortId = if (id.length > 8) id.substring(0, 8) else id
        val durColor = when {
            durability <= maxDurability / 10 -> "${ChatColor.RED}"
            durability <= maxDurability / 3 -> "${ChatColor.YELLOW}"
            else -> "${ChatColor.GREEN}"
        }
        return listOf(
            "${ChatColor.GRAY}唯一 ID: $shortId",
            "${ChatColor.GRAY}耐久: $durColor$durability${ChatColor.GRAY}/${maxDurability}",
            "",
            "${ChatColor.DARK_PURPLE}右键使用降低耐久",
            "${ChatColor.DARK_PURPLE}展示框旋转复制",
        )
    }

    private fun buildReplicaLore(id: String): List<String> {
        val shortId = if (id.length > 8) id.substring(0, 8) else id
        return listOf(
            "${ChatColor.GRAY}唯一 ID: $shortId",
            "",
            "${ChatColor.DARK_PURPLE}右键传送至起源 (0, 319, 0)",
            "${ChatColor.DARK_PURPLE}（单次使用）",
        )
    }
}
