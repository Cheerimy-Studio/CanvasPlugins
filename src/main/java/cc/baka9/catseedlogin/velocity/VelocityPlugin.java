package cc.baka9.catseedlogin.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CatSeedLogin Velocity 端插件。
 *
 * <p>正版检测方案（借鉴 FastLogin）：
 * <ol>
 *   <li>PreLoginEvent → 异步 Mozilla API 查询</li>
 *   <li>正版 → {@code setResult(forceOnlineMode())} → Velocity 联系 Mojang 验证 UUID</li>
 *   <li>离线英文名 → 拒绝（KickEnglishOfflineNames=true 时）</li>
 *   <li>离线中文名 → 放行（正常密码登录）</li>
 * </ol>
 *
 * <p>正版玩家自动登录由 {@link #onLogin(LoginEvent)} 中的 PluginMessage 触发。
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
    private boolean kickEnglishOfflineNames = true;

    /** 已登录玩家 */
    private static final Set<String> LOGGED_IN = ConcurrentHashMap.newKeySet();

    @Inject
    public VelocityPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDir) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDir = dataDir;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        loadConfig();
        proxy.getChannelRegistrar().register(CHANNEL);
        // Velocity 4.1.0 自动注册主插件实例，不需要手动 register(this, this)
        // 只注册 Listeners（非主类需要显式注册）
        proxy.getEventManager().register(this, new Listeners(proxy, logger, loginServerName));
        logger.info("CatSeedLogin Velocity {} 已加载 (登录服={})",
                getClass().getAnnotation(Plugin.class).version(),
                loginServerName.isEmpty() ? "(未配置)" : loginServerName);
    }

    // ========== 正版检测核心 ==========

    /**
     * PreLoginEvent — 暂停登录，异步查询 Mojang API。
     * 正版玩家 → {@code forceOnlineMode()}，Velocity 会联系 Mojang 获取真实 UUID。
     * 离线英文名 → 拒绝。
     * 离线中文名 → 放行。
     */
    @Subscribe
    public EventTask onPreLogin(PreLoginEvent event) {
        if (!event.getResult().isAllowed()) {
            return null;
        }
        String name = event.getUsername();
        return EventTask.async(() -> {
            boolean isPremium = checkPremium(name);
            if (isPremium) {
                // 正版 → 强制此连接走 online-mode，Velocity 联系 Mojang 获取真实 UUID
                event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());
                logger.info("CatSeedLogin: {} 正版，已切换 online-mode", name);
            } else if (kickEnglishOfflineNames && isEnglishName(name)) {
                // 离线英文名 → 拒绝
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                        Component.text("离线玩家请使用中文名称 ID 登录。")));
                logger.info("CatSeedLogin: {} 英文名离线，已拒绝", name);
            }
            // 离线中文名 → 不干预，走正常登录流程
        });
    }

    /**
     * LoginEvent — 正版玩家：Velocity 已完成 Mojang 验证，发送 PluginMessage 给 Bukkit。
     */
    @Subscribe
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        String name = player.getUsername();
        // 仅正版玩家（UUID ≠ 离线 UUID）
        java.util.UUID offlineUUID = java.util.UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (offlineUUID.equals(player.getUniqueId())) {
            return; // 离线玩家，不干预
        }
        // 标记为已登录（正版玩家跳过切服限制）
        LOGGED_IN.add(name);
        logger.info("CatSeedLogin: {} 正版已标记", name);
    }

    /**
     * ServerPostConnectEvent — 正版玩家进入后端后，通知 Bukkit 自动注册/登录。
     */
    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        if (!LOGGED_IN.contains(player.getUsername())) return;

        player.getCurrentServer().ifPresent(server -> {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                 DataOutputStream out = new DataOutputStream(baos)) {
                out.writeUTF("PREMIUM:" + player.getUsername());
                server.sendPluginMessage(CHANNEL, baos.toByteArray());
                logger.info("CatSeedLogin: {} 已通知 Bukkit 免登录", player.getUsername());
            } catch (IOException e) {
                logger.error("发送 PluginMessage 失败", e);
            }
        });
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        LOGGED_IN.remove(event.getPlayer().getUsername());
    }

    // ========== 工具方法 ==========

    private static boolean isEnglishName(String name) {
        return name.matches("^[a-zA-Z0-9_]+$");
    }

    /**
     * 通过 Mojang API 查询该用户名是否为正版账号。
     * 结果在 5 分钟内缓存。
     */
    private boolean checkPremium(String name) {
        long now = System.currentTimeMillis();
        if (premiumCache.containsKey(name)) {
            long[] entry = premiumCache.get(name);
            if (now - entry[0] < 300_000) {
                return entry[1] == 1L;
            }
        }
        try {
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + name);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestMethod("GET");
            boolean isPremium = conn.getResponseCode() == 200;
            premiumCache.put(name, new long[]{now, isPremium ? 1L : 0L});
            return isPremium;
        } catch (Exception e) {
            logger.warn("Mojang API 查询失败 {}: {}", name, e.getMessage());
            return false;
        }
    }

    private final java.util.Map<String, long[]> premiumCache = new ConcurrentHashMap<>();

    // ========== 配置 ==========

    private void loadConfig() {
        try {
            Files.createDirectories(dataDir);
            Path configFile = dataDir.resolve("velocity.properties");
            if (!Files.exists(configFile)) {
                try (Writer w = Files.newBufferedWriter(configFile)) {
                    w.write("# CatSeedLogin Velocity 代理配置\n");
                    w.write("# 登录服名称：未登录玩家只能连接到此服务器\n");
                    w.write("LoginServerName=login\n");
                }
            }
            Properties props = new Properties();
            try (Reader r = Files.newBufferedReader(configFile)) {
                props.load(r);
            }
            loginServerName = props.getProperty("LoginServerName", "login");
            kickEnglishOfflineNames = Boolean.parseBoolean(
                    props.getProperty("KickEnglishOfflineNames", "true"));
        } catch (IOException e) {
            logger.error("加载 velocity.properties 失败", e);
        }
    }
}
