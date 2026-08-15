package luminus.acng.features.gameplay.teleport

import luminus.acng.Main.config
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.RecipeChoice
import org.bukkit.inventory.ShapelessRecipe
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.platform.BukkitPlugin

/**
 * 传送石道具合成配方注册。
 *
 * - 传送碎片（无序）：龙蛋 + 煤炭块/铁块/金块/青金石块/钻石块/下界合金块/绿宝石块/红石块
 * - 传送核心（无序）：9 个传送碎片
 * - 传送石（无序）：传送核心 + 潮涌核心/海洋之心/沉重核心/鞘翅/末影珍珠/三种蛙明灯
 *
 * 传送石配方 result 用模板（含随机 ID），实际合成时由 TeleportListener
 * 替换为带新随机唯一 ID 的传送石本体。
 */
object TeleportRecipes {

    private var registered = false

    @Awake(LifeCycle.ENABLE)
    fun onEnable() {
        register()
    }

    /** 幂等注册配方 + 合成验证/使用监听器（reload 后可再次调用） */
    fun register() {
        if (registered) return
        if (!config.getBoolean("teleport.enable", true)) return
        val plugin = BukkitPlugin.getInstance()

        // 传送碎片
        val shard = ShapelessRecipe(NamespacedKey("2b2tcore", "teleport_shard"), TeleportItems.createShard())
        shard.addIngredient(Material.DRAGON_EGG)
        shard.addIngredient(Material.COAL_BLOCK)
        shard.addIngredient(Material.IRON_BLOCK)
        shard.addIngredient(Material.GOLD_BLOCK)
        shard.addIngredient(Material.LAPIS_BLOCK)
        shard.addIngredient(Material.DIAMOND_BLOCK)
        shard.addIngredient(Material.NETHERITE_BLOCK)
        shard.addIngredient(Material.EMERALD_BLOCK)
        shard.addIngredient(Material.REDSTONE_BLOCK)
        Bukkit.addRecipe(shard)

        // 传送核心：9 个传送碎片
        val core = ShapelessRecipe(NamespacedKey("2b2tcore", "teleport_core"), TeleportItems.createCore())
        val shardChoice = RecipeChoice.ExactChoice(TeleportItems.createShard())
        repeat(9) { core.addIngredient(shardChoice) }
        Bukkit.addRecipe(core)

        // 传送石：传送核心 + 8 种材料（result 为模板，合成时替换新 ID）
        val stone = ShapelessRecipe(NamespacedKey("2b2tcore", "teleport_stone"), TeleportStone.createBody())
        stone.addIngredient(RecipeChoice.ExactChoice(TeleportItems.createCore()))
        stone.addIngredient(Material.CONDUIT)
        stone.addIngredient(Material.HEART_OF_THE_SEA)
        stone.addIngredient(Material.HEAVY_CORE)
        stone.addIngredient(Material.ELYTRA)
        stone.addIngredient(Material.ENDER_PEARL)
        stone.addIngredient(Material.PEARLESCENT_FROGLIGHT)
        stone.addIngredient(Material.OCHRE_FROGLIGHT)
        stone.addIngredient(Material.VERDANT_FROGLIGHT)
        Bukkit.addRecipe(stone)

        // 合成验证 + 吃掉使用监听器
        plugin.server.pluginManager.registerEvents(TeleportListener, plugin)
        plugin.server.pluginManager.registerEvents(TeleportStoneListener, plugin)
        registered = true
    }
}
