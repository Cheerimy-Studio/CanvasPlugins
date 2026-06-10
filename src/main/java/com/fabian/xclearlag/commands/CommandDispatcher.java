package com.fabian.xclearlag.commands;

import com.fabian.xclearlag.XClearlag;
import com.fabian.xclearlag.utils.DebugLogger;


import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

/**
 * Dispatches commands with special handling for X-Clearlag bracket tags.
 * Supports 1.8-1.21 using reflection for titles and action bars.
 */
public class CommandDispatcher {

    private final XClearlag plugin;
    private String nmsVersion;

    public CommandDispatcher(XClearlag plugin) {
        this.plugin = plugin;
        try {
            this.nmsVersion = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
        } catch (Exception e) {
            this.nmsVersion = "v1_21_R1"; // Fallback to modern
        }
    }

    public void dispatch(String cmd, Collection<? extends Player> players, int secondsLeft) {
        if (cmd == null || cmd.trim().isEmpty()) return;
        cmd = cmd.trim();
        DebugLogger.debug("CommandDispatch", "Dispatching: " + cmd);

        if (handleSpecialCommand(cmd, players, secondsLeft)) return;

        try {
            Bukkit.dispatchCommand(new SilentCommandSender(), processPlaceholders(null, cmd, secondsLeft));
        } catch (Exception e) {
            plugin.getLogger().warning("Error executing command: " + cmd);
        }
    }

    public void dispatch(List<String> cmds, Collection<? extends Player> players, int secondsLeft) {
        if (cmds == null || cmds.isEmpty()) return;
        DebugLogger.debug("CommandDispatch", "Dispatching " + cmds.size() + " commands.");
        for (String cmd : cmds) {
            dispatch(cmd, players, secondsLeft);
        }
    }

    private boolean handleSpecialCommand(String cmd, Collection<? extends Player> players, int secondsLeft) {
        String upper = cmd.toUpperCase();
        
        if (upper.startsWith("[TITLE]")) {
            DebugLogger.debug("CommandDispatch", "Special command: [TITLE] for " + players.size() + " players");
            String title = cmd.substring(7).trim();
            for (Player p : players) {
                sendTitle(p, processPlaceholders(p, title, secondsLeft), "", 10, 40, 10);
            }
            return true;
        }
        
        if (upper.startsWith("[SUBTITLE]")) {
            String subtitle = cmd.substring(10).trim();
            for (Player p : players) {
                sendTitle(p, "", processPlaceholders(p, subtitle, secondsLeft), 10, 40, 10);
            }
            return true;
        }

        if (upper.startsWith("[ACTIONBAR]")) {
            String actionBar = cmd.substring(11).trim();
            for (Player p : players) {
                sendActionBar(p, processPlaceholders(p, actionBar, secondsLeft));
            }
            return true;
        }

        if (upper.startsWith("[BROADCAST]")) {
            Bukkit.broadcastMessage(processPlaceholders(null, cmd.substring(11).trim(), secondsLeft));
            return true;
        }

        if (upper.startsWith("[MESSAGE]")) {
            String msg = cmd.substring(9).trim();
            for (Player p : players) {
                p.sendMessage(processPlaceholders(p, msg, secondsLeft));
            }
            return true;
        }

        if (upper.startsWith("[SOUND]")) {
            String soundName = cmd.substring(7).trim();
            try {
                org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundName.toUpperCase());
                for (Player p : players) {
                    p.playSound(p.getLocation(), sound, 1.0f, 1.0f);
                }
            } catch (Exception ignored) {}
            return true;
        }

        return false;
    }

    private String processPlaceholders(Player player, String text, int secondsLeft) {
        String processed = ChatColor.translateAlternateColorCodes('&', text);
        processed = processed.replace("%time%", String.valueOf(secondsLeft));
        
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                processed = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, processed);
            } catch (Exception ignored) {}
        }
        
        return processed;
    }

    private void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        try {
            // Use reflection for sendTitle to handle different signatures
            Method sendTitle = player.getClass().getMethod("sendTitle", String.class, String.class, int.class, int.class, int.class);
            sendTitle.invoke(player, title, subtitle, fadeIn, stay, fadeOut);
        } catch (Exception e) {
            try {
                // Secondary check for older Spigot versions (2-parameter version)
                Method sendTitle = player.getClass().getMethod("sendTitle", String.class, String.class);
                sendTitle.invoke(player, title, subtitle);
            } catch (Exception ignored) {}
        }
    }

    private void sendActionBar(Player player, String message) {
        try {
            // Try modern Spigot API (1.11+)
            Method sendMessage = player.getClass().getMethod("spigot");
            Object spigot = sendMessage.invoke(player);
            
            Class<?> chatMessageTypeClass = Class.forName("net.md_5.bungee.api.ChatMessageType");
            @SuppressWarnings("unchecked")
            Object enumType = Enum.valueOf((Class<Enum>) chatMessageTypeClass, "ACTION_BAR");
            
            Class<?> baseComponentClass = Class.forName("net.md_5.bungee.api.chat.BaseComponent");
            Class<?> textComponentClass = Class.forName("net.md_5.bungee.api.chat.TextComponent");
            Object component = textComponentClass.getConstructor(String.class).newInstance(message);
            
            Method spigotSendMessage = spigot.getClass().getMethod("sendMessage", chatMessageTypeClass, baseComponentClass);
            spigotSendMessage.invoke(spigot, enumType, component);
            
        } catch (Exception e) {
            // NMS Fallback for 1.8
            try {
                Object handle = player.getClass().getMethod("getHandle").invoke(player);
                Object playerConnection = handle.getClass().getField("playerConnection").get(handle);
                
                Class<?> chatComponentClass = getNMSClass("IChatBaseComponent");
                Method a = getNMSClass("IChatBaseComponent$ChatSerializer").getMethod("a", String.class);
                Object component = a.invoke(null, "{\"text\":\"" + message + "\"}");
                
                Class<?> packetClass = getNMSClass("PacketPlayOutChat");
                Constructor<?> packetConstructor = packetClass.getConstructor(chatComponentClass, byte.class);
                Object packet = packetConstructor.newInstance(component, (byte) 2);
                
                playerConnection.getClass().getMethod("sendPacket", getNMSClass("Packet")).invoke(playerConnection, packet);
            } catch (Exception ignored) {}
        }
    }

    private Class<?> getNMSClass(String name) throws Exception {
        return Class.forName("net.minecraft.server." + nmsVersion + "." + name);
    }
}
