package luminus.acng.features.gameplay.spawn

import luminus.acng.Main.config
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerRespawnEvent
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.platform.BukkitPlugin
import java.util.concurrent.ThreadLocalRandom

/**
 * 随机出生点 + 死亡随机重生 + 无敌时间
 *
 * - 首次加入：随机传送到出生点周围
 * - 死亡重生：未设置床/锚重生点时也随机重生
 * - 两者均授予配置的无敌时间
 *
 * 全部用玩家 EntityScheduler + teleportAsync 保证 Folia 线程安全。
 */
object SpawnListener : Listener {

    @Awake(LifeCycle.ENABLE)
    fun register() {
        if (!config.getBoolean("spawn.enable", true)) return
        BukkitPlugin.getInstance().server.pluginManager.registerEvents(this, BukkitPlugin.getInstance())
    }

    /** 首次加入游戏：随机传送 + 无敌 */
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        if (!config.getBoolean("spawn.enable", true)) return
        val player = event.player
        if (player.hasPlayedBefore()) return

        randomTeleport(player)
        grantInvulnerability(player)
    }

    /**
     * 死亡重生：未设置床/锚重生点时随机重生 + 无敌
     * isBedSpawn 为 true 表示玩家有有效床或重生锚，不干预
     */
    @EventHandler
    fun onRespawn(event: PlayerRespawnEvent) {
        if (!config.getBoolean("spawn.enable", true)) return
        if (!config.getBoolean("spawn.respawn-random", true)) return
        // 已设置床/重生锚 → 尊重原重生点，不随机
        if (event.isBedSpawn) return

        val player = event.player

        // 重生后延迟到玩家实体线程执行传送 + 无敌
        // EntityScheduler 在下一 tick 执行，此时玩家已完成重生放置
        player.scheduler.run(BukkitPlugin.getInstance(), { _ ->
            randomTeleport(player)
            grantInvulnerability(player)
        }, null)
    }

    /** 随机传送到世界出生点周围 */
    private fun randomTeleport(player: Player) {
        val radius = config.getInt("spawn.radius", 500)
        player.scheduler.run(BukkitPlugin.getInstance(), { _ ->
            val world = player.world
            val worldSpawn = world.spawnLocation
            val rnd = ThreadLocalRandom.current()
            val x = worldSpawn.blockX + rnd.nextInt(-radius, radius + 1)
            val z = worldSpawn.blockZ + rnd.nextInt(-radius, radius + 1)
            val loc = world.getHighestBlockAt(x, z).location.add(0.5, 1.0, 0.5)
            player.teleportAsync(loc)
        }, null)
    }

    /** 授予无敌时间 */
    private fun grantInvulnerability(player: Player) {
        val invulnerableSeconds = config.getInt("spawn.invulnerability-seconds", 10)
        if (invulnerableSeconds > 0) {
            player.noDamageTicks = invulnerableSeconds * 20
        }
    }
}
