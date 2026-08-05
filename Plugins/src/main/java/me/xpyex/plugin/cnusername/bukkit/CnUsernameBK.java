package me.xpyex.plugin.cnusername.bukkit;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import me.xpyex.module.cnusername.CnUsername;
import me.xpyex.module.cnusername.CnUsernameConfig;
import me.xpyex.module.cnusername.Logging;
import me.xpyex.module.cnusername.modify.minecraft.ClassVisitorLoginListener;
import me.xpyex.module.cnusername.modify.minecraft.ClassVisitorStringUtil;
import me.xpyex.module.cnusername.modify.paper.ClassVisitorCraftPlayerProfile;
import me.xpyex.plugin.cnusername.CnUsernamePlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

public final class CnUsernameBK extends JavaPlugin implements CnUsernamePlugin {

    public CnUsernameBK() {
        Logging.setLogger(getServer().getLogger());
        CnUsernameConfig.setFolder(getDataFolder());

        long start = System.currentTimeMillis();

        // 字节码修改
        Instrumentation instrumentation = instrumentationOrNull();
        if (instrumentation == null) {
            applyLegacy();
        } else {
            applyInstrumentation(instrumentation);
        }

        long cost = System.currentTimeMillis() - start;
        Logging.info("CnUsername v" + getDescription().getVersion() + " loaded in " + cost + "ms");
        Logging.info("GitHub: https://github.com/Cheerimy-Studio/CanvasPlugins");
    }

    public static void loadClass(String className, byte[] bytes) {
        try {
            CnUsernamePlugin.getDefineClassMethod().invoke(Bukkit.class.getClassLoader(), className, bytes, 0, bytes.length);
        } catch (Throwable e) {
            throw new IllegalStateException("Failed to modify class " + className, e);
        }
    }

    private void applyLegacy() {
        try {
            ClassReader reader = new ClassReader(Bukkit.class.getClassLoader().getResourceAsStream(ClassVisitorStringUtil.CLASS_PATH + ".class"));
            String className = reader.getClassName().replace("/", ".");
            loadClass(className, modifyClass(reader));
        } catch (Exception e) {
            if (CnUsernameConfig.isDebug()) e.printStackTrace();
        }

        try {
            ClassReader reader = new ClassReader(Bukkit.class.getClassLoader().getResourceAsStream(ClassVisitorCraftPlayerProfile.CLASS_PATH + ".class"));
            String className = reader.getClassName().replace("/", ".");
            loadClass(className, modifyClass(reader));
        } catch (Exception e) {
            if (CnUsernameConfig.isDebug()) e.printStackTrace();
        }
    }

    @Override
    public void onLoad() {
        try {
            ClassReader classReader = null;
            for (String classPath : new String[]{
                ClassVisitorLoginListener.CLASS_PATH_MOJANG,
                ClassVisitorLoginListener.CLASS_PATH_SPIGOT,
                ClassVisitorLoginListener.CLASS_PATH_YARN
            }) {
                try {
                    classReader = new ClassReader(Bukkit.class.getClassLoader().getResourceAsStream(classPath + ".class"));
                    break;
                } catch (IOException ignored) {
                }
            }
            if (classReader != null) {
                String className = classReader.getClassName().replace("/", ".");
                loadClass(className, modifyClass(classReader));
            }
        } catch (Exception e) {
            if (CnUsernameConfig.isDebug()) e.printStackTrace();
        }
    }

    @Override
    public void onEnable() {
        // Cheerimy-Studio 正版检测
        org.bukkit.plugin.Plugin cheerimy = getServer().getPluginManager().getPlugin("Cheerimy-Studio");
        if (cheerimy == null || !cheerimy.isEnabled()) {
            Logging.warning("[CnUsername] Cheerimy-Studio not found, integrity check failed.");
            Logging.warning("[CnUsername] https://github.com/Cheerimy-Studio/MinecraftPlugins");
        }

        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPreLogin(AsyncPlayerPreLoginEvent event) {
                if ("CS-CoreLib".equals(event.getName())) {
                    event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_BANNED);
                    event.setKickMessage("Invalid username\\nCnUsername Defend");
                }
            }
        }, this);
    }

    @Override
    public void onDisable() {
    }

    private byte[] modifyClass(ClassReader reader) {
        ClassWriter classWriter = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);
        ClassVisitor classVisitor = new ClassVisitorLoginListener(
            reader.getClassName().replace("/", "."), classWriter, CnUsernameConfig.getPattern());
        reader.accept(classVisitor, 0);
        return classWriter.toByteArray();
    }
}
