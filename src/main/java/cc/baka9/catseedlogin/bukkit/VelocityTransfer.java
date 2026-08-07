package cc.baka9.catseedlogin.bukkit;

import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Velocity Plugin Message 通道 —— 登录/注册成功后通知代理端"该玩家已登录"。
 * <p>
 * 通道名 catseedlogin:transfer，格式：playerName (writeUTF)
 * Velocity 端 VelocityPlugin.onPluginMessage 监听此通道并标记已登录。
 */
public final class VelocityTransfer {

    /** 自定义 Plugin Message 通道名 */
    public static final String CHANNEL = "catseedlogin:transfer";

    private VelocityTransfer() {}

    /**
     * 通知 Velocity：该玩家已在登录服完成登录，允许切换子服。
     */
    public static void notifyLoggedIn(Player player) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeUTF(player.getName());
            player.sendPluginMessage(CatSeedLogin.instance, CHANNEL, baos.toByteArray());
        } catch (IOException e) {
            CatSeedLogin.instance.getLogger().warning("向 Velocity 发送 PluginMessage 失败: " + e.getMessage());
        }
    }
}
