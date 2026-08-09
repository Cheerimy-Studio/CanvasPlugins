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
            Object server = org.bukkit.Bukkit.getServer();
            java.lang.reflect.Method getGlobal = server.getClass().getMethod("getGlobalRegionScheduler");
            Object globalScheduler = getGlobal.invoke(server);
            java.lang.reflect.Method runFixed = globalScheduler.getClass().getMethod("runAtFixedRate",
                    org.bukkit.plugin.Plugin.class, java.util.function.Consumer.class, long.class, long.class);
            Object task = runFixed.invoke(globalScheduler, plugin,
                    (java.util.function.Consumer<Object>) ignored -> runnable.run(), 0L, l);
            bukkitTaskList.add(task);
        } catch (Exception fallback) {
            // 静默回退：Folia 26.2 不支持 runTaskTimer，忽略非关键定时任务
        }

    }
}
