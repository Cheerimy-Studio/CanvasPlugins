package me.xpyex.plugin.cnusername;

import bot.inker.acj.JvmHacker;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.io.PrintStream;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import me.xpyex.module.cnusername.ClassTransformer;
import me.xpyex.module.cnusername.CnUsernameConfig;
import me.xpyex.module.cnusername.Logging;
import me.xpyex.module.cnusername.pass.PassRegistry;

public interface CnUsernamePlugin {
    AtomicReference<MethodHandle> DEFINE_CLASS_METHOD = new AtomicReference<>();

    static MethodHandle getDefineClassMethod() {
        if (DEFINE_CLASS_METHOD.get() == null) {
            try {
                DEFINE_CLASS_METHOD.set(JvmHacker.lookup().findVirtual(
                    ClassLoader.class,
                    "defineClass",
                    MethodType.methodType(Class.class, String.class, byte[].class, int.class, int.class)
                ));
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("初始化失败", e);
            }
        }
        return DEFINE_CLASS_METHOD.get();
    }

    default Instrumentation instrumentationOrNull() {
        // 抑制 JDK 21+ 动态加载 JavaAgent 时产生的 STDERR 警告
        // "WARNING: A Java agent has been loaded dynamically"
        PrintStream originalErr = System.err;
        try {
            System.setErr(new PrintStream(new java.io.OutputStream() { public void write(int b) {} }));
            return JvmHacker.instrumentation();
        } catch (Throwable e) {
            System.setErr(originalErr);
            String errorMsg = e.getMessage();
            if (errorMsg != null && (errorMsg.contains("Permission denied") || errorMsg.contains("Read-only file system"))) {
                Logging.warning("容器环境限制，无法动态加载JavaAgent，使用直接字节码方案");
            } else if (errorMsg != null && errorMsg.contains("tools.jar")) {
                Logging.warning("未找到 tools.jar，使用直接字节码方案");
            } else {
                Logging.warning("无法获取Instrumentation: " + e);
                if (CnUsernameConfig.isDebug()) e.printStackTrace();
            }
            return null;
        } finally {
            System.setErr(originalErr);
        }
    }

    @SuppressWarnings("unchecked")
    default void applyInstrumentation(Instrumentation instrumentation) {
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
                return CnUsernamePlugin.this.transform(loader, className, classBeingRedefined, classfileBuffer);
            }
        }, true);

        Map<String, Class<?>> loadedClasses = Arrays.stream(instrumentation.getAllLoadedClasses())
                                                  .collect(Collectors.toMap(
                                                      clazz -> clazz.getName().replace('.', '/'),
                                                      clazz -> clazz,
                                                      (existing, replacement) -> existing  // 遇到重复key时保留第一个
                                                  ));

        Set<Class<?>> pendingRetransformClasses = PassRegistry.allPossibleClasses().stream()
                                                      .filter(loadedClasses::containsKey)
                                                      .map(loadedClasses::get)
                                                      .collect(Collectors.toSet());

        for (Class<?> retransformClass : pendingRetransformClasses) {
            try {
                instrumentation.retransformClasses(retransformClass);
            } catch (UnmodifiableClassException e) {
                Logging.warning("无法重定义类 " + retransformClass.getName() + ": " + e);
            }
        }
    }

    default byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, byte[] classfileBuffer) {
        return ClassTransformer.transform(
            loader,
            className,
            classBeingRedefined,
            classfileBuffer
        );
    }
}
