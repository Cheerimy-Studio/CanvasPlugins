package luminus.acng.features.gameplay.teleport

import luminus.acng.Main.config
import luminus.acng.features.gameplay.duplications.Replica
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType

/**
 * 传送碎片 / 传送核心 物品工厂。
 *
 * 合成链：传送碎片 → 传送核心 → 传送石
 * - 传送碎片（龙蛋 / 紫色斜体 / 附魔特效）
 * - 传送核心（下界之星 / 黄色斜体 / 附魔特效）
 *
 * 两者复用 Replica 的复制品标记（2b2tcore:replica + 「复制品」词条），
 * 因此会被复制系统识别为复制品、不可被二次复制；但**不**写入 craft_ingredient flag，
 * 因此仍可作为合成材料（传送碎片→核心、传送核心→传送石）。
 *
 * 两者额外写入 placeable / usable flag，不可放置、不可使用，只能待在背包内移动。
 */
object TeleportItems {

    val SHARD_KEY = NamespacedKey("2b2tcore", "teleport_shard")
    val CORE_KEY = NamespacedKey("2b2tcore", "teleport_core")

    /** 复制品词条（复用 duplication.replica.lore 配置，与普通复制品一致） */
    private val replicaLore: String
        get() = ChatColor.translateAlternateColorCodes(
            '&', config.getString("duplication.replica.lore", "&7复制品") ?: "&7复制品"
        )

    /** 传送碎片：龙蛋 + 紫色斜体名称 + 附魔特效 + 复制品标记 + 不能放置/使用 */
    fun createShard(): ItemStack {
        val item = ItemStack(Material.DRAGON_EGG)
        val meta = item.itemMeta ?: return item
        meta.setEnchantmentGlintOverride(true)
        meta.setDisplayName("§d§o传送碎片")
        meta.persistentDataContainer.set(SHARD_KEY, PersistentDataType.BYTE, 1)
        applyTeleportMarking(meta)
        item.itemMeta = meta
        return item
    }

    /** 传送核心：下界之星 + 黄色斜体名称 + 附魔特效 + 复制品标记 + 不能放置/使用 */
    fun createCore(): ItemStack {
        val item = ItemStack(Material.NETHER_STAR)
        val meta = item.itemMeta ?: return item
        meta.setEnchantmentGlintOverride(true)
        meta.setDisplayName("§e§o传送核心")
        meta.persistentDataContainer.set(CORE_KEY, PersistentDataType.BYTE, 1)
        applyTeleportMarking(meta)
        item.itemMeta = meta
        return item
    }

    /**
     * 给传送碎片/核心打上复制品标记 + 「复制品」词条 + placeable/usable flag。
     */
    private fun applyTeleportMarking(meta: ItemMeta) {
        // 复制品 PDC 标记（复用 Replica.replicaKey，复制系统据此拦截二次复制）
        meta.persistentDataContainer.set(Replica.replicaKey, PersistentDataType.BYTE, 1)
        // 「复制品」词条（与普通复制品一致）
        val lore = meta.lore?.toMutableList() ?: mutableListOf()
        val plain = ChatColor.stripColor(replicaLore) ?: "复制品"
        if (lore.none { ChatColor.stripColor(it) == plain }) {
            lore.add(replicaLore)
        }
        meta.lore = lore
        // 不可放置、不可使用
        meta.persistentDataContainer.set(NamespacedKey("itemtag", "placeable"), PersistentDataType.INTEGER, 0)
        meta.persistentDataContainer.set(NamespacedKey("itemtag", "usable"), PersistentDataType.INTEGER, 0)
    }

    fun isShard(item: ItemStack?): Boolean = hasKey(item, SHARD_KEY)
    fun isCore(item: ItemStack?): Boolean = hasKey(item, CORE_KEY)

    private fun hasKey(item: ItemStack?, key: NamespacedKey): Boolean {
        if (item == null || item.type.isAir) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(key, PersistentDataType.BYTE)
    }
}
