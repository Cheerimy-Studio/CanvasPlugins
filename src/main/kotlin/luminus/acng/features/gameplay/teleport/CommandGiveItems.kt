package luminus.acng.features.gameplay.teleport

import luminus.acng.Main.config
import luminus.acng.msg
import org.bukkit.entity.Player
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.simpleCommand

/** 管理员命令：发放传送石/起源石/瞬移石/压缩珍珠 */
object CommandGiveItems {

    @Awake(LifeCycle.ENABLE)
    fun init() {
        simpleCommand(
            "giveteleportstone",
            permission = "2b2tcore.give.teleportstone",
            permissionDefault = PermissionDefault.OP
        ) { sender, _ ->
            val player = sender as? Player ?: return@simpleCommand
            val item = TeleportStone.createBody()
            player.inventory.addItem(item)
            player.msg("&a已给予传送石")
        }

        if (!config.getBoolean("origin-stone.enable", true)) return

        simpleCommand(
            "giveoriginstone",
            permission = "2b2tcore.give.originstone",
            permissionDefault = PermissionDefault.OP
        ) { sender, _ ->
            val player = sender as? Player ?: return@simpleCommand
            val item = OriginStone.createBody()
            player.inventory.addItem(item)
            player.msg("&a已给予起源石")
        }

        if (!config.getBoolean("warp-stone.enable", true)) return

        simpleCommand(
            "givewarpstone",
            permission = "2b2tcore.give.warpstone",
            permissionDefault = PermissionDefault.OP
        ) { sender, _ ->
            val player = sender as? Player ?: return@simpleCommand
            val item = WarpStone.createBody()
            player.inventory.addItem(item)
            player.msg("&a已给予瞬移石")
        }

        simpleCommand(
            "givepearl",
            permission = "2b2tcore.give.pearl",
            permissionDefault = PermissionDefault.OP
        ) { sender, args ->
            val player = sender as? Player ?: return@simpleCommand
            val level = args.getOrNull(0)?.toIntOrNull()?.coerceIn(1, WarpStoneItems.MAX_LEVEL)
                ?: WarpStoneItems.MAX_LEVEL
            val amount = args.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 64) ?: 1
            val item = WarpStoneItems.createPearl(level)
            item.amount = amount
            player.inventory.addItem(item)
            player.msg("&a已给予 ${amount} 个 ${WarpStoneItems.levelName(level)}压缩珍珠")
        }
    }
}
