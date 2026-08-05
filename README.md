<div align="center">

# CanvasPlugins

面向 [Canvas](https://github.com/CraftCanvasMC/Canvas)（Folia 分支）核心的 2B2T 插件集合

**如果这个项目对你有帮助，欢迎点个 Star ⭐ 支持一下！**

</div>

## 简介

CanvasPlugins 是一组基于 Canvas（Folia 多线程服务端分支）的 2B2T 风格服务器插件集合。所有插件均严格遵循 Folia 线程安全规则，通过区域线程调度和 EntityScheduler 确保多线程环境下的稳定性。

## 包含插件

| 插件 | 分支 | 说明 |
|---|---|---|
| [2B2TCore](../2B2TCore) | [`2B2TCore`](../tree/2B2TCore) | Anarchy 服务器核心插件，提供复制漏洞、玩家统计、自杀命令等功能 |
| [CnUsername](../CnUsername) | [`CnUsername`](../tree/CnUsername) | 允许玩家使用中文名进入服务器，Folia/Canvas 26.2 适配版 |

## 下载

- **预构建版本**：前往 [Releases](../../releases) 页面下载最新构建
- **自行构建**：克隆对应插件分支后执行 `./gradlew build`，产物位于 `build/libs/`

## 兼容性

| 项目 | 要求 |
|---|---|
| 服务端核心 | Folia / Canvas 26.2（MC 26.2） |
| Java 版本 | 25+ |

## 致谢

- [TabooLib](https://tabooproject.org) — 插件开发框架
- [Canvas](https://github.com/CraftCanvasMC/Canvas) — Folia 分支核心
- [PistonChat](https://github.com/AlexProgrammerDE/PistonChat) — 聊天颜色集成
- [AnarchyCore-NextGen](https://github.com/LuminusPlugins/AnarchyCore-NextGen) — 原始项目

## 许可证

[GNU LGPL v3](LICENSE)

---

<div align="center">

**喜欢这个项目？给个 Star 让更多人看到 ⭐**

</div>
