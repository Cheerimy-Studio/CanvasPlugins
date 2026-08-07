package cc.baka9.catseedlogin.bukkit;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.core.filter.AbstractFilter;

import java.util.Set;

/**
 * 控制台日志密码隐藏过滤器。
 * <p>
 * Paper 打印 "xxx issued server command: /login password" 的时机在
 * PlayerCommandPreprocessEvent 之前（ServerGamePacketListenerImpl 先 log 后发事件），
 * 因此无法通过修改事件消息来隐藏密码。AuthMe 等插件采用的就是本方案：
 * 给 root logger 挂一个 Log4j2 Filter，拦截含密码的命令日志整条 DENY，
 * 并打印一条替换为 *** 的日志。
 */
public class ConsolePasswordFilter extends AbstractFilter {

    private static final Set<String> PASSWORD_COMMANDS = Set.of(
            "login", "l", "register", "reg",
            "changepassword", "changepw",
            "resetpassword", "repw"
    );

    private static final String REPLACED_SUFFIX = " [password hidden by CatSeedLogin]";

    /** 安装过滤器到 root logger（在 onEnable 时调用，onDisable 时无需移除，插件重载自动重建） */
    public static void install() {
        org.apache.logging.log4j.core.Logger root = (org.apache.logging.log4j.core.Logger)
                org.apache.logging.log4j.LogManager.getRootLogger();
        root.addFilter(new ConsolePasswordFilter());
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String msg, Object... params) {
        return process(msg);
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Object msg, Throwable t) {
        return process(msg == null ? null : msg.toString());
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Message msg, Throwable t) {
        return process(msg == null ? null : msg.getFormattedMessage());
    }

    @Override
    public Result filter(LogEvent event) {
        return process(event.getMessage() == null ? null : event.getMessage().getFormattedMessage());
    }

    private Result process(String msg) {
        if (msg == null) return Result.NEUTRAL;
        // 已替换过的日志直接放行，避免递归
        if (msg.contains(REPLACED_SUFFIX)) return Result.NEUTRAL;
        // 匹配 "xxx issued server command: /login password"
        int idx = msg.indexOf("issued server command:");
        if (idx < 0) return Result.NEUTRAL;
        String after = msg.substring(idx + "issued server command:".length()).trim();
        if (!after.startsWith("/")) return Result.NEUTRAL;
        String[] parts = after.split(" ");
        if (parts.length <= 1) return Result.NEUTRAL;
        String cmd = parts[0].substring(1).toLowerCase();
        if (!PASSWORD_COMMANDS.contains(cmd)) return Result.NEUTRAL;

        // 原日志 DENY，打印替换版（用 Log4j 打印以保留时间戳/格式）
        String prefix = msg.substring(0, idx + "issued server command:".length());
        StringBuilder redacted = new StringBuilder(prefix);
        redacted.append(' ').append(parts[0]);
        for (int i = 1; i < parts.length; i++) redacted.append(" ***");
        redacted.append(REPLACED_SUFFIX);
        LOGGER.log(Level.INFO, redacted.toString());
        return Result.DENY;
    }

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("CatSeedLogin");
}
