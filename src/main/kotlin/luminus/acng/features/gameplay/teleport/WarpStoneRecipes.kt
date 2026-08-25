package luminus.acng.features.gameplay.teleport

import luminus.acng.Main.config
import luminus.acng.features.gameplay.duplications.Replica
import luminus.acng.msg
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.inventory.CraftingInventory
import org.bukkit.inventory.RecipeChoice
import org.bukkit.inventory.ShapelessRecipe
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.platform.BukkitPlugin

/**
 * 瞬移石配方注册模块。
 *
 * 通用压缩珍珠链：
 * - 9 个普通末影珍珠 → Lv.1 压缩珍珠
 * - 9 个 Lv.(N-1) → Lv.N（N = 2..9）
 * - 9 个 Lv.9 → 满级压缩珍珠
 *
 * 瞬移石：8 个满级压缩珍珠 + 1 个传送核心
 *
 * 合成验证：压缩珍珠本身是复制品（允许合成，不可二次复制），
 * 瞬移石合成时传送核心允许复制品，替换为带新随机 ID 的本体。
 */
object WarpStoneRecipes : Listener {

    private var registered = false

    @Awake(LifeCycle.ENABLE)
    fun onEnable() {
        register()
    }

    fun register() {
        if (registered) return
        if (!config.getBoolean("warp-stone.enable", true)) return
        val plugin = BukkitPlugin.getInstance()

        // ========== 压缩珍珠链：10 级，每级 9 合 1 ==========
        for (level in 1..WarpStoneItems.MAX_LEVEL) {
            val result = WarpStoneItems.createPearl(level)
            val recipe = ShapelessRecipe(
                NamespacedKey("2b2tcore", "compressed_pearl_$level"),
                result
            )
            if (level == 1) {
                // Lv.1：9 个普通末影珍珠
                repeat(9) { recipe.addIngredient(Material.ENDER_PEARL) }
            } else {
                // Lv.2+：9 个上一级压缩珍珠
                val prev = WarpStoneItems.createPearl(level - 1)
                val choice = RecipeChoice.ExactChoice(prev)
                repeat(9) { recipe.addIngredient(choice) }
            }
            Bukkit.addRecipe(recipe)
        }

        // ========== 瞬移石：8 满级压缩珍珠 + 1 传送核心 ==========
        val warpStone = ShapelessRecipe(
            NamespacedKey("2b2tcore", "warp_stone"),
            WarpStone.createBody()
        )
        val maxPearl = WarpStoneItems.createPearl(WarpStoneItems.MAX_LEVEL)
        val pearlChoice = RecipeChoice.ExactChoice(maxPearl)
        repeat(8) { warpStone.addIngredient(pearlChoice) }
        warpStone.addIngredient(RecipeChoice.ExactChoice(TeleportItems.createCore()))
        Bukkit.addRecipe(warpStone)

        // 注册合成验证 + 使用监听器
        plugin.server.pluginManager.registerEvents(this, plugin)
        plugin.server.pluginManager.registerEvents(WarpStoneListener, plugin)

        registered = true
    }

    // ==================== 合成验证 ====================

    @EventHandler
    fun onCraft(event: CraftItemEvent) {
        val inventory = event.inventory as? CraftingInventory ?: return
        val result = inventory.result ?: return
        val player = event.whoClicked as? Player ?: return

        when {
            // 压缩珍珠：确保合成结果带复制品标记（配方 result 模板已通过 Replica.mark 打标）
            WarpStoneItems.isPearl(result) -> { /* 已由 Replica.mark 处理 */ }
            // 瞬移石：合成成功后替换为带新随机 ID 的本体
            WarpStone.isStone(result) -> {
                inventory.result = WarpStone.createBody()
            }
        }
    }
}
