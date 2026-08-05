package luminus.acng.features.gameplay.miscs

import luminus.acng.Main.config
import org.bukkit.Bukkit
import org.bukkit.event.server.PluginEnableEvent
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.info
import taboolib.platform.BukkitPlugin

/**
 * PistonChat 聊天颜色集成挂载器。
 * 不在 plugin.yml 中声明 softdepend，改为运行时检测：
 * - 插件启动时若 PistonChat 已加载则直接挂载
 * - 否则监听 PluginEnableEvent，待 PistonChat 加载后再挂载
 * 这样无论加载顺序如何都能正确集成，且未安装 PistonChat 时不会触发其依赖类加载。
 */
object PistonChatHook {

    private var registered = false

    @Awake(LifeCycle.ENABLE)
    fun onEnable() {
        tryRegister()
    }

    @SubscribeEvent
    fun onPluginEnable(event: PluginEnableEvent) {
        if (event.plugin.name.equals("PistonChat", ignoreCase = true)) {
            tryRegister()
        }
    }

    fun tryRegister() {
        if (registered) return
        if (!config.getBoolean("chat-color.enable", false)) return
        if (Bukkit.getPluginManager().getPlugin("PistonChat") == null) return
        try {
            // 仅在此处引用 ChatColorListener，确保未安装 PistonChat 时不会加载其事件类
            Bukkit.getPluginManager().registerEvents(ChatColorListener, BukkitPlugin.getInstance())
            registered = true
            info("[2B2TCore] PistonChat 聊天颜色集成已挂载")
        } catch (_: Throwable) {
            // PistonChat 不可用，忽略
        }
    }
}
