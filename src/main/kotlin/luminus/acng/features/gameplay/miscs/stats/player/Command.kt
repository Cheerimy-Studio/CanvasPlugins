package luminus.acng.features.gameplay.miscs.stats.player

import luminus.acng.features.gameplay.miscs.stats.player.Listeners
import luminus.acng.features.gameplay.miscs.stats.player.config
import luminus.acng.features.gameplay.miscs.stats.player.getPlayerData
import luminus.acng.msg
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.command.command
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.function.adaptPlayer
import taboolib.platform.BukkitPlugin
import java.util.Locale


object Command {

    @Awake(LifeCycle.ENABLE)
    fun onEnable() {
        command("stat", permission = "2b2tcore.stat", permissionDefault = PermissionDefault.OP) {
            // 无参数：查看自己
            execute<CommandSender> { sender, _, _ ->
                if (sender is Player) {
                    sender.scheduler.run(BukkitPlugin.getInstance(), { _ ->
                        sender.msg(buildStatisticsMessage(sender))
                    }, null)
                }
            }
            // /stat <玩家名>：查看指定玩家（需在线）
            dynamic {
                suggestion<CommandSender> { sender, context ->
                    Bukkit.getOnlinePlayers().map { it.name }
                }
                execute<CommandSender> { sender, context, _ ->
                    val targetName = context.argumentOrNull(0)
                    if (targetName.isNullOrBlank()) {
                        sender.msg("&c用法：/stat <玩家名>")
                        return@execute
                    }
                    val target = Bukkit.getPlayerExact(targetName)
                        ?: return@execute sender.msg("&c玩家 $targetName 不在线")
                    target.scheduler.run(BukkitPlugin.getInstance(), { _ ->
                        sender.msg(buildStatisticsMessage(target))
                    }, null)
                }
            }
        }
    }

    private fun buildStatisticsMessage(player: Player): String {
        val messages = config.getStringList("messages.player.messages")
        val data = player.getPlayerData()

        val permissionPlaceholders = config.getConfigurationSection("messages.player.permission-placeholders")
        val permissionStatus = buildPermissionStatus(player)
        var processedMessages = ""
        Listeners.OnlineTime.saveCurrentSession(adaptPlayer(player))
        val formattedOnlineTime = data.onlineTime.format(
            config.getString("messages.player.time-day", "Days") ?: "Days",
            config.getString("messages.player.time-hour", "Hours") ?: "Hours",
            config.getString("messages.player.time-minute", "Minutes") ?: "Minutes",
            config.getString("messages.player.time-second", "Sec") ?: "Sec"
        )

        messages.forEach { message ->
            var processedMessage = message


            processedMessage = processedMessage
                .replace("%name%", player.name)
                .replace("%kills%", data.kills.toString())
                .replace("%deaths%", data.deaths.toString())
                .replace("%kd%", String.format(Locale.US, "%.2f", data.kd))
                .replace("%joins%", data.joins.toString())
                .replace("%quits%", data.quits.toString())
                .replace("%online-time-days%", data.onlineTime.days.toString())
                .replace("%online-time-hrs%", data.onlineTime.hours.toString())
                .replace("%online-time-min%", data.onlineTime.minutes.toString())
                .replace("%online-time-sec%", data.onlineTime.seconds.toString())
                .replace("%formatted-online-time%", formattedOnlineTime)
                .replace("%permissions%", permissionStatus)


            permissionPlaceholders?.let { section ->
                section.getKeys(false).forEach { permission ->
                    val placeholder = "%has-${permission}%"
                    if (processedMessage.contains(placeholder)) {
                        val permissionConfig = section.getConfigurationSection(permission)
                        if (permissionConfig != null) {
                            val replacement = if (player.hasPermission(permission)) {
                                permissionConfig.getString("if-has", "true")
                            } else {
                                permissionConfig.getString("if-not", "false")
                            }
                            processedMessage = processedMessage.replace(placeholder, replacement ?: "")
                        }
                    }
                }
            }

            if (processedMessage.isNotBlank()) {
                processedMessages += processedMessage + "\n"
            }
        }
        return processedMessages.trimEnd()
    }

    /** 构造权限状态行，供 /stat 消息使用（分组汇总：全部=勾，部分=黄色问号，没有=X） */
    private fun buildPermissionStatus(player: Player): String {
        val tick = "&a✔"
        val question = "&e?"
        val cross = "&c✘"

        // 复制权限组
        val dupePermissions = listOf(
            "2b2tcore.dupe.command",
            "2b2tcore.dupe.original",
            "2b2tcore.dupe.chicken.xin",
            "2b2tcore.dupe.chicken.click",
            "2b2tcore.dupe.donkey.xin",
            "2b2tcore.dupe.donkey.org",
            "2b2tcore.dupe.item-frame",
            "2b2tcore.dupe.mine-and-place"
        )
        val dupeStatus = evaluatePermissionGroup(player, dupePermissions, tick, question, cross)

        // 地狱上层权限组（单一权限）
        val runmaxStatus = evaluatePermissionGroup(player, listOf("2b2tcore.runmax"), tick, question, cross)

        // 聊天颜色权限组
        val chatColorStatus = evaluatePermissionGroup(
            player,
            listOf("2b2tcore.chatcolor.vip", "2b2tcore.chatcolor.op"),
            tick, question, cross
        )

        // 自杀权限组（单一权限，默认 true 所以通常所有人都有）
        val suicideStatus = evaluatePermissionGroup(player, listOf("2b2tcore.suicide"), tick, question, cross)

        return "&7权限：&7[复制 $dupeStatus] [跑顶 $runmaxStatus] [聊天 $chatColorStatus] [自杀 $suicideStatus]"
    }

    /**
     * 评估一组权限的汇总状态：
     * - 全部有 → tick
     * - 部分有 → question（黄色）
     * - 全无 → cross
     */
    private fun evaluatePermissionGroup(
        player: Player,
        permissions: List<String>,
        tick: String,
        question: String,
        cross: String
    ): String {
        val hasCount = permissions.count { player.hasPermission(it) }
        return when {
            hasCount == permissions.size -> tick
            hasCount > 0 -> question
            else -> cross
        }
    }

    data class PlayerData(val kills: Int, val deaths: Int, val kd: Double, val joins: Int, val quits: Int, val onlineTime: DateTime)
    data class DateTime(val days: Int, val hours: Int, val minutes: Int, val seconds: Int) {
        fun format(dayLabel: String, hourLabel: String, minuteLabel: String, secondLabel: String): String {
            val parts = mutableListOf<String>()
            if (days > 0) parts.add("$days $dayLabel")
            if (hours > 0) parts.add("$hours $hourLabel")
            if (minutes > 0) parts.add("$minutes $minuteLabel")
            if (seconds > 0) parts.add("$seconds $secondLabel")
            // 全为 0 时显示 "0 $secondLabel"
            if (parts.isEmpty()) parts.add("0 $secondLabel")
            return parts.joinToString(" ")
        }

        companion object {
            fun decodeSecondsToDateTime(totalSeconds: Long): DateTime {
                var remaining = totalSeconds

                val days = (remaining / 86400).toInt()
                remaining %= 86400
                val hours = (remaining / 3600).toInt()
                remaining %= 3600
                val minutes = (remaining / 60).toInt()
                val seconds = (remaining % 60).toInt()

                return DateTime(days, hours, minutes, seconds)
            }
        }
    }
}
