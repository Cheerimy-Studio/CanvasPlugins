package com.fabian.xclearlag.commands;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.UUID;

/**
 * A wrapper for ConsoleCommandSender that discards all messages sent to it.
 * Used to silence feedback from commands executed by the plugin.
 */
public class SilentCommandSender implements org.bukkit.command.ConsoleCommandSender {

    private final ConsoleCommandSender realSender = Bukkit.getConsoleSender();

    @Override
    public void sendMessage(String message) {
        // Do nothing - silence!
    }

    @Override
    public void sendMessage(String[] messages) {
        // Do nothing - silence!
    }

    // Compatibility for older/newer versions (not necessarily in CommandSender in
    // all versions)
    public void sendMessage(UUID uuid, String s) {
        // Do nothing - silence!
    }

    public void sendMessage(UUID uuid, String[] strings) {
        // Do nothing - silence!
    }

    @Override
    public Server getServer() {
        return realSender.getServer();
    }

    @Override
    public String getName() {
        return "X-Clearlag";
    }

    @Override
    public boolean isOp() {
        return true; // Always OP to execute cleanup commands
    }

    @Override
    public void setOp(boolean value) {
        // Ignore
    }

    @Override
    public boolean isPermissionSet(String name) {
        return realSender.isPermissionSet(name);
    }

    @Override
    public boolean isPermissionSet(Permission perm) {
        return realSender.isPermissionSet(perm);
    }

    @Override
    public boolean hasPermission(String name) {
        // Specifically disable command feedback logs/notifications
        if (name.equalsIgnoreCase("minecraft.command.feedback")) {
            return false;
        }
        return realSender.hasPermission(name);
    }

    @Override
    public boolean hasPermission(Permission perm) {
        if (perm.getName().equalsIgnoreCase("minecraft.command.feedback")) {
            return false;
        }
        return realSender.hasPermission(perm);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) {
        return realSender.addAttachment(plugin, name, value);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin) {
        return realSender.addAttachment(plugin);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) {
        return realSender.addAttachment(plugin, name, value, ticks);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, int ticks) {
        return realSender.addAttachment(plugin, ticks);
    }

    @Override
    public void removeAttachment(PermissionAttachment attachment) {
        realSender.removeAttachment(attachment);
    }

    @Override
    public void recalculatePermissions() {
        realSender.recalculatePermissions();
    }

    @Override
    public Set<PermissionAttachmentInfo> getEffectivePermissions() {
        return realSender.getEffectivePermissions();
    }

    @Override
    public void sendRawMessage(String message) {
        // Silence
    }

    // No @Override to avoid compile errors on older Bukkit versions
    public void sendRawMessage(java.util.UUID uuid, String message) {
        // Silence
    }

    @Override
    public boolean isConversing() {
        return false;
    }

    @Override
    public void acceptConversationInput(String input) {
        // Silence
    }

    @Override
    public boolean beginConversation(org.bukkit.conversations.Conversation conversation) {
        return false;
    }

    @Override
    public void abandonConversation(org.bukkit.conversations.Conversation conversation) {
        // Silence
    }

    @Override
    public void abandonConversation(org.bukkit.conversations.Conversation conversation,
            org.bukkit.conversations.ConversationAbandonedEvent event) {
        // Silence
    }
}
