/*
 * SadAC Inventory Guard — 违禁物品黑名单
 *
 * 功能:
 * - 从 BanItemConfig.yml 加载违禁物品 Material 列表
 * - 玩家点击背包 / 使用 / 消耗物品时检查并移除违禁物品
 * - 玩家登录时扫描并清除背包/装备中的违禁物品
 * - 支持运行时添加/移除: /sad banitem add|remove <material>
 *
 * Paper 26.2 / Folia 兼容（事件均在区域线程触发）
 */

package fr.neatmonster.nocheatplus.sadac;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * SadAC 原版违禁物品管理
 *
 * 配置文件: plugins/NoCheatPlus/BanItemConfig.yml
 */
public class SadInventoryGuard implements Listener {

    private final File configFile;
    private final Set<Material> bannedMaterials = new HashSet<>();

    public SadInventoryGuard(final JavaPlugin plugin) {
        this.configFile = new File(plugin.getDataFolder(), "BanItemConfig.yml");
        loadBanList();
    }

    /* ================================================================
     * 事件处理
     * ================================================================ */

    // 玩家登录时扫描并清除违禁物品（静默）
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(final PlayerJoinEvent event) {
        if (bannedMaterials.isEmpty()) return;
        final Player player = event.getPlayer();
        purgeInventory(player);
    }

    // 点击背包
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        final ItemStack item = event.getCurrentItem();
        if (item == null || item.getType().isAir()) return;
        if (!bannedMaterials.contains(item.getType())) return;

        event.setCancelled(true);
        event.getInventory().setItem(event.getSlot(), null);
    }

    // 使用物品
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemUse(final PlayerInteractEvent event) {
        final ItemStack item = event.getItem();
        if (item == null || !bannedMaterials.contains(item.getType())) return;

        event.setCancelled(true);
        event.getPlayer().getInventory().remove(item);
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

    public void reloadConfig() {
        loadBanList();
    }

    /* ── 内部工具 ── */

    /** 扫描玩家背包 + 装备栏，静默清除违禁物品 */
    private void purgeInventory(final Player player) {
        final PlayerInventory inv = player.getInventory();
        boolean changed = false;

        for (int i = 0; i < inv.getSize(); i++) {
            final ItemStack item = inv.getItem(i);
            if (item != null && bannedMaterials.contains(item.getType())) {
                inv.setItem(i, null);
                changed = true;
            }
        }
        for (final ItemStack armor : inv.getArmorContents()) {
            if (armor != null && bannedMaterials.contains(armor.getType())) {
                armor.setType(Material.AIR);
                changed = true;
            }
        }
        final ItemStack offhand = inv.getItemInOffHand();
        if (offhand != null && bannedMaterials.contains(offhand.getType())) {
            offhand.setType(Material.AIR);
            changed = true;
        }
        // 忽略 changed：静默运行，不刷日志
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
                // 未知物品名静默跳过，避免刷日志
            }
        }
    }

    private void saveBanList() {
        final YamlConfiguration yml = new YamlConfiguration();
        final List<String> names = new ArrayList<>();
        for (final Material mat : bannedMaterials) {
            names.add(mat.name());
        }
        yml.set("banned-items", names);
        try {
            yml.save(configFile);
        } catch (final IOException e) {
            // 静默：持久化失败不打扰运行
        }
    }
}
