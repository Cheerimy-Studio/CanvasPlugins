package luminus.acng.features.gameplay.teleport

import luminus.acng.Main.config
import luminus.acng.features.gameplay.duplications.Replica
import luminus.acng.msg
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

/**
 * 传送石核心逻辑（本体 / 复制品）。
 *
 * - 本体：钻石 + 黄色名称 + 随机唯一 ID + 耐久（默认 10），无复制品称号
 * - 复制品：继承本体 ID + 复制品标记（2b2tcore:replica）+「复制品」词条，
 *   无耐久，不可二次复制；可「吃掉」传送到持有本体的在线玩家处
 *
 * 传送石只能通过展示框复制（其他复制方式在各自入口拦截），
 * 每次复制一个复制品，本体耐久 -1，耐久耗尽无法再复制。
 */
object TeleportStone {

    /** 传送石唯一 ID 键（本体与复制品共享，用于配对） */
    val ID_KEY = NamespacedKey("2b2tcore", "tp_stone_id")

    /** 本体耐久键 */
    val DURABILITY_KEY = NamespacedKey("2b2tcore", "tp_stone_durability")

    /** 默认最大耐久 */
    private val maxDurability: Int
        get() = config.getInt("teleport.durability", 10)

    /** 复制品词条（复用 duplication.replica.lore 配置，与普通复制品一致） */
    private val replicaLore: String
        get() = ChatColor.translateAlternateColorCodes(
            '&', config.getString("duplication.replica.lore", "&7复制品") ?: "&7复制品"
        )

    /** 生成传送石本体（随机唯一 ID + 满耐久） */
    fun createBody(): ItemStack {
        val item = ItemStack(Material.DIAMOND)
        val meta = item.itemMeta ?: return item
        meta.setDisplayName("§e传送石")
        val id = UUID.randomUUID().toString()
        meta.persistentDataContainer.set(ID_KEY, PersistentDataType.STRING, id)
        meta.persistentDataContainer.set(DURABILITY_KEY, PersistentDataType.INTEGER, maxDurability)
        meta.lore = buildLore(id, maxDurability)
        item.itemMeta = meta
        return item
    }

    /** 从本体生成复制品（继承 ID + 复制品标记 + 复制品词条，无耐久） */
    fun makeReplica(body: ItemStack): ItemStack {
        val replica = body.clone()
        replica.amount = 1
        val meta = replica.itemMeta ?: return replica
        meta.persistentDataContainer.set(Replica.replicaKey, PersistentDataType.BYTE, 1)
        meta.persistentDataContainer.remove(DURABILITY_KEY)
        val id = getId(body) ?: return replica
        meta.lore = mutableListOf("§8#${id.take(8)}", replicaLore)
        replica.itemMeta = meta
        return replica
    }

    /** 是否传送石（本体或复制品） */
    fun isStone(item: ItemStack?): Boolean = hasStringKey(item, ID_KEY)

    /** 是否传送石本体（有 ID 且无复制品标记） */
    fun isBody(item: ItemStack?): Boolean =
        isStone(item) && !hasByteKey(item, Replica.replicaKey)

    /** 是否传送石复制品（有 ID 且有复制品标记） */
    fun isReplica(item: ItemStack?): Boolean =
        isStone(item) && hasByteKey(item, Replica.replicaKey)

    fun getId(item: ItemStack?): String? =
        item?.itemMeta?.persistentDataContainer?.get(ID_KEY, PersistentDataType.STRING)

    fun getDurability(item: ItemStack?): Int {
        if (!isBody(item)) return 0
        return item?.itemMeta?.persistentDataContainer
            ?.getOrDefault(DURABILITY_KEY, PersistentDataType.INTEGER, 0) ?: 0
    }

    /** 本体耐久 -1，返回是否成功（耐久为 0 时返回 false） */
    fun decreaseDurability(item: ItemStack): Boolean {
        if (!isBody(item)) return false
        val current = getDurability(item)
        if (current <= 0) return false
        val meta = item.itemMeta ?: return false
        val newDurability = current - 1
        meta.persistentDataContainer.set(DURABILITY_KEY, PersistentDataType.INTEGER, newDurability)
        val id = getId(item) ?: return false
        meta.lore = buildLore(id, newDurability)
        item.itemMeta = meta
        return true
    }

    /**
     * 吃掉复制品：传送到持有本体的在线玩家处。
     * @return true 表示传送成功（应消耗 1 个复制品）
     */
    fun consume(player: Player, item: ItemStack): Boolean {
        val id = getId(item) ?: return false
        if (!isReplica(item)) return false
        val holder = Bukkit.getOnlinePlayers().firstOrNull { p ->
            // 搜索主背包 + 副手 + 装备栏，确保本体在任何格都能找到
            p.inventory.contents.any { it != null && isBody(it) && getId(it) == id } ||
            p.inventory.itemInOffHand.let { it != null && isBody(it) && getId(it) == id } ||
            p.inventory.armorContents.any { it != null && isBody(it) && getId(it) == id }
        }
        if (holder == null) {
            player.msg("&c持有传送石本体的玩家不在线，无法传送！")
            return false
        }
        player.teleportAsync(holder.location)
        player.msg("&a已传送到 ${holder.name} 身边")
        return true
    }

    private fun buildLore(id: String, durability: Int): List<String> =
        listOf("§7耐久: $durability/$maxDurability", "§8#${id.take(8)}")

    private fun hasStringKey(item: ItemStack?, key: NamespacedKey): Boolean {
        if (item == null || item.type.isAir) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(key, PersistentDataType.STRING)
    }

    private fun hasByteKey(item: ItemStack?, key: NamespacedKey): Boolean {
        if (item == null || item.type.isAir) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(key, PersistentDataType.BYTE)
    }
}
