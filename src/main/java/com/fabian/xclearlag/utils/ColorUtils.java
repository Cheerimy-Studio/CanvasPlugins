package com.fabian.xclearlag.utils;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtils {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern SPIGOT_HEX_PATTERN = Pattern.compile("(?i)&x(&[A-Fa-f0-9]){6}");

    private static boolean papiAvailable = false;
    private static Method setPlaceholdersMethod;

    static {
        try {
            Class<?> papiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            setPlaceholdersMethod = papiClass.getMethod("setPlaceholders", org.bukkit.OfflinePlayer.class, String.class);
            papiAvailable = true;
        } catch (Exception ignored) {}
    }

    public static String translateColors(String text) {
        if (text == null || text.isEmpty()) return text;
        text = convertHexColors(text);
        text = ChatColor.translateAlternateColorCodes('&', text);
        return text;
    }

    public static String format(Player player, String text) {
        if (text == null || text.isEmpty()) return "";
        text = applyPapi(player, text);
        return translateColors(text);
    }

    public static String convertLegacyAndHex(String text) {
        if (text == null || text.isEmpty()) return text;
        return translateColors(text);
    }

    private static String convertHexColors(String text) {
        if (text == null || text.isEmpty()) return text;

        // Convert Spigot hex format &x&R&R&G&G&B&B to &#RRGGBB first
        Matcher spigotMatcher = SPIGOT_HEX_PATTERN.matcher(text);
        StringBuilder spigotBuilder = new StringBuilder();
        while (spigotMatcher.find()) {
            String hex = spigotMatcher.group().replaceAll("[&xX]", "");
            spigotMatcher.appendReplacement(spigotBuilder, "&#" + hex);
        }
        spigotMatcher.appendTail(spigotBuilder);
        text = spigotBuilder.toString();

        // Convert &#RRGGBB to section-sign hex format §x§R§R§G§G§B§B
        Matcher hexMatcher = HEX_PATTERN.matcher(text);
        StringBuilder builder = new StringBuilder();
        while (hexMatcher.find()) {
            String hex = hexMatcher.group(1);
            StringBuilder hexBuilder = new StringBuilder("\u00a7x");
            for (char c : hex.toCharArray()) {
                hexBuilder.append('\u00a7').append(c);
            }
            hexMatcher.appendReplacement(builder, hexBuilder.toString());
        }
        hexMatcher.appendTail(builder);
        return builder.toString();
    }

    public static String applyPapi(Player player, String text) {
        if (text == null || text.isEmpty()) return text;
        if (player != null && papiAvailable && setPlaceholdersMethod != null) {
            try {
                text = (String) setPlaceholdersMethod.invoke(null, player, text);
            } catch (Exception ignored) {}
        }
        return text;
    }

    public static void sendComponent(CommandSender sender, String message) {
        sender.sendMessage(message);
    }

    public static boolean isPAPIAvailable() { return papiAvailable; }
}