package cc.baka9.catseedlogin.bukkit;

import cc.baka9.catseedlogin.bukkit.database.Cache;
import cc.baka9.catseedlogin.bukkit.object.LoginPlayer;
import cc.baka9.catseedlogin.bukkit.object.LoginPlayerHelper;
import cc.baka9.catseedlogin.bukkit.task.Task;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;

public class Listeners implements Listener {

    // 涉及密码的命令名（含别名）
    private static final Set<String> PASSWORD_COMMANDS = Set.of(
            "login", "l", "register", "reg",
            "changepassword", "changepw",
            "resetpassword", "repw"
    );

    // 存储被替换前的真实参数（玩家名 → 原始 args）
    private static final ConcurrentHashMap<String, String[]> REAL_ARGS = new ConcurrentHashMap<>();

    /**
     * 获取玩家命令的真实参数（密码隐藏前）。
     * 命令处理器调用后应立即清除。
     */
    public static String[] getRealArgs(Player player) {
        return REAL_ARGS.remove(player.getName());
    }

    /**
     * 在 LOWEST 优先级拦截含密码的命令，
     * 将控制台日志中的密码替换为 ***，同时保存真实参数供命令处理器使用。
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void hidePasswordInConsole(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage();
        String cmd = msg.split(" ")[0].toLowerCase().replaceFirst("/", "");
        if (!PASSWORD_COMMANDS.contains(cmd)) return;

        String[] parts = msg.split(" ");
        if (parts.length <= 1) return; // 无参数，不需处理

        // 保存真实参数（不含命令名）
        String[] realArgs = Arrays.copyOfRange(parts, 1, parts.length);
        REAL_ARGS.put(event.getPlayer().getName(), realArgs);

        // 替换为 *** 供控制台显示
        StringBuilder redacted = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            redacted.append(" ***");
        }
        event.setMessage(redacted.toString());
    }

    private boolean playerIsNotMinecraftPlayer(Player p){
        return !p.getClass().getName().matches("org\\.bukkit\\.craftbukkit.*?\\.entity\\.CraftPlayer");
    }

    /**
     * 检测用户名是否为纯英文（ASCII 字母、数字、下划线组成）。
     * 中文用户名返回 false。
     */
    private boolean isEnglishName(String name) {
        return name.matches("^[a-zA-Z0-9_]+$");
    }

