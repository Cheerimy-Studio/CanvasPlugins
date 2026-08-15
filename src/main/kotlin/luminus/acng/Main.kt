package luminus.acng

import luminus.acng.features.gameplay.duplications.ChickenDupe
import luminus.acng.features.gameplay.duplications.MineAndPlaceDupe
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.command.CommandSender
import taboolib.common.platform.Plugin
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.subCommand
import taboolib.common.platform.function.adaptCommandSender
import taboolib.common.platform.function.info
import taboolib.common.platform.function.pluginVersion
import taboolib.common.platform.function.warning
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import taboolib.platform.BukkitPlugin

object Main : Plugin() {

    @Config("config.yml")
    lateinit var config: Configuration
    private const val CONFIG_VERSION = 8
    private const val GITHUB_REPO = "https://github.com/Cheerimy-Studio/CanvasPlugins"

    override fun onEnable() {
        // 启动耗时计时
        val start = System.currentTimeMillis()

        if (config.getInt("config-ver", CONFIG_VERSION) != CONFIG_VERSION) {
            BukkitPlugin.getInstance().saveResource("config.yml", true)
        }

        // PistonChat 聊天颜色集成在 PistonChatHook 中通过 @Awake 与 PluginEnableEvent 动态挂载

        // Cheerimy-Studio 正版检测
        val cheerimy = Bukkit.getPluginManager().getPlugin("Cheerimy-Studio")
        if (cheerimy == null || !cheerimy.isEnabled) {
            warning("[2B2TCore] Cheerimy-Studio integrity check failed.")
            warning("[2B2TCore] This plugin may have been tampered with.")
            warning("[2B2TCore] Please install Cheerimy-Studio from: https://github.com/Cheerimy-Studio/MinecraftPlugins")
        }

        val cost = System.currentTimeMillis() - start
        info("§a2B2TCore §ev$pluginVersion §a加载完成")
        info("§a启动耗时: §e${cost}ms")
        info("§aGitHub: §b$GITHUB_REPO")
    }

    @CommandHeader("core", permission = "2b2tcore.reload", permissionDefault = PermissionDefault.OP)
    object CommandMain {
        @CommandBody
        val info = subCommand {
            execute<CommandSender> { sender, _, _ ->
                sender.msg("&e2B2TCore &6v$pluginVersion")
                sender.msg("&eGitHub: &b$GITHUB_REPO")
            }
        }

        @CommandBody
        val reload = subCommand {
            execute<CommandSender> { sender, _, _ ->
                config.reload()
                luminus.acng.features.gameplay.miscs.stats.player.config.reload()
                sender.msg("&e已重载配置文件")
            }
        }

        @CommandBody
        val clearcache = subCommand {
            execute<CommandSender> { sender, _, _ ->
                ChickenDupe.XinMode.reload()
                MineAndPlaceDupe.clear()
                sender.msg("&e已清空缓存")
            }
        }
    }
}

fun CommandSender.msg(vararg args: Any) {
    adaptCommandSender(this).message(args)
}

fun ProxyCommandSender.message(vararg args: Any) {
    fun Any?.toChatString(): String = when (this) {
        null -> "null"
        is Array<*> -> this.joinToString("") { it.toChatString() }
        is Collection<*> -> this.joinToString("") { it.toChatString() }
        else -> this.toString()
    }
    val message = args.joinToString("") { it.toChatString() }
    sendMessage(ChatColor.translateAlternateColorCodes('&', message))
}
