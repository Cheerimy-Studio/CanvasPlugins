<div align="center">

# 2B2TCore

面向 Folia / Canvas 分支核心的 Anarchy 服务器核心插件

**当前版本：v1.0.0**

</div>

## 简介

2B2TCore 是一款为 Folia 多线程服务端及其分支（如 Canvas 26.2）设计的 Anarchy 服务器核心插件，提供复制漏洞、玩家统计、自杀命令等常用功能。所有功能均严格遵循 Folia 线程安全规则，通过区域线程调度和 EntityScheduler 确保多线程环境下的稳定性。

## 兼容性

| 项目 | 要求 |
|---|---|
| 服务端核心 | Folia / Canvas（26.2，MC 26.2） |
| Java 版本 | 25+ |
| Kotlin | 2.3.21 |
| TabooLib | 6.2.4 |

## 功能列表

所有功能均可在 `config.yml` 中独立开关。

### 复制漏洞

| 功能 | 触发方式 | 权限节点 |
|---|---|---|
| 命令复制 | `/dupe` | `core.dupe.command` |
| 物品展示框复制 | 旋转展示框（概率触发） | `core.dupe.item-frame` |
| 鸡刷复制 - Xin 模式 | 右键鸡存入潜影盒，到点掉落 | `core.dupe.chicken.xin` |
| 鸡刷复制 - 点击模式 | 右键鸡复制手持物品（带冷却） | `core.dupe.chicken.click` |
| 驴复制 - Xin 模式 | 击杀驮兽掉落其库存 | `core.dupe.donkey.xin` |
| 驴复制 - Org 模式 | 骑驮兽下线复制其库存 | `core.dupe.donkey.org` |
| 破坏放置复制 | 累计破坏潜影盒到指定次数 | `core.dupe.mine-and-place` |

### 杂项功能

| 功能 | 命令 | 权限节点 | 说明 |
|---|---|---|---|
| 自杀 | `/suicide`（别名 `514`、`kill`） | `core.suicide`（默认允许） | OP 走原版 kill 支持选择器 |
| 聊天颜色 | 自动生效 | `core.chatcolor.vip` / `core.chatcolor.op` | 需安装 PistonChat |

### 玩家统计

| 命令 | 别名 | 数据项 |
|---|---|---|
| `/stat` | `stats`、`statistics`、`统计` | 击杀、死亡、K/D、加入次数、退出次数、在线时长 |

数据通过 Bukkit PDC 持久化存储在玩家身上，无需数据库。

### 管理命令

| 命令 | 权限 | 说明 |
|---|---|---|
| `/core`（别名 `/2b2t`） | `core.reload` | 主命令 |
| `/core info` | `core.reload` | 查看版本和 GitHub 仓库 |
| `/core reload` | `core.reload` | 重载配置文件 |
| `/core clearcache` | `core.reload` | 清空鸡刷计时器和破坏计数缓存 |

## 依赖

- **运行时无硬依赖**，独立运行
- **可选软依赖**：[PistonChat](https://github.com/AlexProgrammerDE/PistonChat) — 用于聊天颜色功能，未安装时该功能自动禁用，其余功能不受影响

## 权限一览

| 权限节点 | 默认 | 说明 |
|---|---|---|
| `core.reload` | OP | 管理命令（重载/清缓存/信息） |
| `core.suicide` | 所有玩家 | 自杀命令 |
| `core.dupe.command` | OP | 命令复制 |
| `core.dupe.item-frame` | OP | 物品展示框复制 |
| `core.dupe.chicken.xin` | OP | 鸡刷复制 - Xin 模式 |
| `core.dupe.chicken.click` | OP | 鸡刷复制 - 点击模式 |
| `core.dupe.donkey.xin` | OP | 驴复制 - Xin 模式 |
| `core.dupe.donkey.org` | OP | 驴复制 - Org 模式 |
| `core.dupe.mine-and-place` | OP | 破坏放置复制 |
| `core.chatcolor.vip` | OP | 聊天颜色 - VIP 组 |
| `core.chatcolor.op` | OP | 聊天颜色 - OP 组 |

## 构建

```
./gradlew build
```

产物位于 `build/libs/2B2TCore-<version>.jar`。

## Folia 线程安全实现

- 所有事件监听器在对应区域线程内同步执行实体/世界/库存操作
- 鸡刷复制 Xin 模式使用 `EntityScheduler.runAtFixedRate` 在鸡自身区域线程周期检测
- 驴复制 Org 模式延迟打开库存使用 `EntityScheduler.runDelayed`
- 在线时长 PDC 操作在玩家区域线程内同步执行
- 异步任务仅遍历 `ConcurrentHashMap`，不操作实体/世界

## 致谢

- [TabooLib](https://tabooproject.org) — 插件开发框架
- [PistonChat](https://github.com/AlexProgrammerDE/PistonChat) — 聊天颜色集成
- [AnarchyCore-NextGen](https://github.com/LuminusPlugins/AnarchyCore-NextGen) — 原始项目
- [Canvas](https://github.com/CraftCanvasMC/Canvas) — Folia 分支核心

## 许可证

[GNU LGPL v3](LICENSE)
