package luminus.acng.features.gameplay.miscs

import luminus.acng.Main.config
import luminus.acng.msg
import org.bukkit.command.CommandSender
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.command.command

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
            "%stat%" to statText(),
            "%nether-roof%" to netherRoofText()
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
            "&e/suicide &7(或 &e/514&7) — 自杀"
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
        return if (luminus.acng.features.gameplay.miscs.stats.player.config.getBoolean("player.enable", true)) {
            "&e/stat &7[玩家] — 查看玩家统计"
        } else ""
    }

    private fun netherRoofText(): String {
        return if (config.getBoolean("nether-roof.enable", false)) {
            "&e地狱顶层限制 &7— 超过 128 层自动传送（权限：2b2tcore.runmax）"
        } else ""
    }

    private fun buildDefaultHelp(): String {
        return buildString {
            appendLine("&7=====&62B2T 帮助菜单&7=====")
            appendLine("&e/stat &7[玩家] — 查看玩家统计")
            appendLine("&e/suicide &7或 &e/514 &7— 自杀")
            appendLine("&e/dupe &7— 复制手持物品")
            appendLine("&e/core info &7— 查看插件信息")
        }.trimEnd()
    }
}
