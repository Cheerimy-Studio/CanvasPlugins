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

/**
 * 接收 Velocity 代理端 PluginMessage 的通道。
 * <p>
 * 通道名 catseedlogin:transfer，消息格式：
 * <pre>PREMIUM:&lt;玩家名&gt;</pre>
 * 收到后自动为正版玩家注册/登录（若尚未登录）。
 */
public class VelocityReceiver implements PluginMessageListener {

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!VelocityTransfer.CHANNEL.equals(channel)) return;

        String text;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            text = in.readUTF();
        } catch (IOException e) {
            CatSeedLogin.instance.getLogger().warning("解析 Velocity PluginMessage 失败: " + e.getMessage());
            return;
        }

        if (text == null || !text.startsWith("PREMIUM:")) return;
        String name = text.substring(8);

        // 使用 GlobalScheduler（runEntity 需要实体已存在于世界中，但 onPluginMessage 触发时玩家可能还未完全进入世界）
        Scheduler.runGlobal(CatSeedLogin.instance, () -> {
            if (LoginPlayerHelper.isLogin(name)) return;

            Player target = CatSeedLogin.instance.getServer().getPlayerExact(name);
            if (target == null) return;

            LoginPlayer lp = Cache.getIgnoreCase(name);
            if (lp == null) {
                // 正版玩家首次连接 → 自动注册
                lp = new LoginPlayer(name, generateRandomPassword());
                lp.crypt();
                try {
                    CatSeedLogin.sql.add(lp);
                } catch (Exception e) {
                    CatSeedLogin.instance.getLogger().warning("自动注册正版玩家失败: " + e.getMessage());
                    e.printStackTrace();
                    return;
                }
            }
            LoginPlayerHelper.add(lp);
            LoginPlayerHelper.recordCurrentIP(target, lp);
            CatSeedLogin.instance.getLogger().info("正版玩家 " + name + " 已自动登录");

            // 通知 Velocity 允许切服
            VelocityTransfer.notifyLoggedIn(target);
        });
    }

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static String generateRandomPassword() {
        StringBuilder sb = new StringBuilder(16);
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        for (int i = 0; i < 16; i++) {
            sb.append(chars.charAt(SECURE_RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
