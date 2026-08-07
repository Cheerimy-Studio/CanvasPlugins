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
            var processed = line

            processed = processed.replace("%version%", config.getString("plugin-version", "1.0.10") ?: "1.0.10")

            processed = processFeaturePlaceholder(processed)
            if (processed.isNotEmpty()) {
                sb.append(processed).append("\n")
            }
        }
        return sb.toString().trimEnd()
    }

    private fun processFeaturePlaceholder(line: String): String {
        val features = mapOf(
            "%suicide%" to suicideText(),
            "%dupe-cmd%" to dupeCmdText(),
            "%item-frame%" to itemFrameText(),
            "%chicken%" to chickenText(),
            "%donkey%" to donkeyText(),
            "%mine-place%" to minePlaceText(),
            "%chat-color%" to chatColorText()
        )

        val contains = features.keys.filter { line.contains(it) }
        if (contains.isEmpty()) return line

        contains.forEach { placeholder ->
            val text = features[placeholder] ?: ""
            if (text.isEmpty()) return "" // 功能未启用时整行不显示
        }

        var result = line
        features.forEach { (placeholder, text) ->
            result = result.replace(placeholder, text)
        }
        return result
    }

    private fun suicideText(): String {
        return if (config.getBoolean("suicide-enable", true)) {
            "&e/kill &7(或 &e/suicide&7, &e/514&7) — 自杀"
        } else ""  // 功能未启用时返回空
    }

    private fun dupeCmdText(): String {
        return if (config.getBoolean("duplication.command.enable", true)) {
            "&e/dupe &7— 复制手持物品"
        } else ""  // 功能未启用时返回空
    }

    private fun itemFrameText(): String {
        return if (config.getBoolean("duplication.item-frame.enable", false)) {
            "&e旋转物品展示框 &7— 有概率复制框内物品"
        } else ""  // 功能未启用时返回空
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
        } else ""  // 功能未启用时返回空
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
        } else ""  // 功能未启用时返回空
    }

    private fun minePlaceText(): String {
        return if (config.getBoolean("duplication.mine-and-place.enable", false)) {
            "&e破坏放置复制 &7— 累计破坏潜影盒复制"
        } else ""  // 功能未启用时返回空
    }

    private fun chatColorText(): String {
        return if (config.getBoolean("chat-color.enable", false)) {
            "&e聊天颜色 &7— 根据权限组自动修改聊天颜色"
        } else ""  // 功能未启用时返回空
    }

    private fun buildDefaultHelp(): String {
        return buildString {
            appendLine("&e===== &62B2TCore &e=====")
            appendLine(" ")
            val lines = listOf(
                suicideText(),
                dupeCmdText(),
                itemFrameText(),
                chickenText(),
                donkeyText(),
                minePlaceText(),
                chatColorText()
            ).filter { it.isNotEmpty() }
            lines.forEach { appendLine(it) }
            appendLine("&7出生点随机传送 + 10秒无敌")
            appendLine("&e/stat &7[玩家] — 查看玩家统计")
            appendLine(" ")
            appendLine("&e管理命令：")
            appendLine("&e/core info &7— 查看插件信息")
            appendLine("&e/core reload &7— 重载配置文件")
            appendLine("&e/core clearcache &7— 清空复制缓存")
        }.trimEnd()
    }
}
