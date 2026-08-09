package cc.baka9.catseedlogin.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

/**
 * Velocity 事件监听 —— 未登录玩家禁止切换子服。
 */
public class Listeners {
    private final ProxyServer proxy;
    private final Logger logger;
    private final String loginServerName;

    Listeners(ProxyServer proxy, Logger logger, String loginServerName) {
        this.proxy = proxy;
        this.logger = logger;
        this.loginServerName = loginServerName;
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        if (loginServerName == null || loginServerName.isEmpty()) return;
        if (event.getOriginalServer().getServerInfo().getName().equals(loginServerName)) return;
        if (VelocityPlugin.LOGGED_IN.contains(event.getPlayer().getUsername())) return;
        proxy.getServer(loginServerName).ifPresent(server -> {
            event.setResult(ServerPreConnectEvent.ServerResult.allowed(server));
            logger.info("CatSeedLogin: {} 未登录，拦截切服 -> {}", event.getPlayer().getUsername(), loginServerName);
        });
    }
}
