package luminus.acng.features.gameplay.teleport

import luminus.acng.Main.config
import luminus.acng.features.gameplay.duplications.Replica
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

/** 起源石核心逻辑。本体：紫菘花 + 耐久；复制品：单次传送到主世界 (0,319,0)。 */
object OriginStone {

    val ID_KEY = NamespacedKey("2b2tcore", "origin_stone_id")
    val DURABILITY_KEY = NamespacedKey("2b2tcore", "origin_stone_dur")

    private val maxDurability get() = config.getInt("origin-stone.durability", 100)

    fun createBody(): ItemStack = createBody(UUID.randomUUID().toString())

    fun createBody(id: String): ItemStack {
        val item = ItemStack(Material.ALLIUM)
        val meta = item.itemMeta ?: return item
        meta.displayName(Component.text("起源石").color(NamedTextColor.DARK_PURPLE))
        meta.persistentDataContainer.set(ID_KEY, PersistentDataType.STRING, id)
        meta.persistentDataContainer.set(DURABILITY_KEY, PersistentDataType.INTEGER, maxDurability)
        meta.persistentDataContainer.set(NamespacedKey("itemtag", "placeable"), PersistentDataType.INTEGER, 0)
        meta.persistentDataContainer.set(NamespacedKey("itemtag", "usable"), PersistentDataType.INTEGER, 0)
        meta.isUnbreakable = true
        meta.lore = buildBodyLore(id, maxDurability)
        item.itemMeta = meta
        return item
    }

    fun makeReplica(body: ItemStack): ItemStack {
        val id = getID(body) ?: return body
        val item = ItemStack(Material.ALLIUM)
        val meta = item.itemMeta ?: return item
        meta.displayName(Component.text("起源石").color(NamedTextColor.DARK_PURPLE))
        meta.persistentDataContainer.set(ID_KEY, PersistentDataType.STRING, id)
        meta.persistentDataContainer.set(NamespacedKey("itemtag", "placeable"), PersistentDataType.INTEGER, 0)
        meta.persistentDataContainer.set(NamespacedKey("itemtag", "usable"), PersistentDataType.INTEGER, 0)
        meta.isUnbreakable = true
        meta.lore = buildReplicaLore(id)
        item.itemMeta = meta
        return Replica.mark(item)
    }

    fun isStone(item: ItemStack?): Boolean {
        if (item == null || item.type != Material.ALLIUM) return false
        return item.itemMeta?.persistentDataContainer?.has(ID_KEY, PersistentDataType.STRING) == true
    }

    fun isBody(item: ItemStack?): Boolean {
        if (!isStone(item)) return false
        return item!!.itemMeta!!.persistentDataContainer.has(DURABILITY_KEY, PersistentDataType.INTEGER)
    }

    fun isReplicaItem(item: ItemStack?): Boolean = isStone(item) && Replica.isReplica(item)

    fun getID(item: ItemStack): String? =
        item.itemMeta?.persistentDataContainer?.get(ID_KEY, PersistentDataType.STRING)

    fun getDurability(item: ItemStack): Int =
        item.itemMeta?.persistentDataContainer?.get(DURABILITY_KEY, PersistentDataType.INTEGER) ?: 0

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

    private fun buildBodyLore(id: String, durability: Int): List<String> {
        val shortId = if (id.length > 8) id.substring(0, 8) else id
        val durColor = when {
            durability <= maxDurability / 10 -> "&c"
            durability <= maxDurability / 3 -> "&e"
            else -> "&a"
        }
        return listOf(
            "&7ID: $shortId",
            "&7耐久: $durColor$durability&7/$maxDurability",
            "",
            "&5右键使用",
            "&5展示框旋转复制",
        ).map { it.replace("&", "\u00A7") }
    }

    private fun buildReplicaLore(id: String): List<String> {
        val shortId = if (id.length > 8) id.substring(0, 8) else id
        return listOf(
            "&7ID: $shortId",
            "",
            "&5右键传送至起源 (0, 319, 0)",
            "&7（单次使用）",
        ).map { it.replace("&", "\u00A7") }
    }
}
