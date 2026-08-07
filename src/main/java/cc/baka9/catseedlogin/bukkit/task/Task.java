package cc.baka9.catseedlogin.bukkit.task;

import cc.baka9.catseedlogin.bukkit.CatSeedLogin;
import cc.baka9.catseedlogin.bukkit.Scheduler;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public abstract class Task implements Runnable {
    protected Task(){
    }

    private static TaskAutoKick taskAutoKick;
    private static TaskSendLoginMessage taskSendLoginMessage;
    private static List<Object> bukkitTaskList = new ArrayList<>();

    public static TaskAutoKick getTaskAutoKick(){
        if (taskAutoKick == null) {
            taskAutoKick = new TaskAutoKick();
        }
        return taskAutoKick;

    }

    public static TaskSendLoginMessage getTaskSendLoginMessage(){
        if (taskSendLoginMessage == null) {
            taskSendLoginMessage = new TaskSendLoginMessage();
        }
        return taskSendLoginMessage;

    }

    private static CatSeedLogin plugin = CatSeedLogin.instance;

    public static void runAll(){
        runTaskTimer(Task.getTaskSendLoginMessage(), 20 * 5);
        runTaskTimer(Task.getTaskAutoKick(), 20 * 5);

    }

    public static void cancelAll(){
        Iterator<Object> iterator = bukkitTaskList.iterator();
        while (iterator.hasNext()) {
            Object task = iterator.next();
            try {
                task.getClass().getMethod("cancel").invoke(task);
            } catch (Exception ignored) {
            }
            iterator.remove();
        }

    }

    public static void runTaskTimer(Runnable runnable, long l){
        // Folia: GlobalRegionScheduler.runAtFixedRate(plugin, task, 0, l)
        try {
            Object globalScheduler = org.bukkit.Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            Object task = globalScheduler.getClass().getMethod("runAtFixedRate", org.bukkit.plugin.Plugin.class, java.util.function.Consumer.class, long.class, long.class)
                    .invoke(globalScheduler, plugin, (java.util.function.Consumer<Object>) ignored -> runnable.run(), 0L, l);
            bukkitTaskList.add(task);
        } catch (Exception fallback) {
            // Spigot/Paper 旧版回退
            bukkitTaskList.add(plugin.getServer().getScheduler().runTaskTimer(plugin, runnable, 0, l));
        }

    }
}
