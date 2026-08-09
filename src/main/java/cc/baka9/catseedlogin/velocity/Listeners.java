package cc.baka9.catseedlogin.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Velocity 事件监听 —— 未登录玩家禁止切换子服。
 */
public class Listeners {

    private final ProxyServer proxy;
    private final Logger logger;
    private final String loginServerName;

    /** 已登录玩家集合（由 PluginMessage 标记） */
    private static final Set<String> LOGGED_IN = ConcurrentHashMap.newKeySet();

    Listeners(ProxyServer proxy, Logger logger, String loginServerName) {
        this.proxy = proxy;
        this.logger = logger;
        this.loginServerName = loginServerName;
    }

    static void markLoggedIn(String playerName) {
        LOGGED_IN.add(playerName);
    }

    static void markLoggedOut(String playerName) {
        LOGGED_IN.remove(playerName);
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        if (loginServerName == null || loginServerName.isEmpty()) return;

        // 目标是登录服：放行
        if (event.getOriginalServer().getServerInfo().getName().equals(loginServerName)) return;

        // 已登录：放行
        if (LOGGED_IN.contains(event.getPlayer().getUsername())) return;

        // 未登录：强制改道登录服
        proxy.getServer(loginServerName).ifPresent(server -> {
            event.setResult(ServerPreConnectEvent.ServerResult.allowed(server));
            logger.info("CatSeedLogin: {} 未登录，拦截切服 -> {}", event.getPlayer().getUsername(), loginServerName);
        });
    }
}
