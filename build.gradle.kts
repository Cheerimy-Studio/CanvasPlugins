import io.izzel.taboolib.gradle.*
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    id("io.izzel.taboolib") version "2.0.38"
    id("org.jetbrains.kotlin.jvm") version "2.3.21"
}

taboolib {
    env {
        install(Basic)
        install(Bukkit)
        install(BukkitUtil)
        // BukkitUI 引入 NMS（平台缓存），Canvas 26.2 不兼容，移除
        // BukkitHook 已移除（ProtocolHandler/MeteorInjector NMS）
        // I18n / Database* 未使用，移除
        install(MinecraftChat)
        install(CommandHelper)
        install(Metrics)
        // 本插件仅使用 Bukkit API，不依赖 NMS，跳过 TabooLib 的 Minecraft 版本检查
        disableOnUnsupportedVersion = false
        disableOnSkippedVersion = false
    }
    description {
        name = "2B2TCore"
        desc("2B2TCore - Anarchy server core plugin for Folia / Canvas")
        contributors {
            name("NaN")
        }
        links {
            name("https://github.com/Cheerimy-Studio/CanvasPlugins")
        }
    }
    version { taboolib = "6.2.4-99fb800" }
}

repositories {
    mavenCentral()
    // Folia API 仓库（Canvas 基于 Folia）
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.tabooproject.org/main/")
}

dependencies {
    // 编译目标：Folia API（Canvas 26.2 = MC 26.2）
    compileOnly("dev.folia:folia-api:26.2.build.1-beta")
    compileOnly(kotlin("stdlib"))
    // 本地依赖：PistonChat（聊天颜色集成，运行时软依赖）
    compileOnly(fileTree("libs"))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}
