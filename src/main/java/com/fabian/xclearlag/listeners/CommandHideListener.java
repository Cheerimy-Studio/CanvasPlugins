package com.fabian.xclearlag.listeners;

import com.fabian.xclearlag.utils.DebugLogger;

import java.util.Collection;

public class CommandHideListener implements org.bukkit.event.Listener {

    // Only hide our own namespaced commands
    private static final String[] HIDDEN_PREFIXES = {
            "xclearlag:", "x-clearlag:"
    };

    public void onCommandSend(org.bukkit.event.Event event) {
        if (!event.getClass().getSimpleName().equals("PlayerCommandSendEvent")) {
            return;
        }

        DebugLogger.debug("CommandHide", "Filtering own namespaced commands for player");
        try {
            java.lang.reflect.Method getCommandsMethod = event.getClass().getMethod("getCommands");
            Object commandsObj = getCommandsMethod.invoke(event);

            if (commandsObj instanceof Collection) {
                @SuppressWarnings("unchecked")
                Collection<String> commands = (Collection<String>) commandsObj;

                commands.removeIf(command -> {
                    String lower = command.toLowerCase();
                    for (String prefix : HIDDEN_PREFIXES) {
                        if (lower.startsWith(prefix)) return true;
                    }
                    return false;
                });
            }
        } catch (Exception ignored) {
        }
    }
}