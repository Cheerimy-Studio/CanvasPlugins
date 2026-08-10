package luminus.acng.features.gameplay.miscs

import luminus.acng.Main.config
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.PluginEnableEvent
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning
import taboolib.platform.BukkitPlugin

/**
 * PistonChat 聊天颜色集成挂载器。
 * 不在 plugin.yml 中声明 softdepend，改为运行时检测：
 * - 插件启动时若 PistonChat 已加载则直接挂载
 * - 同时通过 Bukkit Listener 监听 PluginEnableEvent，待 PistonChat 加载后再挂载
 * 这样无论加载顺序如何都能正确集成，且未安装 PistonChat 时不会触发其依赖类加载。
 * 双保险：TabooLib @SubscribeEvent 自动注册 + Bukkit Listener 显式注册。
 */
object PistonChatHook : Listener {

    private var registered = false

    @Awake(LifeCycle.ENABLE)
    fun onEnable() {
        // 显式注册 Bukkit Listener，确保 PluginEnableEvent 能被收到（即使 TabooLib 自动注册失败也能兜底）
        BukkitPlugin.getInstance().server.pluginManager.registerEvents(this, BukkitPlugin.getInstance())
        tryRegister()
    }

    @EventHandler
    fun onPluginEnable(event: PluginEnableEvent) {
        if (event.plugin.name.equals("PistonChat", ignoreCase = true)) {
            tryRegister()
        }
    }

    fun tryRegister() {
        if (registered) return
        if (!config.getBoolean("chat-color.enable", false)) return
        // 大小写不敏感查 PistonChat（防止插件注册名大小写不一致导致检测失败）
        val pistonChat = Bukkit.getPluginManager().getPlugin("PistonChat")
            ?: Bukkit.getPluginManager().plugins.firstOrNull { it.name.equals("PistonChat", ignoreCase = true) }
        if (pistonChat == null || !pistonChat.isEnabled) return
        try {
            Bukkit.getPluginManager().registerEvents(ChatColorListener, BukkitPlugin.getInstance())
            registered = true
            info("[2B2TCore] PistonChat 聊天颜色集成已挂载")
        } catch (t: Throwable) {
            warning("[2B2TCore] PistonChat 聊天颜色集成挂载失败: ${t.javaClass.simpleName}: ${t.message}")
        }
    }
}