    /**
     * 检测正版玩家：UUID 不等于离线模式 UUID。
     */
    private boolean isPremiumPlayer(Player player) {
        UUID offlineUUID = UUID.nameUUIDFromBytes(("OfflinePlayer:" + player.getName()).getBytes(StandardCharsets.UTF_8));
        return !offlineUUID.equals(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event){
        if (playerIsNotMinecraftPlayer(event.getPlayer())) return;
        if (LoginPlayerHelper.isLogin(event.getPlayer().getName())) return;
        String input = event.getMessage().toLowerCase();
        for (Pattern regex : Config.Settings.CommandWhiteList) {
            if (regex.matcher(input).find()) return;
        }
        event.setCancelled(true);

    }

    @EventHandler
    public void onPlayerLogin(AsyncPlayerPreLoginEvent event){
        if (!Cache.isLoaded) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "服务器还在初始化..");
            return;
        }
        String name = event.getName();
        LoginPlayer lp = Cache.getIgnoreCase(name);
        if (lp == null) return;
        if (!lp.getName().equals(name)) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "游戏名字母大小写不匹配,请使用游戏名" + lp.getName() + "重新尝试登录");
            return;
        }
        if (LoginPlayerHelper.isLogin(name)) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "玩家 " + lp.getName() + " 已经在线了!");
        }
        int count = 0;
        String hostAddress = event.getAddress().getHostAddress();
        for (Player p : Bukkit.getOnlinePlayers()) {
            String ip = p.getAddress().getAddress().getHostAddress();
            if (ip.equals(hostAddress)) {
                count++;
            }
            if (count >= Config.Settings.IpCountLimit) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "太多相同ip的账号同时在线!");
                return;
            }
        }


    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event){
        if (playerIsNotMinecraftPlayer(event.getPlayer())) return;
        if (LoginPlayerHelper.isLogin(event.getPlayer().getName())) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event){
        if (playerIsNotMinecraftPlayer(event.getPlayer())) return;
        if (LoginPlayerHelper.isLogin(event.getPlayer().getName())) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event){
        if (LoginPlayerHelper.isLogin(event.getPlayer().getName())) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event){
        if (!(event.getWhoClicked() instanceof Player) || LoginPlayerHelper.isLogin(event.getWhoClicked().getName()))
            return;
        event.setCancelled(true);
    }

    //登陆之前不能攻击
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event){
        if (!(event.getDamager() instanceof Player)) return;
        if (playerIsNotMinecraftPlayer((Player) event.getDamager())) return;
        if (LoginPlayerHelper.isLogin(event.getDamager().getName())) return;
        event.setCancelled(true);
    }

    //登陆之前不会受到伤害
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event){
        if (Config.Settings.BeforeLoginNoDamage) {

            Entity entity = event.getEntity();
            if (entity instanceof Player && !playerIsNotMinecraftPlayer((Player) entity)) {
                if (!LoginPlayerHelper.isLogin(entity.getName())) {
                    event.setCancelled(true);
                }

            }

        }

    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event){
        if (Config.Settings.CanTpSpawnLocation && event.getTo().equals(Config.Settings.SpawnLocation)) return;
        if (playerIsNotMinecraftPlayer(event.getPlayer())) return;
        if (LoginPlayerHelper.isLogin(event.getPlayer().getName())) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event){
        if (playerIsNotMinecraftPlayer(event.getPlayer())) return;
        if (LoginPlayerHelper.isLogin(event.getPlayer().getName())) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event){
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (playerIsNotMinecraftPlayer(player)) return;
        if (LoginPlayerHelper.isLogin(player.getName())) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event){
        Player player = event.getPlayer();
        if (playerIsNotMinecraftPlayer(player)) return;
        // AlwaysFreeze: 无论是否登录都限制移动
        if (Config.Settings.AlwaysFreeze) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ() && from.getY() - to.getY() >= 0.0D) {
                return;
            }
            if (Config.Settings.CanTpSpawnLocation) {
                Scheduler.safeTeleport(player, Config.Settings.SpawnLocation);
            } else {
                event.setCancelled(true);
            }
            return;
        }
        // 未登录才限制
        if (LoginPlayerHelper.isLogin(player.getName())) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ() && from.getY() - to.getY() >= 0.0D) {
            return;
        }

        if (Config.Settings.CanTpSpawnLocation) {
            // Folia: 跨世界传送使用 teleportAsync，避免阻塞区域线程
            Scheduler.safeTeleport(player, Config.Settings.SpawnLocation);
        } else {
            event.setCancelled(true);
        }

    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event){
        Player player = event.getPlayer();
        if (LoginPlayerHelper.isLogin(player.getName())) {
            if (!player.isDead() || Config.Settings.DeathStateQuitRecordLocation) {
                Config.setOfflineLocation(player);
            }
            Scheduler.runGlobalLater(CatSeedLogin.instance, () -> LoginPlayerHelper.remove(player.getName()), Config.Settings.ReenterInterval);        }
        Task.getTaskAutoKick().playerJoinTime.remove(player.getName());

    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event){
        Player p = event.getPlayer();
        Cache.refresh(p.getName());
        // 正版玩家自动登录（免注册免输入密码）—— 跳过出生点传送
        if (Config.Settings.PremiumAllowNoRegister && isPremiumPlayer(p)) {
            Scheduler.runGlobal(CatSeedLogin.instance, () -> {
                LoginPlayer lp = Cache.getIgnoreCase(p.getName());
                if (lp == null) {
                    // 未注册 → 自动生成随机密码并注册
                    lp = new LoginPlayer(p.getName(), generateRandomPassword());
                    lp.crypt();
                    try {
                        CatSeedLogin.sql.add(lp);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    LoginPlayerHelper.add(lp);
                } else if (!LoginPlayerHelper.isLogin(p.getName())) {
                    // 已注册但未登录 → 自动登录
                    LoginPlayerHelper.add(lp);
                }
                LoginPlayerHelper.recordCurrentIP(p, lp);
            });
            return;
        }
        if (Config.Settings.CanTpSpawnLocation) {
            // Folia: 跨世界传送必须用 teleportAsync（异步），同步 teleport 会阻塞区域线程
            Scheduler.safeTeleport(p, Config.Settings.SpawnLocation);
        }
    }

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private static String generateRandomPassword() {
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(PASSWORD_CHARS.charAt(SECURE_RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    //id只能下划线字母数字
    @EventHandler
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event){
        String name = event.getName();

        // 英文名离线玩家踢出检测
        if (Config.Settings.KickEnglishOfflineNames && isEnglishName(name)) {
            // 白名单内放行
            if (!Config.Settings.EnglishNameWhitelist.contains(name)) {
                // 正版玩家放行（UUID 不等于离线 UUID）
                if (Config.Settings.PremiumAllowNoRegister) {
                    UUID offlineUUID = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
                    if (!offlineUUID.equals(event.getUniqueId())) {
                        // 正版玩家，放行
                    } else {
                        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                                Config.Settings.EnglishNameKickMessage);
                        return;
                    }
                } else {
                    event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                            Config.Settings.EnglishNameKickMessage);
                    return;
                }
            }
        }

        if (Config.Settings.LimitChineseID) {
            if (!name.matches("^\\w+$")) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        "请使用由数字,字母和下划线组成的游戏名,才能进入游戏");
            }
        }
        if (name.length() < Config.Settings.MinLengthID) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    "你的游戏名太短了,至少需要 " + Config.Settings.MinLengthID + " 个字符的长度");
        }
        if (name.length() > Config.Settings.MaxLengthID) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    "你的游戏名太长了,最长只能到达 " + Config.Settings.MaxLengthID + " 个字符的长度");
        }

    }

}
