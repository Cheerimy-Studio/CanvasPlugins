package luminus.acng.features.gameplay.teleport

import luminus.acng.features.gameplay.duplications.Replica
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/**
 * 压缩珍珠通用物品工厂。
 *
 * 合成链：普通末影珍珠 → Lv.1 → Lv.2 → ... → Lv.9 → 满级
 * 每级 9 合 1，合成出来即为复制品（允许作为合成材料，不可二次复制）。
 * 紫菘花颜色体系：黄色 uncommon 名称 + 附魔特效。
 *
 * 通用注册模块设计：[createPearl] + [isPearl] + [getLevel] 可复用于任意压缩物品链。
 */
object WarpStoneItems {

    /** 压缩物品 PDC 标识键 */
    val PEARL_KEY = NamespacedKey("2b2tcore", "compressed_pearl")

    /** 压缩等级键 */
    val LEVEL_KEY = NamespacedKey("2b2tcore", "pearl_level")

    /** 最大等级（满级） */
    const val MAX_LEVEL = 10

    /** 等级显示名称 */
    fun levelName(level: Int): String = when (level) {
        MAX_LEVEL -> "§6满级"
        else -> "§eLv.$level"
    }

    // ==================== 创建 ====================

    /**
     * 创建指定等级的压缩珍珠（自动打复制品标记）。
     * 通用工厂方法：仅需传入等级，PDC + 词条 + 复制品标记全自动。
     */
    fun createPearl(level: Int): ItemStack {
        val item = ItemStack(Material.ENDER_PEARL)
        val meta = item.itemMeta ?: return item
        meta.displayName(net.kyori.adventure.text.Component.text("${levelName(level)} 压缩珍珠"))
        meta.setEnchantmentGlintOverride(true)
        meta.persistentDataContainer.set(PEARL_KEY, PersistentDataType.BYTE, 1)
        meta.persistentDataContainer.set(LEVEL_KEY, PersistentDataType.INTEGER, level)
        // ItemTag flags: 禁止放置/使用（防止被当作末影珍珠扔出）
        meta.persistentDataContainer.set(NamespacedKey("itemtag", "placeable"), PersistentDataType.INTEGER, 0)
        meta.persistentDataContainer.set(NamespacedKey("itemtag", "usable"), PersistentDataType.INTEGER, 0)
        meta.lore = listOf(
            "${ChatColor.GRAY}等级: ${levelName(level)}"
        )
        item.itemMeta = meta
        return Replica.mark(item)
    }

    /** 生成所有等级模板（供配方注册用） */
    fun allPearls(): Map<Int, ItemStack> =
        (1..MAX_LEVEL).associateWith { createPearl(it) }

    // ==================== 查询 ====================

    /** 是否为压缩珍珠 */
    fun isPearl(item: ItemStack?): Boolean {
        if (item == null || item.type != Material.ENDER_PEARL) return false
        return item.itemMeta?.persistentDataContainer?.has(PEARL_KEY, PersistentDataType.BYTE) == true
    }

    /** 获取压缩等级（非压缩珍珠返回 0） */
    fun getLevel(item: ItemStack?): Int {
        if (item == null) return 0
        return item.itemMeta?.persistentDataContainer?.get(LEVEL_KEY, PersistentDataType.INTEGER) ?: 0
    }

    /** 是否为指定等级的压缩珍珠 */
    fun isPearlLevel(item: ItemStack?, level: Int): Boolean =
        isPearl(item) && getLevel(item) == level

    /** 获取上一级珍珠模板（用于配方注册），level=1 时返回 null（原料为普通珍珠） */
    fun prevPearl(level: Int): ItemStack? =
        if (level <= 1) null else createPearl(level - 1)
}
