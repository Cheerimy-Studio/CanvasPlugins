/*
 * SadAC Inventory Guard — 违禁物品黑名单
 * 
 * 功能:
 * - 从 BanItemConfig.yml 加载违禁物品 ID 列表
 * - 玩家点击背包 / 使用物品时检查并移除违禁物品
 * - 支持运行时添加/移除: /sad banitem add|remove <material>
 *
 * Paper 26.2 / Folia 兼容
 */

package fr.neatmonster.nocheatplus.sadac;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

import fr.neatmonster.nocheatplus.logging.StaticLog;

/**
 * SadAC 原版违禁物品管理
 * 
 * 配置文件: plugins/NoCheatPlus/BanItemConfig.yml
 */
public class SadInventoryGuard implements Listener {

    private final File configFile;
    private final Set<Material> bannedMaterials = new HashSet<>();

    public SadInventoryGuard() {
        this.configFile = new File(
                Bukkit.getPluginManager().getPlugin("NoCheatPlus").getDataFolder(),
                "BanItemConfig.yml"
        );
        loadBanList();
    }

    /* ================================================================
     * 事件处理
     * ================================================================ */

    // 点击背包
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        final ItemStack item = event.getCurrentItem();
        if (item == null || item.getType().isAir()) return;
        if (!bannedMaterials.contains(item.getType())) return;

        event.setCancelled(true);
        event.getInventory().setItem(event.getSlot(), null);

        if (event.getWhoClicked() instanceof Player) {
            StaticLog.logInfo("[NCP+SadAC] 移除 " + event.getWhoClicked().getName()
                    + " 背包中的违禁物品: " + item.getType());
        }
    }

    // 使用物品
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemUse(final PlayerInteractEvent event) {
        final ItemStack item = event.getItem();
        if (item == null || !bannedMaterials.contains(item.getType())) return;

        event.setCancelled(true);
        event.getPlayer().getInventory().remove(item);

        StaticLog.logInfo("[NCP+SadAC] " + event.getPlayer().getName()
                + " 尝试使用违禁物品: " + item.getType());
    }

    // 消耗物品
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemConsume(final PlayerItemConsumeEvent event) {
        final ItemStack item = event.getItem();
        if (!bannedMaterials.contains(item.getType())) return;

        event.setCancelled(true);
        event.getPlayer().getInventory().remove(item);
    }

    /* ================================================================
     * 管理方法 — /sad banitem add|remove|list
     * ================================================================ */

    public boolean addBannedMaterial(final Material mat) {
        if (bannedMaterials.add(mat)) {
            saveBanList();
            return true;
        }
        return false;
    }

    public boolean removeBannedMaterial(final Material mat) {
        if (bannedMaterials.remove(mat)) {
            saveBanList();
            return true;
        }
        return false;
    }

    public Set<Material> getBannedMaterials() {
        return Collections.unmodifiableSet(bannedMaterials);
    }

    /* ── 持久化 ── */

    private void loadBanList() {
        bannedMaterials.clear();
        if (!configFile.exists()) {
            saveBanList(); // 创建默认空文件
            return;
        }

        final YamlConfiguration yml = YamlConfiguration.loadConfiguration(configFile);
        for (final String name : yml.getStringList("banned-items")) {
            try {
                final Material mat = Material.valueOf(name.toUpperCase());
                bannedMaterials.add(mat);
            } catch (final IllegalArgumentException e) {
                StaticLog.logWarning("[NCP+SadAC] 未知物品: " + name);
            }
        }
    }

    private void saveBanList() {
        final YamlConfiguration yml = new YamlConfiguration();
        final java.util.List<String> names = new java.util.ArrayList<>();
        for (final Material mat : bannedMaterials) {
            names.add(mat.name());
        }
        yml.set("banned-items", names);
        try {
            yml.save(configFile);
        } catch (final IOException e) {
            StaticLog.logSevere("[NCP+SadAC] 无法保存违禁物品列表: " + e.getMessage());
        }
    }
}
