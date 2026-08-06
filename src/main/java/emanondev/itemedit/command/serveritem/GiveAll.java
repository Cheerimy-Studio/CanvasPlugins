package emanondev.itemedit.command.serveritem;

import emanondev.itemedit.ItemEdit;
import emanondev.itemedit.Util;
import emanondev.itemedit.UtilsString;
import emanondev.itemedit.aliases.Aliases;
import emanondev.itemedit.command.ServerItemCommand;
import emanondev.itemedit.command.SubCmd;
import emanondev.itemedit.utility.CompleteUtility;
import emanondev.itemedit.utility.InventoryUtils;
import emanondev.itemedit.utility.ItemUtils;
import emanondev.itemedit.utility.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class GiveAll extends SubCmd {

    public GiveAll(ServerItemCommand cmd) {
        super("giveall", cmd, false, false);
    }

    @Override
    public void onCommand(@NotNull CommandSender sender, @NotNull String alias, String[] args) {
        try {
            if (Bukkit.getOnlinePlayers().isEmpty()) {
                return;
            }
            // <id> [amount] [silent]
            if (args.length < 2 || args.length > 4) {
                throw new IllegalArgumentException("Wrong param number");
            }
            Boolean silent = args.length == 4 ? (Aliases.BOOLEAN.convertAlias(args[3])) : ((Boolean) false);
            if (silent == null) {
                silent = Boolean.valueOf(args[3]);
            }
            int amount = args.length >= 3 ? Integer.parseInt(args[2]) : 1;
            if (amount < 1) {
                throw new IllegalArgumentException("Wrong amount number");
            }
            ItemStack item = ItemEdit.get().getServerStorage().getItem(args[1]);
            final boolean replaceHolders = ItemEdit.get().getConfig().loadBoolean("serveritem.replace-holders", true);
            final boolean dropExcess = ItemEdit.get().getConfig().loadBoolean("serveritem.give-drops-excess", true);
            final String nick = ItemEdit.get().getServerStorage().getNick(args[1]);
            AtomicInteger total = new AtomicInteger(0);
            for (Player target : Bukkit.getOnlinePlayers()) {
                ItemStack playerItem = item.clone();
                if (replaceHolders) {
                    ItemMeta playerMeta = ItemUtils.getMeta(playerItem);
                    List<String> playerLore = playerMeta.hasLore() ? playerMeta.getLore() : null;
                    String playerTitle = playerMeta.hasDisplayName() ? playerMeta.getDisplayName() : null;
                    playerMeta.setDisplayName(UtilsString.fix(playerTitle, target, true, "%player_name%",
                            target.getName(), "%player_uuid%", target.getUniqueId().toString()));
                    playerMeta.setLore(UtilsString.fix(playerLore, target, true, "%player_name%", target.getName(),
                            "%player_uuid%", target.getUniqueId().toString()));
                    playerItem.setItemMeta(playerMeta);
                }
                final ItemStack finalItem = playerItem;
                final boolean finalSilent = silent;
                SchedulerUtils.run(ItemEdit.get(), target, () -> {
                    if (!target.isOnline()) return;
                    int given = InventoryUtils.giveAmount(target, finalItem, amount,
                            dropExcess ? InventoryUtils.ExcessMode.DROP_EXCESS : InventoryUtils.ExcessMode.DELETE_EXCESS);
                    total.addAndGet(given);
                    if (given > 0 && !finalSilent) {
                        sendLanguageString("feedback", null, target, "%id%", args[1].toLowerCase(),
                                "%nick%", nick, "%amount%", String.valueOf(given));
                    }
                });
            }

            SchedulerUtils.runLater(ItemEdit.get(), 2L, () -> {
                if (total.get() > 0 && ItemEdit.get().getConfig().loadBoolean("log.action.giveall", true)) {
                    StringBuilder sb = new StringBuilder("[");
                    for (Player target : Bukkit.getOnlinePlayers()) {
                        sb.append(target.getName()).append(", ");
                    }

                    String msg = UtilsString.fix(this.getConfigString("log"), null, true, "%id%", args[1].toLowerCase(),
                            "%nick%", nick, "%amount%",
                            amount + " (for a total of " + total.get() + " given)", "%targets%", sb.delete(sb.length() - 2, sb.length()).append("]").toString());
                    if (ItemEdit.get().getConfig().loadBoolean("log.console", true)) {
                        Util.sendMessage(Bukkit.getConsoleSender(), msg);
                    }
                    if (ItemEdit.get().getConfig().loadBoolean("log.file", true)) {
                        Util.logToFile(msg);
                    }
                }
            });
        } catch (Exception e) {
            onFail(sender, alias);
        }
    }

    @Override
    public List<String> onComplete(@NotNull CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }
        switch (args.length) {
            // <id> [amount] [silent]
            case 2:
                return CompleteUtility.complete(args[1], ItemEdit.get().getServerStorage().getIds());
            case 3:
                return CompleteUtility.complete(args[2], Arrays.asList("1", "10", "64", "576", "2304"));
            case 4:
                return CompleteUtility.complete(args[3], Aliases.BOOLEAN);
        }
        return Collections.emptyList();
    }

}
