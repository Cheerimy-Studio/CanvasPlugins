package luminus.acng.features.gameplay.spawn

import luminus.acng.Main.config
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.platform.BukkitPlugin
import taboolib.platform.util.submit
import java.util.concurrent.ThreadLocalRandom

/**
 * 随机出生点 + 无敌时间
 * player-join 事件：将玩家随机传送到 config 指定半径内，并授予无敌时间。
 * 全部用玩家 EntityScheduler 保证 Folia 线程安全。
 */
object SpawnListener : Listener {

    @Awake(LifeCycle.ENABLE)
    fun register() {
        if (!config.getBoolean("spawn.enable", true)) return
        BukkitPlugin.getInstance().server.pluginManager.registerEvents(this, BukkitPlugin.getInstance())
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        if (!config.getBoolean("spawn.enable", true)) return
        val player = event.player
        if (player.hasPlayedBefore()) return

        val radius = config.getInt("spawn.radius", 500)
        val invulnerableSeconds = config.getInt("spawn.invulnerability-seconds", 10)

        // 在玩家实体调度器中执行区块查找 + 传送，保证 Folia 线程安全
        player.scheduler.run(BukkitPlugin.getInstance(), { _ ->
            val world = player.world
            val worldSpawn = world.spawnLocation
            val rnd = ThreadLocalRandom.current()
            val x = worldSpawn.blockX + rnd.nextInt(-radius, radius + 1)
            val z = worldSpawn.blockZ + rnd.nextInt(-radius, radius + 1)
            val loc = world.getHighestBlockAt(x, z).location.add(0.5, 1.0, 0.5)
            player.teleport(loc)
        }, null)

        if (invulnerableSeconds > 0) {
            player.noDamageTicks = invulnerableSeconds * 20
        }
    }
}
