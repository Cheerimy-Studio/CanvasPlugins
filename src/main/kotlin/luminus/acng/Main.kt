package luminus.acng

import luminus.acng.features.gameplay.duplications.ChickenDupe
import luminus.acng.features.gameplay.duplications.MineAndPlaceDupe
import luminus.acng.features.gameplay.teleport.OriginStone
import luminus.acng.features.gameplay.teleport.TeleportItems
import luminus.acng.features.gameplay.teleport.TeleportStone
import luminus.acng.features.gameplay.teleport.WarpStone
import luminus.acng.features.gameplay.teleport.WarpStoneItems
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
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
    private const val CONFIG_VERSION = 14
    private const val GITHUB_REPO = "https://github.com/Cheerimy-Studio/CanvasPlugins"

    override fun onEnable() {
        // 启动耗时计时
        val start = System.currentTimeMillis()

        val fileConfigVer = config.getInt("config-ver", 0)
        if (fileConfigVer != CONFIG_VERSION) {
            // 不覆盖用户配置文件！只更新版本号，新增配置项使用代码默认值
            warning("[2B2TCore] 配置版本不匹配（当前: $fileConfigVer, 需要: $CONFIG_VERSION）")
            warning("[2B2TCore] 已保留用户自定义配置，新增项将使用默认值")
            warning("[2B2TCore] 如需完整默认配置，请备份后删除 config.yml 再重启")
            config.set("config-ver", CONFIG_VERSION)
            try {
                config.saveToFile()
            } catch (e: NoSuchMethodException) {
                // TabooLib 版本不兼容时静默处理，版本号仅在内存中更新
            } catch (e: Exception) {
                warning("[2B2TCore] 配置版本号写入失败: ${e.message}")
            }
        }

        // PistonChat 聊天颜色集成在 PistonChatHook 中通过 @Awake 与 PluginEnableEvent 动态挂载

        // Cheerimy-Studio 正版检测（仅提示，不阻止运行）
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
                // reload 后重新尝试挂载 PistonChat 聊天颜色
                luminus.acng.features.gameplay.miscs.PistonChatHook.tryRegister()
                // reload 后确保 Listener 已注册（首次启动时 enable=false 则未注册）
                luminus.acng.features.gameplay.spawn.SpawnListener.register()
                luminus.acng.features.gameplay.miscs.NetherRoofListener.register()
                // reload 后确保传送石配方/监听器已注册
                luminus.acng.features.gameplay.teleport.TeleportRecipes.register()
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

        @CommandBody
        val give = subCommand {
            // /core give teleportstone [数量]
            literal("teleportstone") {
                dynamic("amount") {
                    suggestion<Player> { _, _ -> listOf("1", "2", "4", "8", "16", "32", "64") }
                    execute<Player> { sender, ctx, _ ->
                        val amount = (ctx.argument(-1).toIntOrNull() ?: 1).coerceIn(1, 64)
                        val item = TeleportStone.createBody(); item.amount = amount
                        sender.inventory.addItem(item).forEach { (_, v) -> sender.world.dropItemNaturally(sender.location, v) }
                        sender.msg("&a已给予 ${amount} 个传送石")
                    }
                }
                execute<Player> { sender, _, _ ->
                    sender.inventory.addItem(TeleportStone.createBody())
                    sender.msg("&a已给予 1 个传送石")
                }
            }
            // /core give originstone [数量]
            literal("originstone") {
                dynamic("amount") {
                    suggestion<Player> { _, _ -> listOf("1", "2", "4", "8", "16", "32", "64") }
                    execute<Player> { sender, ctx, _ ->
                        val amount = (ctx.argument(-1).toIntOrNull() ?: 1).coerceIn(1, 64)
                        val item = OriginStone.createBody(); item.amount = amount
                        sender.inventory.addItem(item).forEach { (_, v) -> sender.world.dropItemNaturally(sender.location, v) }
                        sender.msg("&a已给予 ${amount} 个起源石")
                    }
                }
                execute<Player> { sender, _, _ ->
                    sender.inventory.addItem(OriginStone.createBody())
                    sender.msg("&a已给予 1 个起源石")
                }
            }
            // /core give warpstone [数量]
            literal("warpstone") {
                dynamic("amount") {
                    suggestion<Player> { _, _ -> listOf("1", "2", "4", "8", "16", "32", "64") }
                    execute<Player> { sender, ctx, _ ->
                        val amount = (ctx.argument(-1).toIntOrNull() ?: 1).coerceIn(1, 64)
                        val item = WarpStone.createBody(); item.amount = amount
                        sender.inventory.addItem(item).forEach { (_, v) -> sender.world.dropItemNaturally(sender.location, v) }
                        sender.msg("&a已给予 ${amount} 个瞬移石")
                    }
                }
                execute<Player> { sender, _, _ ->
                    sender.inventory.addItem(WarpStone.createBody())
                    sender.msg("&a已给予 1 个瞬移石")
                }
            }
            // /core give pearl [等级] [数量]（默认满级 1 个）
            literal("pearl") {
                dynamic("level") {
                    suggestion<Player> { _, _ -> (1..WarpStoneItems.MAX_LEVEL).map { it.toString() } }
                    dynamic("amount") {
                        suggestion<Player> { _, _ -> listOf("1", "4", "8", "16", "64") }
                        execute<Player> { sender, ctx, _ ->
                            val level = (ctx.argument(-2).toIntOrNull() ?: WarpStoneItems.MAX_LEVEL).coerceIn(1, WarpStoneItems.MAX_LEVEL)
                            val amount = (ctx.argument(-1).toIntOrNull() ?: 1).coerceIn(1, 64)
                            val item = WarpStoneItems.createPearl(level); item.amount = amount
                            sender.inventory.addItem(item).forEach { (_, v) -> sender.world.dropItemNaturally(sender.location, v) }
                            sender.msg("&a已给予 ${amount} 个 ${WarpStoneItems.levelName(level).replace("§", "&")} 压缩珍珠")
                        }
                    }
                    execute<Player> { sender, ctx, _ ->
                        val level = (ctx.argument(-1).toIntOrNull() ?: WarpStoneItems.MAX_LEVEL).coerceIn(1, WarpStoneItems.MAX_LEVEL)
                        sender.inventory.addItem(WarpStoneItems.createPearl(level))
                        sender.msg("&a已给予 1 个 ${WarpStoneItems.levelName(level).replace("§", "&")} 压缩珍珠")
                    }
                }
                execute<Player> { sender, _, _ ->
                    sender.inventory.addItem(WarpStoneItems.createPearl(WarpStoneItems.MAX_LEVEL))
                    sender.msg("&a已给予 1 个 &6满级 压缩珍珠")
                }
            }
            // /core give shard [数量]
            literal("shard") {
                dynamic("amount") {
                    suggestion<Player> { _, _ -> listOf("1", "4", "8", "16", "64") }
                    execute<Player> { sender, ctx, _ ->
                        val amount = (ctx.argument(-1).toIntOrNull() ?: 1).coerceIn(1, 64)
                        val item = TeleportItems.createShard(); item.amount = amount
                        sender.inventory.addItem(item).forEach { (_, v) -> sender.world.dropItemNaturally(sender.location, v) }
                        sender.msg("&a已给予 ${amount} 个传送碎片")
                    }
                }
                execute<Player> { sender, _, _ ->
                    sender.inventory.addItem(TeleportItems.createShard())
                    sender.msg("&a已给予 1 个传送碎片")
                }
            }
            // /core give core [数量]
            literal("core") {
                dynamic("amount") {
                    suggestion<Player> { _, _ -> listOf("1", "4", "8", "16", "64") }
                    execute<Player> { sender, ctx, _ ->
                        val amount = (ctx.argument(-1).toIntOrNull() ?: 1).coerceIn(1, 64)
                        val item = TeleportItems.createCore(); item.amount = amount
                        sender.inventory.addItem(item).forEach { (_, v) -> sender.world.dropItemNaturally(sender.location, v) }
                        sender.msg("&a已给予 ${amount} 个传送核心")
                    }
                }
                execute<Player> { sender, _, _ ->
                    sender.inventory.addItem(TeleportItems.createCore())
                    sender.msg("&a已给予 1 个传送核心")
                }
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
