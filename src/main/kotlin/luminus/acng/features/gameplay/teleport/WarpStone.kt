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
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import taboolib.platform.BukkitPlugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 瞬移石核心逻辑（本体 / 复制品）。
 *
 * - 本体：信标 + 青色名称 + 随机唯一 ID + 耐久（默认 100），无复制品称号
 * - 复制品：继承本体 ID + 复制品标记 +「复制品」词条，单次使用
 *
 * 功能：右键向视线方向直线传送 8888 格，给予附魔金苹果等同效果，冷却 10 秒。
 * 展示框复制：本体耐久 -1，掉落复制品。
 */
object WarpStone {

    val ID_KEY = NamespacedKey("2b2tcore", "warp_stone_id")
    val DURABILITY_KEY = NamespacedKey("2b2tcore", "warp_stone_dur")

    private val maxDurability: Int
        get() = config.getInt("warp-stone.durability", 100)

    private val cooldownSeconds: Int
        get() = config.getInt("warp-stone.cooldown", 10)

    private val teleportDistance: Double
        get() = config.getDouble("warp-stone.distance", 8888.0)

    /** 玩家冷却记录：UUID → 上次使用时间戳(ms) */
    private val cooldowns = ConcurrentHashMap<UUID, Long>()

    // ==================== 创建 ====================

    /** 创建瞬移石本体（随机唯一 ID + 满耐久） */
    fun createBody(): ItemStack = createBody(UUID.randomUUID().toString())

    fun createBody(id: String): ItemStack {
        val item = ItemStack(Material.BEACON)
        val meta = item.itemMeta ?: return item
        meta.displayName(net.kyori.adventure.text.Component.text("瞬移石").color(net.kyori.adventure.text.format.NamedTextColor.AQUA))
        meta.persistentDataContainer.set(ID_KEY, PersistentDataType.STRING, id)
        meta.persistentDataContainer.set(DURABILITY_KEY, PersistentDataType.INTEGER, maxDurability)
        meta.isUnbreakable = true
        meta.lore = buildBodyLore(id, maxDurability)
        item.itemMeta = meta
        return item
    }

    /** 从本体生成复制品（继承 ID + 复制品标记，单次使用） */
    fun makeReplica(body: ItemStack): ItemStack {
        val id = getID(body) ?: return body
        val item = ItemStack(Material.BEACON)
        val meta = item.itemMeta ?: return item
        meta.displayName(net.kyori.adventure.text.Component.text("瞬移石").color(net.kyori.adventure.text.format.NamedTextColor.AQUA))
        meta.persistentDataContainer.set(ID_KEY, PersistentDataType.STRING, id)
        meta.isUnbreakable = true
        meta.lore = buildReplicaLore(id)
        item.itemMeta = meta
        return Replica.mark(item)
    }

    // ==================== 查询 ====================

    fun isStone(item: ItemStack?): Boolean {
        if (item == null || item.type != Material.BEACON) return false
        return item.itemMeta?.persistentDataContainer?.has(ID_KEY, PersistentDataType.STRING) == true
    }

    fun isBody(item: ItemStack?): Boolean {
        if (!isStone(item)) return false
        return item!!.itemMeta!!.persistentDataContainer.has(DURABILITY_KEY, PersistentDataType.INTEGER)
    }

    fun isReplicaItem(item: ItemStack?): Boolean {
        return isStone(item) && Replica.isReplica(item)
    }

    fun getID(item: ItemStack): String? {
        return item.itemMeta?.persistentDataContainer?.get(ID_KEY, PersistentDataType.STRING)
    }

    fun getDurability(item: ItemStack): Int {
        return item.itemMeta?.persistentDataContainer?.get(DURABILITY_KEY, PersistentDataType.INTEGER) ?: 0
    }

    /** 消耗 1 点耐久，返回 false 表示已耗尽 */
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

    private fun setCooldown(player: Player) {
        cooldowns[player.uniqueId] = System.currentTimeMillis()
    }

    /** 玩家退出时清理冷却记录，防止内存泄漏 */
    fun cleanupCooldown(player: Player) {
        cooldowns.remove(player.uniqueId)
    }

    // ==================== 瞬移 ====================

    /**
     * 执行瞬移：向视线方向传送 teleportDistance 格 + 附魔金苹果效果。
     * Folia 安全：teleportAsync 回调在目标区域线程执行。
     */
    fun executeWarp(player: Player) {
        val dir = player.location.direction.clone().normalize()
        val target = player.location.clone().add(dir.multiply(teleportDistance))

        // 限制在世界边界内
        val world = player.world
        val border = world.worldBorder
        if (!border.isInside(target)) {
            val center = border.center
            val radius = border.size / 2 - 2
            val dx = target.x - center.x
            val dz = target.z - center.z
            val dist = Math.sqrt(dx * dx + dz * dz)
            if (dist > radius) {
                val scale = radius / dist
                target.x = center.x + dx * scale
                target.z = center.z + dz * scale
            }
        }

        // 确保 Y 在世界高度内，且脚下有方块（+1 站在方块顶部）
        target.y = (world.getHighestBlockYAt(target).toDouble() + 1).coerceIn(
            world.minHeight.toDouble(), (world.maxHeight - 2).toDouble()
        )

        setCooldown(player)

        player.teleportAsync(target).thenAccept { success ->
            if (success) {
                // 附魔金苹果等同效果
                val loc = player.location
                Bukkit.getRegionScheduler().runDelayed(
                    BukkitPlugin.getInstance(),
                    loc,
                    { _ ->
                        player.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, 5 * 20, 1))
                        player.addPotionEffect(PotionEffect(PotionEffectType.ABSORPTION, 2 * 60 * 20, 3))
                        player.addPotionEffect(PotionEffect(PotionEffectType.FIRE_RESISTANCE, 5 * 60 * 20, 0))
                        player.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, 60 * 20, 0))
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
            durability <= maxDurability / 10 -> ChatColor.RED
            durability <= maxDurability / 3 -> ChatColor.YELLOW
            else -> ChatColor.GREEN
        }
        return listOf(
            "${ChatColor.GRAY}唯一 ID: $shortId",
            "${ChatColor.GRAY}耐久: ${durColor}${durability}${ChatColor.GRAY}/${maxDurability}",
            "",
            "${ChatColor.DARK_AQUA}右键瞬移 ${teleportDistance.toInt()} 格",
            "${ChatColor.DARK_AQUA}冷却 ${cooldownSeconds} 秒 + 附魔金苹果效果",
            "${ChatColor.DARK_PURPLE}展示框旋转复制",
        )
    }

    private fun buildReplicaLore(id: String): List<String> {
        val shortId = if (id.length > 8) id.substring(0, 8) else id
        return listOf(
            "${ChatColor.GRAY}唯一 ID: $shortId",
            "${ChatColor.DARK_AQUA}右键瞬移（单次）",
        )
    }
}
