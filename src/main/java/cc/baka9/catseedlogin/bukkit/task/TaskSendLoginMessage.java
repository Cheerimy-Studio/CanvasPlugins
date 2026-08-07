package cc.baka9.catseedlogin.bukkit.task;

import cc.baka9.catseedlogin.bukkit.Config;
import cc.baka9.catseedlogin.bukkit.Scheduler;
import cc.baka9.catseedlogin.bukkit.database.Cache;
import cc.baka9.catseedlogin.bukkit.object.LoginPlayerHelper;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;


public class TaskSendLoginMessage extends Task {

    @Override
    public void run(){
        if (!Cache.isLoaded) return;
        // Folia: 每个玩家在其自己的区域线程上发送消息
        for (Player player : Bukkit.getOnlinePlayers()) {
            Scheduler.runEntity(player, cc.baka9.catseedlogin.bukkit.CatSeedLogin.instance, p -> {
                if (!LoginPlayerHelper.isLogin(p.getName())) {
                    if (!LoginPlayerHelper.isRegister(p.getName())) {
                        p.sendMessage(Config.Language.REGISTER_REQUEST);
                        return;
                    }
                    p.sendMessage(Config.Language.LOGIN_REQUEST);
                }
            });
        }
    }
}
