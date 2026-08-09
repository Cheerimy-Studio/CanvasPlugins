package cc.baka9.catseedlogin.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
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
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CatSeedLogin Velocity 端插件（极简版）。
 * <p>
 * online-mode=false 下 Velocity 不联系 Mojang，所有 UUID 是离线 UUID。
 * Mojang API 只查名字是否为正版账号，不验证连接者身份。
 * <p>
 * ⚠️ 安全警告：禁止基于 Mojang API 做正版免登录！
 * online-mode=false 时无法验证连接者是否是真拥有者 → 冒充漏洞。
 * 正版玩家仍需密码登录。唯一优势：英文名不被 KickEnglishOfflineNames 踢出。
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

    /** Mojang API 缓存（5分钟） */
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
        proxy.getEventManager().register(this, new Listeners(proxy, logger, loginServerName));
        proxy.getEventManager().register(this, this);
        logger.info("CatSeedLogin Velocity 已启动 | 登录服: {} | 踢英文离线: {}",
                loginServerName.isEmpty() ? "(未配置)" : loginServerName, kickEnglishOfflineNames);
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        if (!kickEnglishOfflineNames) return;
        Player player = event.getPlayer();
        String name = player.getUsername();
        if (!name.matches("^[a-zA-Z0-9_]+$")) return;
        if (!checkPremium(name)) {
            event.setResult(LoginEvent.ComponentResult.denied(
                    Component.text(englishNameKickMessage)));
            logger.info("CatSeedLogin: {} 英文名离线玩家已拒绝", name);
        }
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().getId().equals(CHANNEL.getId())) return;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(event.getData()))) {
            Listeners.markLoggedIn(in.readUTF());
        } catch (IOException e) {
            logger.error("CatSeedLogin: PluginMessage 解析失败", e);
        }
        event.setResult(PluginMessageEvent.ForwardResult.handled());
    }

    private boolean checkPremium(String name) {
        Long t = premiumCacheTime.get(name);
        if (t != null && System.currentTimeMillis() - t < CACHE_TTL_MS) {
            return premiumCache.getOrDefault(name, false);
        }
        boolean r = false;
        try {
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + name);
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(3000); c.setReadTimeout(3000);
            c.setRequestMethod("GET");
            c.setRequestProperty("User-Agent", "CatSeedLogin/2.0.0");
            r = (c.getResponseCode() == 200);
        } catch (Exception ignore) {}
        premiumCache.put(name, r);
        premiumCacheTime.put(name, System.currentTimeMillis());
        return r;
    }

    private void loadConfig() {
        try {
            Files.createDirectories(dataDir);
            Path f = dataDir.resolve("velocity.properties");
            if (!Files.exists(f)) {
                try (Writer w = Files.newBufferedWriter(f)) {
                    w.write("LoginServerName=login\n");
                    w.write("KickEnglishOfflineNames=false\n");
                    w.write("EnglishNameKickMessage=离线玩家请使用中文名称 ID 登录。\n");
                }
            }
            Properties p = new Properties();
            try (Reader r = Files.newBufferedReader(f)) { p.load(r); }
            loginServerName = p.getProperty("LoginServerName", "login");
            kickEnglishOfflineNames = Boolean.parseBoolean(p.getProperty("KickEnglishOfflineNames", "false"));
            englishNameKickMessage = p.getProperty("EnglishNameKickMessage", "离线玩家请使用中文名称 ID 登录。");
        } catch (IOException e) { logger.error("加载 velocity.properties 失败", e); }
    }
}
