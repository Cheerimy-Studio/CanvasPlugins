package cc.baka9.catseedlogin.bukkit;

import cc.baka9.catseedlogin.bukkit.command.*;
import cc.baka9.catseedlogin.bukkit.database.Cache;
import cc.baka9.catseedlogin.bukkit.database.MySQL;
import cc.baka9.catseedlogin.bukkit.database.SQL;
import cc.baka9.catseedlogin.bukkit.database.SQLite;
import cc.baka9.catseedlogin.bukkit.object.LoginPlayerHelper;
import cc.baka9.catseedlogin.bukkit.task.Task;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class CatSeedLogin extends JavaPlugin {

    public static CatSeedLogin instance;
    public static SQL sql;
    public static boolean loadProtocolLib = false;

    @Override
    public void onEnable(){
        instance = this;
        //鎺у埗鍙板瘑鐮侀殣钘忥紙Log4j2 Filter锛孭aper 鍦ㄥ懡浠や簨浠跺墠宸叉墦鏃ュ織锛屽繀椤荤敤杩囨护鍣ㄦ嫤鎴級
        ConsolePasswordFilter.install();
        //Config
        try {
            Config.load();
            Config.save();
        } catch (Exception e) {
            e.printStackTrace();
            getServer().getLogger().warning("鍔犺浇閰嶇疆鏂囦欢鏃跺嚭閿欙紝璇锋鏌ヤ綘鐨勯厤缃枃浠躲€?);
        }
        sql = Config.MySQL.Enable ? new MySQL(this) : new SQLite(this);
        try {

            sql.init();

            Cache.refreshAll();
        } catch (Exception e) {
            getLogger().warning("搂c鍔犺浇鏁版嵁搴撴椂鍑洪敊");
            e.printStackTrace();
        }
        // 娉ㄥ唽 Plugin Message 閫氶亾锛圴elocity 閫氫俊锛?
        Bukkit.getServer().getMessenger().registerOutgoingPluginChannel(this, VelocityTransfer.CHANNEL);
        Bukkit.getServer().getMessenger().registerIncomingPluginChannel(this, VelocityTransfer.CHANNEL, new VelocityReceiver());

        //Listeners
        getServer().getPluginManager().registerEvents(new Listeners(), this);

        //ProtocolLibListeners
        try {
            Class.forName("com.comphenix.protocol.ProtocolLib");
            ProtocolLibListeners.enable();
            loadProtocolLib = true;
        } catch (ClassNotFoundException e) {
            getLogger().warning("鏈嶅姟鍣ㄦ病鏈夎杞絇rotocolLib鎻掍欢锛岃繖灏嗘棤娉曚娇鐢ㄧ櫥褰曞墠闅愯棌鑳屽寘");
        }

        // bc
        if (Config.BungeeCord.Enable) {
            Communication.socketServerStartAsync();
        }

        //Commands
        getServer().getPluginCommand("login").setExecutor(new CommandLogin());
        getServer().getPluginCommand("login").setTabCompleter((commandSender, command, s, args)
                -> args.length == 1 ? Collections.singletonList("瀵嗙爜") : new ArrayList<>(0));

        getServer().getPluginCommand("register").setExecutor(new CommandRegister());
        getServer().getPluginCommand("register").setTabCompleter((commandSender, command, s, args)
                -> args.length == 1 ? Collections.singletonList("瀵嗙爜 閲嶅瀵嗙爜") : new ArrayList<>(0));

        getServer().getPluginCommand("changepassword").setExecutor(new CommandChangePassword());
        getServer().getPluginCommand("changepassword").setTabCompleter((commandSender, command, s, args)
                -> args.length == 1 ? Collections.singletonList("鏃у瘑鐮?鏂板瘑鐮?閲嶅鏂板瘑鐮?) : new ArrayList<>(0));

        PluginCommand bindemail = getServer().getPluginCommand("bindemail");
        bindemail.setExecutor(new CommandBindEmail());
        bindemail.setTabCompleter((commandSender, command, s, args) -> {
            if (args.length == 1) {
                return Arrays.asList("set 闇€瑕佺粦瀹氱殑閭", "verify 閭楠岃瘉鐮?);
            }
            if (args.length == 2) {
                if (args[0].equals("set")) {
                    return Collections.singletonList("闇€瑕佺粦瀹氱殑閭");
                }
                if (args[0].equals("verify")) {
                    return Collections.singletonList("閭鑾峰彇鐨勯獙璇佺爜");
                }
            }
            return Collections.emptyList();
        });
        PluginCommand resetpassword = getServer().getPluginCommand("resetpassword");
        resetpassword.setExecutor(new CommandResetPassword());
        resetpassword.setTabCompleter((commandSender, command, s, args) -> {
            if (args.length == 1) {
                return Arrays.asList("forget", "re 楠岃瘉鐮?鏂板瘑鐮?);
            }
            if (args[0].equals("re")) {
                if (args.length == 2) {
                    return Collections.singletonList("楠岃瘉鐮?鏂板瘑鐮?);
                }
                if (args.length == 3) {
                    return Collections.singletonList("鏂板瘑鐮?);
                }
            }
            return Collections.emptyList();
        });
        PluginCommand catseedlogin = getServer().getPluginCommand("catseedlogin");
        catseedlogin.setExecutor(new CommandCatSeedLogin());

        //Task
        Task.runAll();

    }


    @Override
    public void onDisable(){
        Bukkit.getOnlinePlayers().forEach(p -> {
            if (!LoginPlayerHelper.isLogin(p.getName())) return;
            if (!p.isDead() || Config.Settings.DeathStateQuitRecordLocation) {
                Config.setOfflineLocation(p);
            }

        });
        try {
            sql.getConnection().close();
        } catch (Exception e) {
            getLogger().warning("鑾峰彇鏁版嵁搴撹繛鎺ユ椂鍑洪敊");
            e.printStackTrace();
        }
        Communication.socketServerStop();
        super.onDisable();
    }

    public void runTaskAsync(Runnable runnable){
        Scheduler.runAsync(this, runnable);
    }


}
