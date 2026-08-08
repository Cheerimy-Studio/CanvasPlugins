package cc.baka9.catseedlogin.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * CatSeedLogin Velocity 端 —— 英文名离线玩家踢出 + 登录状态同步 + 未登录禁切服。
 * <ul>
 *   <li>LoginEvent: 英文名 + 非正版 UUID + 非白名单 → 拒绝连接（Velocity 直接持有 Mojang 鉴权结果，可靠度 100%）</li>
 *   <li>PluginMessage: Bukkit 登录服通知后允许切服</li>
 *   <li>ServerPreConnectEvent: 未登录玩家切服强制送回登录服</li>
 * </ul>
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

    // ── 英文名离线玩家踢出配置 ──
    private boolean kickEnglishOfflineNames = false;
    private String englishNameKickMessage = "离线玩家请使用中文名称 ID 登录。";
    private final Set<String> englishNameWhitelist = new HashSet<>();

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
        logger.info("CatSeedLogin Velocity OK | 登录服: {} | 英文名踢出: {}",
                loginServerName.isEmpty() ? "(未配置)" : loginServerName, kickEnglishOfflineNames);
    }

    /**
     * Velocity LoginEvent —— 英文名离线玩家拦截。
     * Velocity 直接持有 Mojang 认证结果，event.getPlayer().getUniqueId()
     * 对正版玩家返回 Mojang UUID（≠ 离线 UUID），对离线玩家返回离线 UUID。
     * 这比 Bukkit 后端 AsyncPlayerPreLoginEvent 可靠 100%。
     */
    @Subscribe
    public void onLogin(LoginEvent event) {
        if (!kickEnglishOfflineNames) return;
        String name = event.getPlayer().getUsername();

        // 只检测纯英文名（ASCII 字母+数字+下划线）
        if (!name.matches("^[a-zA-Z0-9_]+$")) return;

        // 白名单放行
        if (englishNameWhitelist.contains(name)) return;

        // 正版检测：UUID ≠ 离线 UUID → 正版 → 放行
        UUID offlineUUID = UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        if (!offlineUUID.equals(event.getPlayer().getUniqueId())) return;

        // 离线玩家 + 英文名 + 非白名单 → 拒绝
        event.setResult(LoginEvent.ComponentResult.denied(
                Component.text(englishNameKickMessage)));
        logger.info("CatSeedLogin: {} 英文名离线玩家已踢出", name);
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
                    w.write("\n");
                    w.write("# 是否踢出英文名离线玩家（正版玩家 + 白名单内不受影响）\n");
                    w.write("# 设为 true 时 Velocity 端可直接通过 Mojang UUID 精准区分正版/离线\n");
                    w.write("KickEnglishOfflineNames=false\n");
                    w.write("# 踢出提示\n");
                    w.write("EnglishNameKickMessage=离线玩家请使用中文名称 ID 登录。\n");
                    w.write("# 白名单英文名（逗号分隔）\n");
                    w.write("EnglishNameWhitelist=\n");
                }
            }
            Properties props = new Properties();
            try (Reader r = Files.newBufferedReader(configFile)) {
                props.load(r);
            }
            loginServerName = props.getProperty("LoginServerName", "").trim();
            kickEnglishOfflineNames = Boolean.parseBoolean(
                    props.getProperty("KickEnglishOfflineNames", "false"));
            englishNameKickMessage = props.getProperty(
                    "EnglishNameKickMessage", "离线玩家请使用中文名称 ID 登录。");
            String whitelist = props.getProperty("EnglishNameWhitelist", "");
            englishNameWhitelist.clear();
            if (!whitelist.isBlank()) {
                for (String s : whitelist.split(",")) {
                    String trimmed = s.trim();
                    if (!trimmed.isEmpty()) englishNameWhitelist.add(trimmed);
                }
            }
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
        if (!loginServerName.isEmpty() &&
                !sourceConn.getServerInfo().getName().equals(loginServerName)) return;

        try (DataInputStream in = new DataInputStream(
                new ByteArrayInputStream(event.getData()))) {
            String playerName = in.readUTF();
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
