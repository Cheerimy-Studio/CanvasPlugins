package luminus.acng.features.gameplay.miscs.stats.player

import luminus.acng.msg
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.command.command
import taboolib.common.platform.command.player
import taboolib.common.platform.function.adaptPlayer


object Command {

    @Awake(LifeCycle.ENABLE)
    fun onEnable() {
        command("stat") {
            execute<CommandSender> { sender, _, _ ->
                if (sender is Player) {
                    sender.msg(buildStatisticsMessage(sender))
                }
            }
            player {
                execute<CommandSender> { sender, context, _ ->
                    sender.msg(context.player().castSafely<Player>()?.let { buildStatisticsMessage(it) } ?: "Error Statistics")
                }
            }
        }
    }

    private fun buildStatisticsMessage(player: Player): String {
        val messages = config.getStringList("messages.player.messages")
        val data = player.getPlayerData()

        val permissionPlaceholders = config.getConfigurationSection("messages.player.permission-placeholders")
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
                .replace("%kd%", data.kds.toString())
                .replace("%joins%", data.joins.toString())
                .replace("%quits%", data.quits.toString())
                .replace("%online-time-days%", data.onlineTime.days.toString())
                .replace("%online-time-hrs%", data.onlineTime.hours.toString())
                .replace("%online-time-min%", data.onlineTime.minutes.toString())
                .replace("%online-time-sec%", data.onlineTime.seconds.toString())
                .replace("%formatted-online-time%", formattedOnlineTime)


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
            processedMessages += processedMessage + "\n"
        }
        return processedMessages
    }

    data class PlayerData(val kills: Int, val deaths: Int, val kds: Double, val joins: Int, val quits: Int, val onlineTime: DateTime)
    data class DateTime(val days: Int, val hours: Int, val minutes: Int, val seconds: Int) {
        fun format(day: String, hrs: String, min: String, sec: String): String {
            val parts = mutableListOf<String>()
            if (days != 0) parts += "$days $day"
            if (hours != 0) parts += "$hours $hrs"
            if (minutes != 0) parts += "$minutes $min"
            if (seconds != 0 || parts.isEmpty()) parts += "$seconds $sec"
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