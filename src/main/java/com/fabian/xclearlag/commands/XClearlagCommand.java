package com.fabian.xclearlag.commands;

import com.fabian.xclearlag.XClearlag;
import com.fabian.xclearlag.api.CleanupReason;
import com.fabian.xclearlag.managers.*;

import com.fabian.xclearlag.utils.MetricsTracker;
import com.fabian.xclearlag.utils.DebugLogger;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Handles all subcommands for /xcl.
 */
public class XClearlagCommand implements CommandExecutor, TabCompleter {

    private final XClearlag plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss");

    public XClearlagCommand(XClearlag plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        DebugLogger.debug("Command", "Command received: /xcl " + String.join(" ", args) + " from " + sender.getName());
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "help":
                sendHelp(sender);
                break;
            case "reload":
                handleReload(sender);
                break;
            case "clear":
                handleClear(sender, args);
                break;
            case "lag":
                handleLag(sender);
                break;
            case "tps":
                handleTps(sender);
                break;
            case "inspect":
                handleInspect(sender, args);
                break;
            case "clearchunk":
                handleClearChunk(sender, args);
                break;
            case "tpchunk":
                handleTpChunk(sender, args);
                break;
            case "update":
                handleUpdate(sender);
                break;
            case "stats":
                handleStats(sender);
                break;
            case "debug":
                boolean currentDebug = plugin.getConfig().getBoolean("debug", false);
                plugin.getConfig().set("debug", !currentDebug);
                plugin.saveConfig();
                plugin.getConfigManager().load();
                sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "debug-toggled").replace("%state%", currentDebug ? "disabled" : "enabled"));
                break;
            default:
                sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "unknown-command"));
                break;
        }

        return true;
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("xclearlag.admin.reload")) {
            sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "no-permission"));
            return;
        }
        plugin.reload();
        sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "reload-success"));
    }

    private void handleClear(CommandSender sender, String[] args) {
        DebugLogger.debug("Command", "Handling clear command from " + sender.getName());
        if (!sender.hasPermission("xclearlag.admin.clear")) {
            sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "no-permission"));
            return;
        }

        XConfig config = plugin.getConfigManager().get();
        if (!config.manualClear.enabled) {
            sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "manual-clear-disabled"));
            return;
        }

        if (sender instanceof Player) {
            UUID uuid = ((Player) sender).getUniqueId();
            if (cooldowns.containsKey(uuid)) {
                long left = (cooldowns.get(uuid) + (config.manualClear.cooldown * 1000L)) - System.currentTimeMillis();
                if (left > 0) {
                    sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "cooldown", "%time%", String.valueOf(left / 1000L)));
                    return;
                }
            }
            cooldowns.put(uuid, System.currentTimeMillis());
        }

        if (args.length > 1 && config.manualClear.allowSpecific) {
            String taskName = args[1].toLowerCase();
            DebugLogger.debug("Command", "Clearing specific task: " + taskName);
            ClearTask task = plugin.getTaskManager().getTaskMap().get(taskName);
            if (task == null) {
                sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "invalid-task", "%task%", taskName));
                return;
            }
            task.executeCleanup(sender, false, CleanupReason.MANUAL_TRIGGERED);
        } else {
            if (config.manualClear.tasks.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "No tasks configured for manual-clear in config.yml!");
                return;
            }
            
            DebugLogger.debug("Command", "Clearing all manual tasks: " + config.manualClear.tasks);
            sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "global-clear-start"));
            
            // Execute all configured manual tasks
            for (String name : config.manualClear.tasks) {
                ClearTask task = plugin.getTaskManager().getTaskMap().get(name.toLowerCase());
                if (task != null) {
                    task.executeCleanup(sender, true, CleanupReason.MANUAL_TRIGGERED);
                }
            }
        }
    }

    private void handleLag(CommandSender sender) {
        if (!sender.hasPermission("xclearlag.admin.lag")) {
            sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "no-permission"));
            return;
        }

        double tps = plugin.getTpsMonitor().getTPS();
        String tpsColor = tps >= 18 ? "&a" : (tps >= 15 ? "&e" : "&c");

        sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "lag-header"));
        sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "lag-tps", 
            "%color%", tpsColor, 
            "%tps%", String.format("%.2f", tps)));
        sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "lag-memory", 
            "%used%", String.valueOf(getUsedMemory()), 
            "%max%", String.valueOf(getMaxMemory())));
    }

    private void handleStats(CommandSender sender) {
        if (!sender.hasPermission("xclearlag.admin.stats")) {
            sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "no-permission"));
            return;
        }

        MetricsTracker metrics = plugin.getMetricsTracker();
        sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "stats-header"));
        sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "stats-total", "%count%", String.valueOf(metrics.getTotalRemoved())));
        sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "stats-average", "%avg%", String.format("%.1f", metrics.getAverageRemoved())));
        
        List<MetricsTracker.CleanupRecord> history = metrics.getHistory();
        if (history.isEmpty()) {
            sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "stats-no-data"));
        } else {
            sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "stats-history-header"));
            for (int i = 0; i < Math.min(5, history.size()); i++) {
                MetricsTracker.CleanupRecord r = history.get(i);
                String time = df.format(new Date(r.timestamp));
                sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "stats-history-entry",
                    "%time%", time,
                    "%task%", r.taskName,
                    "%count%", String.valueOf(r.removed),
                    "%tps%", String.format("%.1f", r.tpsAtTime)));
            }
        }
    }

    private void handleTps(CommandSender sender) {
        double tps = plugin.getTpsMonitor().getTPS();
        sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "tps-format", "%tps%", String.format("%.2f", tps)));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "help-header"));
        sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "help-help"));
        sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "help-clear"));
        sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "help-lag"));
        sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "help-stats"));
        sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "help-inspect"));
        sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "help-clearchunk"));
        sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "help-tpchunk"));
        sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "help-update"));
        sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "help-reload"));
    }

    private void handleInspect(CommandSender sender, String[] args) {
        if (!sender.hasPermission("xclearlag.admin.inspect")) {
            sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "no-permission"));
            return;
        }

        Chunk chunk = getTargetChunk(sender, args, 1);
        if (chunk == null) return;

        int items = 0;
        int mobs = 0;
        int total = 0;

        for (Entity entity : chunk.getEntities()) {
            total++;
            if (entity instanceof Item) items++;
            else if (entity instanceof LivingEntity && !(entity instanceof Player)) mobs++;
        }

        sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "inspect-format", 
            "%world%", chunk.getWorld().getName(),
            "%x%", String.valueOf(chunk.getX()),
            "%z%", String.valueOf(chunk.getZ()),
            "%items%", String.valueOf(items),
            "%mobs%", String.valueOf(mobs),
            "%total%", String.valueOf(total)));
    }

    private void handleClearChunk(CommandSender sender, String[] args) {
        if (!sender.hasPermission("xclearlag.admin.clearchunk")) {
            sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "no-permission"));
            return;
        }

        Chunk chunk = getTargetChunk(sender, args, 1);
        if (chunk == null) return;

        int count = 0;
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Item || (entity instanceof LivingEntity && !(entity instanceof Player))) {
                entity.remove();
                count++;
            }
        }

        sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "clearchunk-success", 
            "%count%", String.valueOf(count),
            "%x%", String.valueOf(chunk.getX()),
            "%z%", String.valueOf(chunk.getZ())));
    }

    private void handleTpChunk(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "player-only"));
            return;
        }

        if (!sender.hasPermission("xclearlag.admin.tpchunk")) {
            sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "no-permission"));
            return;
        }

        Player player = (Player) sender;
        Chunk chunk = getTargetChunk(sender, args, 1);
        if (chunk == null) return;

        int x = (chunk.getX() << 4) + 8;
        int z = (chunk.getZ() << 4) + 8;
        int y = chunk.getWorld().getHighestBlockYAt(x, z);

        player.teleport(new org.bukkit.Location(chunk.getWorld(), x + 0.5, y + 1, z + 0.5));
        sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "tpchunk-success", 
            "%x%", String.valueOf(chunk.getX()),
            "%z%", String.valueOf(chunk.getZ())));
    }

    private void handleUpdate(CommandSender sender) {
        if (!sender.hasPermission("xclearlag.admin.update")) {
            sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "no-permission"));
            return;
        }
        plugin.getUpdateChecker().checkForUpdates(sender);
    }

    private Chunk getTargetChunk(CommandSender sender, String[] args, int startIndex) {
        World world;
        int cx, cz;

        if (args.length <= startIndex) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "player-only"));
                return null;
            }
            Player p = (Player) sender;
            return p.getLocation().getChunk();
        }

        try {
            if (args.length >= startIndex + 3) {
                world = Bukkit.getWorld(args[startIndex]);
                if (world == null) {
                    sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "invalid-world", "%world%", args[startIndex]));
                    return null;
                }
                cx = Integer.parseInt(args[startIndex + 1]);
                cz = Integer.parseInt(args[startIndex + 2]);
            } else if (args.length >= startIndex + 2) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "player-only"));
                    return null;
                }
                world = ((Player) sender).getWorld();
                cx = Integer.parseInt(startIndex == 1 ? args[1] : args[startIndex]);
                cz = Integer.parseInt(startIndex == 1 ? args[2] : args[startIndex + 1]);
            } else {
                sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "command-usage"));
                return null;
            }
            return world.getChunkAt(cx, cz);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getLanguageManager().getWithContext(sender, "invalid-coordinates"));
            return null;
        }
    }

    private long getUsedMemory() {
        return (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024;
    }

    private long getMaxMemory() {
        return Runtime.getRuntime().maxMemory() / 1024 / 1024;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("help", "reload", "clear", "lag", "stats", "inspect", "clearchunk", "tpchunk", "update", "debug"), args[0]);
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("clear")) {
                return filter(new ArrayList<>(plugin.getTaskManager().getTaskMap().keySet()), args[1]);
            }
            if (args[0].equalsIgnoreCase("inspect") || args[0].equalsIgnoreCase("clearchunk") || args[0].equalsIgnoreCase("tpchunk")) {
                List<String> worlds = new ArrayList<>();
                for (World w : Bukkit.getWorlds()) worlds.add(w.getName());
                return filter(worlds, args[1]);
            }
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> list, String arg) {
        List<String> result = new ArrayList<>();
        for (String s : list) {
            if (s.toLowerCase().startsWith(arg.toLowerCase())) result.add(s);
        }
        return result;
    }
}
