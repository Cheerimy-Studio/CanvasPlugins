package cc.baka9.catseedlogin.bukkit;

import cc.baka9.catseedlogin.bukkit.database.Cache;
import cc.baka9.catseedlogin.bukkit.object.LoginPlayer;
import cc.baka9.catseedlogin.bukkit.object.LoginPlayerHelper;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.logging.Level;

/**
 * 接收 Velocity 端 PluginMessage。
 * <p>
 * Velocity 端通过 {@code PreLoginEvent.forceOnlineMode()} 强制联系 Mojang
 * 获取真实 UUID，然后通过 PluginMessage 通知 Bukkit 自动注册/登录。
 * <p>
 * 消息格式：{@code PREMIUM:玩家名}
 */
public class VelocityReceiver implements PluginMessageListener {

    private static final SecureRandom RNG = new SecureRandom();
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!VelocityTransfer.CHANNEL.equals(channel)) return;

        String text;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            text = in.readUTF();
        } catch (IOException e) {
            CatSeedLogin.instance.getLogger().log(Level.WARNING, "Velocity PluginMessage 解析失败", e);
            return;
        }

        if (text == null || !text.startsWith("PREMIUM:")) return;
        String name = text.substring(8);

        // 调度到主线程
        Scheduler.runGlobal(CatSeedLogin.instance, () -> {
            autoLogin(name);
        });
    }

    /**
     * 为正版玩家自动注册（首次）或登录（已有账号）。
     */
    private static void autoLogin(String name) {
        if (LoginPlayerHelper.isLogin(name)) return;

        Player player = CatSeedLogin.instance.getServer().getPlayerExact(name);
        if (player == null) return;

        LoginPlayer lp = Cache.getIgnoreCase(name);
        if (lp == null) {
            // 首次连接 → 自动注册
            lp = new LoginPlayer(name, generatePassword());
            lp.crypt();
            try {
                CatSeedLogin.sql.add(lp);
            } catch (Exception e) {
                CatSeedLogin.instance.getLogger().log(Level.SEVERE, "正版自动注册失败", e);
                return;
            }
            Cache.refresh(name);
        }

        // 自动登录
        LoginPlayerHelper.add(lp);
        LoginPlayerHelper.recordCurrentIP(player, lp);
        VelocityTransfer.notifyLoggedIn(player);
        CatSeedLogin.instance.getLogger().info("CatSeedLogin: " + name + " 正版自动登录成功");
    }

    private static String generatePassword() {
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(CHARS.charAt(RNG.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
