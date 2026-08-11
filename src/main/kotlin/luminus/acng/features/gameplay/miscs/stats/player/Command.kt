package luminus.acng.features.gameplay.miscs.stats.player

import luminus.acng.features.gameplay.miscs.stats.player.config
import luminus.acng.msg
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.command.command
import taboolib.common.platform.command.player
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.function.adaptPlayer
import taboolib.platform.BukkitPlugin
import java.util.Locale


object Command {

    @Awake(LifeCycle.ENABLE)
    fun onEnable() {
        command("stat", permission = "2b2tcore.stat", permissionDefault = PermissionDefault.OP) {
            execute<CommandSender> { sender, _, _ ->
                if (sender is Player) {
                    // 调度到自身实体线程，Folia 线程安全
                    sender.scheduler.run(BukkitPlugin.getInstance(), { _ ->
                        sender.msg(buildStatisticsMessage(sender))
                    }, null)
                }
            }
            player {
                execute<CommandSender> { sender, context, _ ->
                    // 所有玩家都可以查看其他玩家的统计
                    val target = context.player().castSafely<Player>() ?: run {
                        sender.msg("&c无法找到该玩家")
                        return@execute
                    }
                    // Folia：目标玩家可能在别的区域线程，统计构建必须调度到目标玩家的实体线程
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

    /** 构造权限状态行，供 /stat 消息使用（一行显示） */
    private fun buildPermissionStatus(player: Player): String {
        val tick = "&a✔"
        val cross = "&c✘"
        val runMax = if (player.hasPermission("2b2tcore.runmax")) tick else cross
        val dupe = if (player.hasPermission("2b2tcore.dupe.command")) tick else cross
        val greenName = if (player.hasPermission("2b2tcore.chatcolor.vip")) tick else cross
        return "&7权限：&7🏃 $runMax &7📦 $dupe &7🟢 $greenName"
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
