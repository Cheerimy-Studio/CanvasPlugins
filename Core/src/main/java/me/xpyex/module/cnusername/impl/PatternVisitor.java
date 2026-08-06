package me.xpyex.module.cnusername.impl;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.Getter;
import me.xpyex.module.cnusername.CnUsernameConfig;
import me.xpyex.module.cnusername.Logging;
import org.objectweb.asm.ClassVisitor;

@Getter
public abstract class PatternVisitor extends CUClassVisitor {
    private final String pattern;

    protected PatternVisitor(String className, ClassVisitor classVisitor, String pattern) {
        super(className, classVisitor);
        String s;
        if (pattern == null || pattern.isEmpty()) {
            s = CnUsernameConfig.DEFAULT_PATTERN;
            Logging.debug("当前玩家名规则将使用本组件的默认正则规则");
        } else {
            try {
                Pattern.compile(pattern);
                s = pattern;
            } catch (PatternSyntaxException e) {
                s = CnUsernameConfig.DEFAULT_PATTERN;
                e.printStackTrace();
                Logging.warning("自定义正则格式无效，已恢复默认规则: " + pattern);
            }
        }
        Logging.debug("当前组件使用的正则规则为: §6" + s);
        this.pattern = s;
    }
}
