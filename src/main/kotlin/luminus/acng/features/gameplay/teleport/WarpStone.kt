package luminus.acng.features.gameplay.teleport

import luminus.acng.Main.config
import luminus.acng.features.gameplay.duplications.Replica
import luminus.acng.msg
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import taboolib.platform.BukkitPlugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** 瞬移石核心逻辑。本体：信标 + 耐久（默认 20）；复制品：单次瞬移 8888 格 + 附魔金苹果效果。 */
object WarpStone {

    val ID_KEY = NamespacedKey("2b2tcore", "warp_stone_id")
    val DURABILITY_KEY = NamespacedKey("2b2tcore", "warp_stone_dur")

    private val maxDurability get() = config.getInt("warp-stone.durability", 20)
    private val cooldownSeconds get() = config.getInt("warp-stone.cooldown", 10)
    private val teleportDistance get() = config.getDouble("warp-stone.distance", 8888.0)

    private val cooldowns = ConcurrentHashMap<UUID, Long>()

    fun createBody(): ItemStack = createBody(UUID.randomUUID().toString())

    fun createBody(id: String): ItemStack {
        val item = ItemStack(Material.BEACON)
        val meta = item.itemMeta ?: return item
        meta.displayName(Component.text("瞬移石").color(NamedTextColor.AQUA))
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
        val item = ItemStack(Material.BEACON)
        val meta = item.itemMeta ?: return item
        meta.displayName(Component.text("瞬移石").color(NamedTextColor.AQUA))
        meta.persistentDataContainer.set(ID_KEY, PersistentDataType.STRING, id)
        meta.persistentDataContainer.set(NamespacedKey("itemtag", "placeable"), PersistentDataType.INTEGER, 0)
        meta.persistentDataContainer.set(NamespacedKey("itemtag", "usable"), PersistentDataType.INTEGER, 0)
        meta.isUnbreakable = true
        meta.lore = buildReplicaLore(id)
        item.itemMeta = meta
        return Replica.mark(item)
    }

    fun isStone(item: ItemStack?): Boolean {
        if (item == null || item.type != Material.BEACON) return false
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

    // ==================== 冷却 ====================

    fun isOnCooldown(player: Player): Boolean {
        val lastUse = cooldowns[player.uniqueId] ?: return false
        return System.currentTimeMillis() - lastUse < cooldownSeconds * 1000L
    }

    fun getRemainingCooldown(player: Player): Int {
        val lastUse = cooldowns[player.uniqueId] ?: return 0
        val remaining = cooldownSeconds * 1000L - (System.currentTimeMillis() - lastUse)
        return if (remaining > 0) (remaining / 1000).toInt() + 1 else 0
    }

    fun setCooldown(player: Player) {
        cooldowns[player.uniqueId] = System.currentTimeMillis()
    }

    fun cleanupCooldown(player: Player) {
        cooldowns.remove(player.uniqueId)
    }

    // ==================== 瞬移 ====================

    /** 执行瞬移 + 附魔金苹果效果。Folia 安全。XZ 直线传送，Y 保持不变。 */
    fun executeWarp(player: Player) {
        val loc = player.location
        val dir = loc.direction.clone()
        dir.y = 0.0
        if (dir.lengthSquared() < 0.001) dir.x = 1.0 // 垂直看天/脚下时兜底随机方向
        dir.normalize()
        val target = loc.clone().add(dir.multiply(teleportDistance))
        target.y = loc.y

        setCooldown(player)

        player.teleportAsync(target).thenAccept { success ->
            if (success) {
                val loc = player.location
                Bukkit.getRegionScheduler().runDelayed(
                    BukkitPlugin.getInstance(), loc,
                    { _ ->
                        player.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, 5 * 20, 1))
                        player.addPotionEffect(PotionEffect(PotionEffectType.ABSORPTION, 2 * 60 * 20, 3))
                        player.addPotionEffect(PotionEffect(PotionEffectType.FIRE_RESISTANCE, 5 * 60 * 20, 0))
                        player.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, 1 * 60 * 20, 0))
                    },
                    1L
                )
            }
        }
    }

    // ==================== Lore ====================

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
            "&b冷却: ${cooldownSeconds}秒",
        ).map { it.replace("&", "\u00A7") }
    }

    private fun buildReplicaLore(id: String): List<String> {
        val shortId = if (id.length > 8) id.substring(0, 8) else id
        return listOf(
            "&7ID: $shortId",
        ).map { it.replace("&", "\u00A7") }
    }
}
