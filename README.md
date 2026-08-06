<div align="center">

# CnUsername

允许玩家使用中文名甚至特殊字符进入 Minecraft 服务器

**当前版本：v1.2.3 | Folia / Canvas 26.2 适配版**

</div>

## 简介

CnUsername 通过字节码修改（ASM）解除 Minecraft 服务端对玩家用户名的字符限制，允许使用中文名、特殊字符等进入服务器。

本分支为 **Folia / Canvas 26.2 适配版**，仅支持 Bukkit 系服务端（Spigot / Paper / Folia / Canvas）。

## 兼容性

| 项目 | 要求 |
|---|---|
| 服务端核心 | Folia / Canvas 26.2（MC 26.2），向下兼容 Spigot / Paper |
| Java 版本 | 25+ |

## 支持平台

| 平台 | 加载方式 | 说明 |
|---|---|---|
| Bukkit / Spigot / Paper / Folia / Canvas | 插件模式 + JavaAgent | 推荐使用 JavaAgent 解锁全部功能 |

## 下载

- **预构建版本**：前往 [Releases](../../releases) 页面下载最新构建
- **自行构建**：`gradle :BuildAllPlatforms:shadowJar`

产物：`BuildAllPlatforms/build/libs/CnUsername-<version>.jar`（全平台统一包，插件 + JavaAgent）

## 插件方式使用

1. 下载 `CnUsername-<version>.jar`
2. 放入服务端 `plugins/` 目录
3. 重启服务端
4. 如需自定义正则，在 `plugins/CnUsername/pattern.txt` 中填写

## JavaAgent 方式使用（推荐）

1. 下载 `CnUsername-<version>.jar`
2. 放入服务端根目录
3. 启动命令中添加 `-javaagent:CnUsername-<version>.jar`，例如：
   ```
   java -javaagent:CnUsername-1.2.3.jar -jar server.jar
   ```

## 注意事项

1. Paper 及其分支需在配置文件中设置 `perform-validate-username: false`
2. 安装 AuthMe 需修改 `config.yml` 中 `allowedNicknameCharacters` 正则
3. 安装 LuckPerms 需设置 `config.yml` 中 `allow-invalid-usernames: true`
4. 玩家名长度不能超过 16 个字符（双端解包器限制）
5. 默认正则：`^[a-zA-Z0-9_]{3,16}|[a-zA-Z0-9_一-龥]{2,10}$`

## 依赖

- **可选软依赖**：[Cheerimy-Studio](https://github.com/Cheerimy-Studio/MinecraftPlugins) — 正版完整性验证，未安装时输出警告（不影响功能）

## 衍生作品声明

All derivative works **must retain** the [Cheerimy-Studio](https://github.com/Cheerimy-Studio/MinecraftPlugins) plugin dependency and integrity verification content. If the Cheerimy-Studio verification plugin does not support the target version, the soft dependency and warning text **must not** be removed.

Commercial use requires obtaining a commercial license for the integrity verification plugin. Contact: QQ 3573840976.

Derivatives may also contact QQ to discuss removal of the soft dependency. This policy exists to ensure derivatives properly credit the original author.

## 致谢

- [XPPlugins/CnUsername](https://github.com/XPPlugins/CnUsername) — 原始项目
- [ASM](https://asm.ow2.io/) — Java 字节码操作框架
- [acj](https://github.com/InkerBot/acj) — JVM Agent 工具

## 许可证

[GPL-3.0](LICENSE)
