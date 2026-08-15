package luminus.acng.features.gameplay.duplications

import luminus.acng.Main.config
import luminus.acng.features.gameplay.teleport.TeleportStone
import luminus.acng.msg
import org.bukkit.entity.Player
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.simpleCommand

/**
 * 命令复制：执行 /dupe 复制手持物品（权限：2b2tcore.dupe.command，默认仅 OP）
 * simpleCommand 在玩家区域线程执行，库存操作和掉落同步执行，Folia 安全
 */
object CommandDupe {

    @Awake(LifeCycle.ENABLE)
    fun init() {
        if (!config.getBoolean("duplication.command.enable")) return

        simpleCommand(
            "dupe",
            permission = "2b2tcore.dupe.command",
            permissionDefault = PermissionDefault.OP
        ) { sender, _ ->
            if (sender !is Player) return@simpleCommand

            val item = sender.inventory.itemInMainHand
            if (item.type.isAir) {
                config.getString("messages.no-item", "You must hold an item to dupe!")?.let { sender.msg(it) }
                return@simpleCommand
            }
            // 传送石只能通过展示框复制
            if (TeleportStone.isStone(item)) {
                sender.msg("&c传送石只能通过展示框复制！")
                return@simpleCommand
            }
            // 复制品不可被二次复制
            if (Replica.checkCanDupe(sender, item)) return@simpleCommand

            val multiplyTimes = config.getInt("duplication.command.multiply-times", 2)
            val maxAmount = config.getInt("duplication.command.max-amount", 2333)
            val amount = minOf(item.amount * multiplyTimes, maxAmount)
            if (amount <= 0) return@simpleCommand

            // 输出物品：默认打上复制品词条，拥有 2b2tcore.dupe.original 权限的玩家得到原版
            val newItem = Replica.output(sender, item.clone())
            newItem.amount = amount

            sender.inventory.addItem(newItem).forEach { (_, itemStack) ->
                sender.world.dropItemNaturally(sender.location, itemStack)
            }

            config.getString("messages.success-dupe", "Successfully duped")?.let { sender.msg(it) }
        }
    }
}
