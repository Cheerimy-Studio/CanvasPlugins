package luminus.acng.features.gameplay.miscs.stats.player

import luminus.acng.features.gameplay.miscs.stats.player.Listeners.JoinQuit.getPlayerJoins
import luminus.acng.features.gameplay.miscs.stats.player.Listeners.JoinQuit.getPlayerQuits
import luminus.acng.features.gameplay.miscs.stats.player.Listeners.KillDeath.calculateKD
import luminus.acng.features.gameplay.miscs.stats.player.Listeners.KillDeath.getPlayerDeaths
import luminus.acng.features.gameplay.miscs.stats.player.Listeners.KillDeath.getPlayerKills
import luminus.acng.features.gameplay.miscs.stats.player.Listeners.OnlineTime.getTotalOnlineTime
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.persistence.PersistentDataType
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.ProxyPlayer
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.adaptPlayer
import taboolib.common.platform.function.submit
import taboolib.module.configuration.Config
import taboolib.module.configuration.ConfigFile
import taboolib.platform.util.killer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

@Config("statistics.yml")
lateinit var config: ConfigFile

object Listeners {
    object KillDeath {
        val killsKey = NamespacedKey("anarchycore-nextgen", "player-kills")
        val deathsKey = NamespacedKey("anarchycore-nextgen", "player-deaths")

        private val isStatisticsEnabled: Boolean
            get() = config.getBoolean("player.enable", true)

        @SubscribeEvent
        fun onKill(event: PlayerDeathEvent) {
            if (!isStatisticsEnabled) return
            // 击杀者非玩家时直接返回（killer 扩展属性已处理 null）
            val killer = event.killer
            if (killer is Player) {
                updateKills(killer, 1)
            }
        }

        @SubscribeEvent
        fun onDeath(event: PlayerDeathEvent) {
            if (!isStatisticsEnabled) return
            updateDeaths(event.entity, 1)
        }

        private fun updateKills(player: Player?, amount: Int) {
            player?.let {
                val pdc = it.persistentDataContainer
                val currentKills = pdc.get(killsKey, PersistentDataType.INTEGER) ?: 0
                pdc.set(killsKey, PersistentDataType.INTEGER, currentKills + amount)
            }
        }

        private fun updateDeaths(player: Player?, amount: Int) {
            player?.let {
                val pdc = it.persistentDataContainer
                val currentDeaths = pdc.get(deathsKey, PersistentDataType.INTEGER) ?: 0
                pdc.set(deathsKey, PersistentDataType.INTEGER, currentDeaths + amount)
            }
        }

        fun getPlayerKills(player: Player): Int {
            return player.persistentDataContainer.get(killsKey, PersistentDataType.INTEGER) ?: 0
        }

        fun getPlayerDeaths(player: Player): Int {
            return player.persistentDataContainer.get(deathsKey, PersistentDataType.INTEGER) ?: 0
        }

        fun getPlayerKD(player: Player): Double {
            val kills = getPlayerKills(player)
            val deaths = getPlayerDeaths(player)
            return calculateKD(kills, deaths)
        }

        fun calculateKD(kills: Int, deaths: Int): Double {
            return if (deaths == 0) kills.toDouble() else kills.toDouble() / deaths.toDouble()
        }
    }

    object JoinQuit {
        val joinsKey = NamespacedKey("anarchycore-nextgen", "player-joins")
        val quitsKey = NamespacedKey("anarchycore-nextgen", "player-quits")

        private fun updateJoins(player: Player?, amount: Int) {
            player?.let {
                val pdc = it.persistentDataContainer
                val currentJoins = pdc.get(joinsKey, PersistentDataType.INTEGER) ?: 0
                pdc.set(joinsKey, PersistentDataType.INTEGER, currentJoins + amount)
            }
        }

        private fun updateQuits(player: Player?, amount: Int) {
            player?.let {
                val pdc = it.persistentDataContainer
                val currentQuits = pdc.get(quitsKey, PersistentDataType.INTEGER) ?: 0
                pdc.set(quitsKey, PersistentDataType.INTEGER, currentQuits + amount)
            }
        }

        fun getPlayerJoins(player: Player): Int {
            return player.persistentDataContainer.get(joinsKey, PersistentDataType.INTEGER) ?: 0
        }

        fun getPlayerQuits(player: Player): Int {
            return player.persistentDataContainer.get(quitsKey, PersistentDataType.INTEGER) ?: 0
        }

        @SubscribeEvent
        fun onJoin(event: PlayerJoinEvent) {
            updateJoins(event.player, 1)
        }

        @SubscribeEvent
        fun onQuit(event: PlayerQuitEvent) {
            updateQuits(event.player, 1)
        }
    }

    object OnlineTime {

        // 以玩家 UUID 作为 key：TabooLib 的 ProxyPlayer 包装对象每次 adaptPlayer()
        // 可能返回不同实例，若其未按 UUID 重写 equals/hashCode，用它当 key 会导致
        // 退出时 remove 不到进入时的记录 → 在线时长恒为 0。改用 UUID 从根上杜绝。
        private val joinTimeMap = ConcurrentHashMap<UUID, Long>()

