package cc.baka9.catseedlogin.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * CatSeedLogin Velocity 端 —— 登录状态同步 + 未登录禁切服。
 * <p>
 * Bukkit 登录服在玩家登录/注册成功后通过 PluginMessage 通道
 * "catseedlogin:transfer" 通知本插件该玩家已登录。
 * 未登录玩家尝试切换子服（/server main）时，强制改道登录服。
 */
@Plugin(
        id = "catseedlogin",
        name = "CatSeedLogin",
        version = "1.4.1-paper26.2",
        authors = {"CatSeed"},
        url = "https://github.com/Cheerimy-Studio/CanvasPlugins"
)
public class VelocityPlugin {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDir;

    static final MinecraftChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.create("catseedlogin", "transfer");

    /** 登录服名称（未登录玩家只能进这个服） */
    static String loginServerName = "";

    @Inject
    public VelocityPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDir) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDir = dataDir;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        loadConfig();
        proxy.getChannelRegistrar().register(CHANNEL);
        proxy.getEventManager().register(this, new Listeners(proxy, logger));
        logger.info("CatSeedLogin Velocity OK | 登录服: {}", loginServerName.isEmpty() ? "(未配置)" : loginServerName);
    }

    private void loadConfig() {
        try {
            Files.createDirectories(dataDir);
            Path configFile = dataDir.resolve("velocity.properties");
            if (!Files.exists(configFile)) {
                try (Writer w = Files.newBufferedWriter(configFile)) {
                    w.write("# CatSeedLogin Velocity\n");
                    w.write("# 登录服名称：未登录玩家只能连接到此服务器\n");
                    w.write("LoginServerName=login\n");
                }
            }
            Properties props = new Properties();
            try (Reader r = Files.newBufferedReader(configFile)) {
                props.load(r);
            }
            loginServerName = props.getProperty("LoginServerName", "").trim();
        } catch (IOException e) {
            logger.error("加载 velocity.properties 失败", e);
        }
    }

    /**
     * Bukkit 登录服通知：玩家已登录。
     * 消息格式：playerName (UTF)
     */
    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().getId().equals(CHANNEL.getId())) return;
        if (!(event.getSource() instanceof ServerConnection sourceConn)) return;
        // 只接受登录服发来的消息
        if (!loginServerName.isEmpty() &&
                !sourceConn.getServerInfo().getName().equals(loginServerName)) return;

        try (DataInputStream in = new DataInputStream(
                new ByteArrayInputStream(event.getData()))) {
            String playerName = in.readUTF();
            // 标记该玩家为已登录，允许切服
            Listeners.markLoggedIn(playerName);
            logger.info("CatSeedLogin: {} 已登录，允许切换子服", playerName);
        } catch (IOException e) {
            logger.error("CatSeedLogin: PluginMessage 解析失败", e);
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Listeners.markLoggedOut(event.getPlayer().getUsername());
    }
}
