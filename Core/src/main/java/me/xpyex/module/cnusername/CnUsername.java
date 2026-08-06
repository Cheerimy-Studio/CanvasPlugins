package me.xpyex.module.cnusername;

import java.io.File;
import java.io.IOException;
import java.lang.Runtime.Version;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.ProtectionDomain;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassWriter;

public class CnUsername {
    private static Version MC_VERSION = null;

    public static void premain(final String agentArgs, final Instrumentation inst) {
        long start = System.currentTimeMillis();
        CnUsernameConfig.loadConfig();

        // 迁移旧的 agent 参数到配置文件
        if (agentArgs != null && !agentArgs.trim().isEmpty()) {
            try {
                Files.write(CnUsernameConfig.getPatternFile().toPath(), agentArgs.getBytes(StandardCharsets.UTF_8));
                CnUsernameConfig.loadConfig();
            } catch (IOException e) {
                if (CnUsernameConfig.isDebug()) e.printStackTrace();
            }
        }

        // 注册 ClassFileTransformer
        inst.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                    ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                return ClassTransformer.transform(loader, className, classBeingRedefined, classfileBuffer);
            }
        });

        long cost = System.currentTimeMillis() - start;
        Logging.info("CnUsername v" + UpdateChecker.version + " (Agent) loaded in " + cost + "ms");
    }

    public static File saveClassFile(ClassWriter writer, String className) throws IOException {
        return saveClassFile(writer.toByteArray(), className);
    }

    public static File saveClassFile(byte[] data, String className) throws IOException {
        File file = new File(CnUsernameConfig.folder, className.replace("/", ".") + ".class");
        Files.write(file.toPath(), data);
        return file;
    }

    public static Version getMcVersion() {
        if (MC_VERSION == null) {
            File properties = new File("server.properties").getAbsoluteFile();
            files:
            for (File file : properties.getParentFile().listFiles()) {
                if (file.isFile() && file.getName().endsWith(".jar")) {
                    try (JarFile jar = new JarFile(file)) {
                        Enumeration<JarEntry> enumFiles = jar.entries();
                        while (enumFiles.hasMoreElements()) {
                            JarEntry entry = enumFiles.nextElement();
                            String entryName = entry.getName();
                            if (entryName.contains("META-INF/versions/1.") && entryName.endsWith("/")) {
                                String[] split = entryName.split("/");
                                MC_VERSION = Version.parse(split[split.length - 1]);
                                break files;
                            } else if (entryName.contains("META-INF/versions/") && (entryName.endsWith(".jar") || entryName.endsWith(".jar.patch"))) {
                                String[] split = entryName.split("/");
                                MC_VERSION = Version.parse(split[split.length - 1].split("-")[1]);
                                break files;
                            }
                        }
                    } catch (Exception e) {
                        if (CnUsernameConfig.isDebug()) e.printStackTrace();
                    }
                }
            }
        }
        return MC_VERSION;
    }
}