        private val ONLINE_TIME_KEY by lazy {
            NamespacedKey("anarchycore-nextgen", "online-time")
        }

        private val initialized = AtomicBoolean(false)

        @Awake(LifeCycle.ENABLE)
        fun init() {
            if (!initialized.compareAndSet(false, true)) return
            // 异步清理任务，仅遍历 ConcurrentMap，不操作实体/世界，Folia 安全
            submit(async = true, delay = 1200L, period = 1200L) {
                cleanupOfflinePlayers()
            }
        }

        @SubscribeEvent
        fun onPlayerJoin(event: PlayerJoinEvent) {
            joinTimeMap[event.player.uniqueId] = System.currentTimeMillis()
        }

        @SubscribeEvent
        fun onPlayerQuit(event: PlayerQuitEvent) {
            val joinTime = joinTimeMap.remove(event.player.uniqueId) ?: return

            val sessionSeconds = (System.currentTimeMillis() - joinTime) / 1000
            updatePDCOnQuit(adaptPlayer(event.player), sessionSeconds)
        }

        private fun updatePDCOnQuit(player: ProxyPlayer, sessionSeconds: Long) {
            if (sessionSeconds <= 0) return
            // PlayerQuitEvent 已在玩家区域线程触发，直接同步操作 PDC，Folia 安全
            player.castSafely<Player>()?.let { bukkitPlayer ->
                val pdc = bukkitPlayer.persistentDataContainer
                val currentTotal = pdc.get(ONLINE_TIME_KEY, PersistentDataType.LONG) ?: 0L
                pdc.set(ONLINE_TIME_KEY, PersistentDataType.LONG, currentTotal + sessionSeconds)
            }
        }

        fun getTotalOnlineTime(player: ProxyPlayer): Long {
            val bukkitPlayer = player.castSafely<Player>() ?: return 0L
            val pdcTotal = bukkitPlayer.persistentDataContainer.get(ONLINE_TIME_KEY, PersistentDataType.LONG) ?: 0L
            val currentSessionSeconds = joinTimeMap[player.uniqueId]?.let { joinTime ->
                (System.currentTimeMillis() - joinTime) / 1000
            } ?: 0L
            return pdcTotal + currentSessionSeconds
        }

        fun getCurrentSessionTime(player: ProxyPlayer): Long {
            return joinTimeMap[player.uniqueId]?.let { joinTime ->
                (System.currentTimeMillis() - joinTime) / 1000
            } ?: 0L
        }

        fun getStoredOnlineTime(player: ProxyPlayer): Long {
            val bukkitPlayer = player.castSafely<Player>() ?: return 0L
            // 调用方应在玩家区域线程；原版用 submit 包装导致返回值永远是 0，已修复
            return bukkitPlayer.persistentDataContainer.get(ONLINE_TIME_KEY, PersistentDataType.LONG) ?: 0L
        }

        private fun cleanupOfflinePlayers() {
            val iterator = joinTimeMap.iterator()
            while (iterator.hasNext()) {
                val (uuid, _) = iterator.next()
                if (Bukkit.getPlayer(uuid) == null) {
                    iterator.remove()
                }
            }
        }

        fun getJoinTime(player: ProxyPlayer): Long? {
            return joinTimeMap[player.uniqueId]
        }

        fun isTracking(player: ProxyPlayer): Boolean {
            return joinTimeMap.containsKey(player.uniqueId)
        }

        fun resetOnlineTime(player: ProxyPlayer, clearCurrentSession: Boolean = true) {
            // 调用方应在玩家区域线程，直接同步移除 PDC 数据，Folia 安全
            player.castSafely<Player>()?.let { bukkitPlayer ->
                bukkitPlayer.persistentDataContainer.remove(ONLINE_TIME_KEY)
            }
            if (clearCurrentSession) {
                joinTimeMap.remove(player.uniqueId)
            }
        }

        fun saveCurrentSession(player: ProxyPlayer): Boolean {
            val joinTime = joinTimeMap[player.uniqueId] ?: return false
            val sessionSeconds = (System.currentTimeMillis() - joinTime) / 1000
            if (sessionSeconds <= 0) return false
            updatePDCOnQuit(player, sessionSeconds)
            joinTimeMap[player.uniqueId] = System.currentTimeMillis()
            return true
        }

        fun getTrackedPlayers(): Set<ProxyPlayer> {
            return joinTimeMap.keys
                .mapNotNull { uuid -> Bukkit.getPlayer(uuid)?.let { adaptPlayer(it) } }
                .toSet()
        }
    }
}

fun Player.getPlayerData(): Command.PlayerData {
    val kills = getPlayerKills(this)
    val deaths = getPlayerDeaths(this)
    return Command.PlayerData(
        kills,
        deaths,
        calculateKD(kills, deaths),
        getPlayerJoins(this),
        getPlayerQuits(this),
        Command.DateTime.decodeSecondsToDateTime(getTotalOnlineTime(adaptPlayer(this)))
    )
}
