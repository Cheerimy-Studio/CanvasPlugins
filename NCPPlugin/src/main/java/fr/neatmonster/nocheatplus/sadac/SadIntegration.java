/*
 * SadAC 整合入口 — 注册监听器 + /sad 命令 + 配置加载
 *
 * 集成方式: 在 NoCheatPlus.onEnable() 中调用 SadIntegration.enable(plugin)
 */

package fr.neatmonster.nocheatplus.sadac;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import fr.neatmonster.nocheatplus.compat.SchedulerHelper;
import fr.neatmonster.nocheatplus.config.ConfigFile;
import fr.neatmonster.nocheatplus.config.ConfigManager;
import fr.neatmonster.nocheatplus.logging.StaticLog;
import fr.neatmonster.nocheatplus.logging.Streams;

/**
 * SadAC 功能集成（Lag 检测 + 违禁物品 + 运行时命令）
 *
 * 使用: 在 NoCheatPlus.onEnable() 末尾调用 SadIntegration.enable(plugin);
 */
public class SadIntegration {

    private static SadLagListener lagListener;
    private static SadInventoryGuard inventoryGuard;

    /**
     * 初始化 SadAC 模块（注册监听器、加载配置、注册 /sad 命令）
     */
    public static void enable(final JavaPlugin plugin) {
        // ── 1. 注册 Lag 监听器 ──
        lagListener = new SadLagListener();
        lagListener.reloadConfig();
        Bukkit.getPluginManager().registerEvents(lagListener, plugin);

        // ── 2. 注册违禁物品监听器 ──
        inventoryGuard = new SadInventoryGuard();
        Bukkit.getPluginManager().registerEvents(inventoryGuard, plugin);

        // ── 3. 注册 /sad 命令 ──
        final PluginCommand cmd = plugin.getCommand("sad");
        if (cmd != null) {
            final SadCommandExecutor executor = new SadCommandExecutor();
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        StaticLog.logInfo("[NCP+SadAC] SadAC 模块已启用 — 高频红石/实体清理/违禁物品");
    }

    /**
     * 热重载 SadAC 配置
     */
    public static void reload() {
        if (lagListener != null) lagListener.reloadConfig();
    }

    /* ================================================================
     * /sad 命令执行器 + Tab 补全
     * ================================================================ */

    public static class SadCommandExecutor implements CommandExecutor, TabCompleter {

        private static final String PREFIX = ChatColor.GRAY + "[" + ChatColor.RED + "NCP+SadAC"
                + ChatColor.GRAY + "] " + ChatColor.WHITE;

        @Override
        public boolean onCommand(final CommandSender sender, final Command command,
                                 final String label, final String[] args) {
            if (!sender.hasPermission("nocheatplus.admin.sadac")) {
                sender.sendMessage(PREFIX + ChatColor.RED + "你没有权限使用此命令！(需要 nocheatplus.admin.sadac)");
                return true;
            }
            if (args.length < 2) {
                showUsage(sender);
                return true;
            }

            final String type = args[0].toLowerCase();
            final String action = args[1].toLowerCase();

            switch (type) {
                case "lag":
                    return handleLag(sender, action, args);
                case "banitem":
                    return handleBanItem(sender, action, args);
                case "reload":
                    SadIntegration.reload();
                    sender.sendMessage(PREFIX + ChatColor.GREEN + "SadAC 配置已重新加载");
                    return true;
                default:
                    sender.sendMessage(PREFIX + ChatColor.RED + "未知类型: " + type
                            + "，可用: lag | banitem | reload");
                    return true;
            }
        }

        private boolean handleLag(final CommandSender sender, final String action, final String[] args) {
            if (lagListener == null) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Lag 检测模块未初始化");
                return true;
            }
            switch (action) {
                case "on":
                    lagListener.setEnabled(true);
                    // Folia 安全：用 SchedulerHelper 在主线程重新加载配置
                    SchedulerHelper.runSyncTask(
                            Bukkit.getPluginManager().getPlugin("NoCheatPlus"),
                            (task) -> lagListener.reloadConfig()
                    );
                    sender.sendMessage(PREFIX + ChatColor.GREEN + "高频红石 & 实体清理已开启");
                    break;
                case "off":
                    lagListener.setEnabled(false);
                    sender.sendMessage(PREFIX + ChatColor.GREEN + "高频红石 & 实体清理已关闭");
                    break;
                case "set":
                    if (args.length < 4) {
                        sender.sendMessage(PREFIX + ChatColor.RED
                                + "用法: /sad lag set [redstone|entities] <值>");
                        return true;
                    }
                    final String param = args[2].toLowerCase();
                    try {
                        final int val = Integer.parseInt(args[3]);
                        switch (param) {
                            case "redstone":
                                lagListener.setRedstoneIntervalMs(val);
                                sender.sendMessage(PREFIX + ChatColor.GREEN
                                        + "高频红石间隔已设为: " + val + " ms");
                                break;
                            case "entities":
                                lagListener.setMaxEntitiesPerChunk(val);
                                sender.sendMessage(PREFIX + ChatColor.GREEN
                                        + "区块最大实体数已设为: " + val);
                                break;
                            default:
                                sender.sendMessage(PREFIX + ChatColor.RED
                                        + "未知参数: " + param + "，可用: redstone | entities");
                        }
                    } catch (NumberFormatException e) {
                        sender.sendMessage(PREFIX + ChatColor.RED + "无效数值: " + args[3]);
                    }
                    break;
                default:
                    sender.sendMessage(PREFIX + ChatColor.RED + "用法: /sad lag [on|off|set redstone|entities <value>]");
            }
            return true;
        }

        private boolean handleBanItem(final CommandSender sender, final String action, final String[] args) {
            if (inventoryGuard == null) {
                sender.sendMessage(PREFIX + ChatColor.RED + "违禁物品模块未初始化");
                return true;
            }
            switch (action) {
                case "add":
                    if (args.length < 3) {
                        sender.sendMessage(PREFIX + ChatColor.RED
                                + "用法: /sad banitem add <MATERIAL>  例如: /sad banitem add TNT");
                        return true;
                    }
                    try {
                        final Material mat = Material.valueOf(args[2].toUpperCase());
                        if (inventoryGuard.addBannedMaterial(mat)) {
                            sender.sendMessage(PREFIX + ChatColor.GREEN
                                    + "已添加违禁物品: " + mat.name());
                        } else {
                            sender.sendMessage(PREFIX + ChatColor.YELLOW
                                    + "该物品已在列表中: " + mat.name());
                        }
                    } catch (IllegalArgumentException e) {
                        sender.sendMessage(PREFIX + ChatColor.RED
                                + "无效物品名: " + args[2] + "（使用大写 Material 名，如 TNT, BEDROCK）");
                    }
                    break;
                case "remove":
                    if (args.length < 3) {
                        sender.sendMessage(PREFIX + ChatColor.RED
                                + "用法: /sad banitem remove <MATERIAL>");
                        return true;
                    }
                    try {
                        final Material mat = Material.valueOf(args[2].toUpperCase());
                        if (inventoryGuard.removeBannedMaterial(mat)) {
                            sender.sendMessage(PREFIX + ChatColor.GREEN
                                    + "已移除违禁物品: " + mat.name());
                        } else {
                            sender.sendMessage(PREFIX + ChatColor.YELLOW
                                    + "该物品不在列表中: " + mat.name());
                        }
                    } catch (IllegalArgumentException e) {
                        sender.sendMessage(PREFIX + ChatColor.RED
                                + "无效物品名: " + args[2]);
                    }
                    break;
                case "list":
                    final Set<Material> bans = inventoryGuard.getBannedMaterials();
                    if (bans.isEmpty()) {
                        sender.sendMessage(PREFIX + "违禁物品列表为空");
                    } else {
                        sender.sendMessage(PREFIX + "违禁物品 ("
                                + bans.size() + "): "
                                + bans.stream().map(Material::name).collect(Collectors.joining(", ")));
                    }
                    break;
                default:
                    sender.sendMessage(PREFIX + ChatColor.RED
                            + "用法: /sad banitem [add|remove|list]");
            }
            return true;
        }

        private void showUsage(final CommandSender sender) {
            sender.sendMessage(ChatColor.GRAY + "§6=== §cNCP+SadAC §6命令 §6===");
            sender.sendMessage(ChatColor.WHITE + "/sad lag on|off                 " + ChatColor.GRAY + "开关高频红石 & 实体清理");
            sender.sendMessage(ChatColor.WHITE + "/sad lag set redstone <ms>     " + ChatColor.GRAY + "设置红石频率阈值(ms)");
            sender.sendMessage(ChatColor.WHITE + "/sad lag set entities <count>  " + ChatColor.GRAY + "设置每区块最大实体数");
            sender.sendMessage(ChatColor.WHITE + "/sad banitem add|remove <材质> " + ChatColor.GRAY + "添加/移除违禁物品");
            sender.sendMessage(ChatColor.WHITE + "/sad banitem list               " + ChatColor.GRAY + "列出违禁物品");
            sender.sendMessage(ChatColor.WHITE + "/sad reload                     " + ChatColor.GRAY + "重载 SadAC 配置");
        }

        /* ── Tab 补全 ── */

        @Override
        public List<String> onTabComplete(final CommandSender sender, final Command command,
                                          final String alias, final String[] args) {
            if (args.length == 1) {
                return filter(Arrays.asList("lag", "banitem", "reload"), args[0]);
            }
            if (args.length == 2) {
                switch (args[0].toLowerCase()) {
                    case "lag":      return filter(Arrays.asList("on", "off", "set"), args[1]);
                    case "banitem":  return filter(Arrays.asList("add", "remove", "list"), args[1]);
                }
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("lag") && args[1].equalsIgnoreCase("set")) {
                return filter(Arrays.asList("redstone", "entities"), args[2]);
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("banitem") && args[1].equalsIgnoreCase("add")) {
                // 提供常见违禁物品建议
                return filter(Arrays.asList("TNT", "BEDROCK", "BARRIER", "COMMAND_BLOCK",
                        "STRUCTURE_BLOCK", "END_PORTAL_FRAME", "DRAGON_EGG"), args[2]);
            }
            return Collections.emptyList();
        }

        private static List<String> filter(final List<String> options, final String prefix) {
            final String lower = prefix.toLowerCase();
            return options.stream().filter(o -> o.toLowerCase().startsWith(lower)).collect(Collectors.toList());
        }
    }
}
