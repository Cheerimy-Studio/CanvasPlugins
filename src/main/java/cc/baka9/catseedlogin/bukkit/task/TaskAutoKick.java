package cc.baka9.catseedlogin.bukkit.task;

import cc.baka9.catseedlogin.bukkit.Config;
import cc.baka9.catseedlogin.bukkit.Scheduler;
import cc.baka9.catseedlogin.bukkit.database.Cache;
import cc.baka9.catseedlogin.bukkit.object.LoginPlayerHelper;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TaskAutoKick extends Task {
    public Map<String, Long> playerJoinTime = new ConcurrentHashMap<>();

    @Override
    public void run(){
        if (!Cache.isLoaded || Config.Settings.AutoKick < 1) return;
        long autoKickMs = Config.Settings.AutoKick * 1000;
        long now = System.currentTimeMillis();
        // Folia: 每个玩家在其自己的区域线程上处理，避免跨区域访问
        for (Player player : Bukkit.getOnlinePlayers()) {
            Scheduler.runEntity(player, cc.baka9.catseedlogin.bukkit.CatSeedLogin.instance, p -> {
                String playerName = p.getName();
                if (!LoginPlayerHelper.isLogin(playerName)) {
                    if (playerJoinTime.containsKey(playerName)) {
                        if (now - playerJoinTime.get(playerName) > autoKickMs) {
                            p.kickPlayer(Config.Language.AUTO_KICK.replace("{time}", Config.Settings.AutoKick + ""));
                        }
                    } else {
                        playerJoinTime.put(playerName, now);
                    }
                } else {
                    playerJoinTime.remove(playerName);
                }
            });
        }
    }
}
