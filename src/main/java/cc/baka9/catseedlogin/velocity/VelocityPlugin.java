package cc.baka9.catseedlogin.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CatSeedLogin Velocity 端插件。
 * <p>
 * online-mode=false 代理模式下，Velocity 不联系 Mojang 验证，所有玩家 UUID 均为离线 UUID。
 * 因此必须通过 Mojang API（api.mojang.com）查询来区分正版/离线玩家。
 * <p>
 * 功能：
 * 1. Mojang API 正版检测（online-mode=false 兼容）
 * 2. 正版玩家通过 PluginMessage 通知 Bukkit 端免登录
 * 3. 英文名离线玩家可选踢出（KickEnglishOfflineNames=true）
 * 4. 未登录玩家禁止切换子服
 */
@Plugin(id = "catseedlogin-velocity", name = "CatSeedLogin-Velocity",
        version = "2.0.0", authors = {"CatSeed"})
public class VelocityPlugin {

    public static final MinecraftChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.create("catseedlogin", "transfer");

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDir;

    private String loginServerName = "login";
    private boolean kickEnglishOfflineNames = false;
    private String englishNameKickMessage = "离线玩家请使用中文名称 ID 登录。";

    /** Mojang API 正版检查结果缓存（5分钟），避免每次登录都调 API */
    private final Map<String, Boolean> premiumCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 5 * 60 * 1000;
    private final Map<String, Long> premiumCacheTime = new ConcurrentHashMap<>();

    @Inject
    public VelocityPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDir) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDir = dataDir;
    }

    @Subscribe
    public void onProxyInit(ProxyInitializeEvent event) {
        loadConfig();

        proxy.getChannelRegistrar().register(CHANNEL);

        Listeners listeners = new Listeners(proxy, logger, loginServerName);
        proxy.getEventManager().register(this, listeners);
        proxy.getEventManager().register(this, this);

        logger.info("CatSeedLogin Velocity 已启动 | 登录服: {} | 踢英文离线: {}",
                loginServerName, kickEnglishOfflineNames);
    }

    /**
     * Velocity LoginEvent —— 玩家连接代理时触发。
     * online-mode=false 时 UUID 全部是离线 UUID，改用 Mojang API 判断正版。
     */
    @Subscribe
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        String name = player.getUsername();

        // 中文名玩家不受 KickEnglishOfflineNames 影响
        if (!isEnglishName(name)) return;

        // 查 Mojang API：该名字是否为官方正版账号
        boolean premium = checkPremium(name);

        if (!premium && kickEnglishOfflineNames) {
            event.setResult(LoginEvent.ComponentResult.denied(
                    Component.text(englishNameKickMessage)));
            logger.info("CatSeedLogin: {} 英文名离线玩家已拒绝", name);
        }
    }

    /**
     * 玩家进入登录服后，若为正版玩家，通知 Bukkit 免登录。
     */
    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        String name = player.getUsername();

        // 异步查 Mojang API（不阻塞网络线程）
        proxy.getScheduler().buildTask(this, () -> {
            boolean premium = checkPremium(name);
            if (!premium) return;

            player.getCurrentServer().ifPresent(server ->
                    server.sendPluginMessage(CHANNEL, encodePremiumMessage(name)));
            logger.info("CatSeedLogin: {} 正版玩家，已通知 Bukkit 免登录", name);
        }).schedule();
    }

    /**
     * 接收 Bukkit 端 PluginMessage。
     * "PREMIUM:name" → Velocity 发送的正版通知（回流，忽略）
     * "name" → Bukkit 登录成功后通知，标记已登录
     */
    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().getId().equals(CHANNEL.getId())) return;

        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(event.getData()));
            String data = in.readUTF();
            // 过滤 "PREMIUM:" 回流
            if (data.startsWith("PREMIUM:")) return;
            Listeners.markLoggedIn(data);
            logger.debug("CatSeedLogin: {} 已登录（来自 Bukkit 通知）", data);
        } catch (IOException e) {
            logger.error("CatSeedLogin: 处理 PluginMessage 失败", e);
        }
        event.setResult(PluginMessageEvent.ForwardResult.handled());
    }

    /**
     * 通过 Mojang API 检查名字是否为正版账号。
     * 结果缓存 5 分钟，避免频繁 API 调用。
     */
    private boolean checkPremium(String name) {
        Long cachedTime = premiumCacheTime.get(name);
        if (cachedTime != null && System.currentTimeMillis() - cachedTime < CACHE_TTL_MS) {
            return premiumCache.getOrDefault(name, false);
        }

        boolean premium = false;
        try {
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + name);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "CatSeedLogin-Velocity/2.0.0");
            premium = (conn.getResponseCode() == 200);
        } catch (Exception e) {
            logger.warn("CatSeedLogin: Mojang API 查询 {} 失败: {}", name, e.getMessage());
            return false;
        }

        premiumCache.put(name, premium);
        premiumCacheTime.put(name, System.currentTimeMillis());
        return premium;
    }

    private static boolean isEnglishName(String name) {
        return name.matches("^[a-zA-Z0-9_]+$");
    }

    private static byte[] encodePremiumMessage(String name) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(baos)) {
            out.writeUTF("PREMIUM:" + name);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void loadConfig() {
        try {
            Files.createDirectories(dataDir);
            Path configFile = dataDir.resolve("velocity.properties");
            if (!Files.exists(configFile)) {
                try (Writer w = Files.newBufferedWriter(configFile)) {
                    w.write("# CatSeedLogin Velocity 代理配置\n");
                    w.write("# 登录服名称：未登录玩家只能连接到此服务器\n");
                    w.write("LoginServerName=login\n");
                    w.write("\n");
                    w.write("# 是否踢出英文名离线玩家（正版玩家不受影响）\n");
                    w.write("KickEnglishOfflineNames=false\n");
                    w.write("# 踢出提示\n");
                    w.write("EnglishNameKickMessage=离线玩家请使用中文名称 ID 登录。\n");
                }
            }
            Properties props = new Properties();
            try (Reader r = Files.newBufferedReader(configFile)) {
                props.load(r);
            }
            loginServerName = props.getProperty("LoginServerName", "login");
            kickEnglishOfflineNames = Boolean.parseBoolean(
                    props.getProperty("KickEnglishOfflineNames", "false"));
            englishNameKickMessage = props.getProperty("EnglishNameKickMessage",
                    "离线玩家请使用中文名称 ID 登录。");
        } catch (IOException e) {
            logger.error("加载 velocity.properties 失败", e);
        }
    }
}
