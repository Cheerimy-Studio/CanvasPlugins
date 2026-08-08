package luminus.acng.features.gameplay.miscs

import luminus.acng.Main.config
import luminus.acng.msg
import org.bukkit.command.CommandSender
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.command.command
import taboolib.common.platform.command.PermissionDefault

/**
 * 帮助命令：/help
 * 根据配置动态显示已启用的功能，关闭的功能不显示
 * 风格对标 /stat：使用 config messages 格式化输出
 */
object Help {

    @Awake(LifeCycle.ENABLE)
    fun init() {
        command("help") {
            execute<CommandSender> { sender, _, _ ->
                sender.msg(buildHelpMessage())
            }
        }
    }

    private fun buildHelpMessage(): String {
        val messages = config.getStringList("messages.help.lines")
        if (messages.isEmpty()) {
            return buildDefaultHelp()
        }

        val sb = StringBuilder()
        messages.forEach { line ->
            val processed = processLine(line)
            if (processed.isNotEmpty()) {
                sb.append(processed).append("\n")
            }
        }
        return sb.toString().trimEnd()
    }

    private fun processLine(line: String): String {
        var result = line.replace("%version%", pluginVersion())
        result = processFeaturePlaceholders(result)
        return result
    }

    private fun processFeaturePlaceholders(line: String): String {
        val features = mapOf(
            "%suicide%" to suicideText(),
            "%dupe-cmd%" to dupeCmdText(),
            "%item-frame%" to itemFrameText(),
            "%chicken%" to chickenText(),
            "%donkey%" to donkeyText(),
            "%mine-place%" to minePlaceText(),
            "%chat-color%" to chatColorText(),
            "%stat%" to statText()
        )

        // 如果行中包含任何未启用的功能占位符，整行不显示
        for ((placeholder, text) in features) {
            if (line.contains(placeholder) && text.isEmpty()) {
                return ""
            }
        }

        var result = line
        for ((placeholder, text) in features) {
            result = result.replace(placeholder, text)
        }
        return result
    }

    private fun pluginVersion(): String {
        return try {
            taboolib.common.platform.function.pluginVersion
        } catch (_: Throwable) {
            "1.0.10"
        }
    }

    private fun suicideText(): String {
        return if (config.getBoolean("suicide-enable", true)) {
            "&e/suicide &7(或 &e/514&7, &e/kill&7) — 自杀"
        } else ""
    }

    private fun dupeCmdText(): String {
        return if (config.getBoolean("duplication.command.enable", true)) {
            "&e/dupe &7— 复制手持物品"
        } else ""
    }

    private fun itemFrameText(): String {
        return if (config.getBoolean("duplication.item-frame.enable", false)) {
            "&e旋转物品展示框 &7— 有概率复制框内物品"
        } else ""
    }

    private fun chickenText(): String {
        val xin = config.getBoolean("duplication.chicken.xin-mode", false)
        val click = config.getBoolean("duplication.chicken.click-mode", false)
        return if (xin || click) {
            val modeText = when {
                xin && click -> "Xin模式 + 点击模式"
                xin -> "Xin模式"
                else -> "点击模式"
            }
            "&e鸡刷复制 &7— $modeText"
        } else ""
    }

    private fun donkeyText(): String {
        val xin = config.getBoolean("duplication.donkey.xin-mode", false)
        val org = config.getBoolean("duplication.donkey.org-mode", false)
        return if (xin || org) {
            val modeText = when {
                xin && org -> "Xin模式 + Org模式"
                xin -> "Xin模式"
                else -> "Org模式"
            }
            "&e驴复制 &7— $modeText"
        } else ""
    }

    private fun minePlaceText(): String {
        return if (config.getBoolean("duplication.mine-and-place.enable", false)) {
            "&e破坏放置复制 &7— 累计破坏潜影盒复制"
        } else ""
    }

    private fun chatColorText(): String {
        return if (config.getBoolean("chat-color.enable", false)) {
            "&e聊天颜色 &7— 根据权限组自动修改聊天颜色"
        } else ""
    }

    private fun statText(): String {
        return if (config.getBoolean("player.enable", true)) {
            "&e/stat &7[玩家] — 查看玩家统计"
        } else ""
    }

    private fun buildDefaultHelp(): String {
        return buildString {
            appendLine("&e===== &62B2T &e=====")
            appendLine(" ")
            val lines = listOf(
                suicideText(),
                dupeCmdText(),
                itemFrameText(),
                chickenText(),
                donkeyText(),
                minePlaceText(),
                chatColorText(),
                statText()
            ).filter { it.isNotEmpty() }
            lines.forEach { appendLine(it) }
            appendLine("&7出生点随机传送 + 10秒无敌")
            appendLine(" ")
            appendLine("&e管理命令（需要权限）：")
            appendLine("&e/core info &7— 查看插件信息")
            appendLine("&e/core reload &7— 重载配置文件")
            appendLine("&e/core clearcache &7— 清空复制缓存")
            appendLine("&e/dupe &7— 复制手持物品（core.dupe.command）")
            appendLine("&e/stat &7[玩家] — 查看玩家统计（core.stat）")
            appendLine("&e/suicide &7— 自杀（core.suicide）")
            appendLine("&7权限可通过 LuckPerms 等插件发放")
        }.trimEnd()
    }
}
