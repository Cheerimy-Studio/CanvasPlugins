<div align="center">
<h1>AnarchyCore-NextGen</h1>

[English](README.md) | 中文
</div>

## 依赖

- [CrystalKillListener v2.0](https://github.com/GuangChen2333/CrystalKillListener/releases/tag/v2.0)

## 发布
Modrinth, MineBBS, GitHub

## 功能
所有功能都可以在 `config.yml` 中开启或关闭。

### 复制漏洞
- [x] 鸡复制 (可设置时间间隔)
- [x] 指令复制
- [x] 驴复制
- [x] 物品展示框复制
- [x] 挖掘放置复制 (x+1)

### 杂项功能
- [x] 玩家统计数据 (击杀数、死亡数、加入次数、退出次数以及权限占位符)
- [x] 自杀指令 (若发送者为管理员则跳转为原版指令)

### 限制功能
- [x] 末影水晶速度限制
- [x] 末地传送门TNT限制

### 搭配以下插件食用最佳
- [AnarchyExploitFixes](https://modrinth.com/plugin/anarchyexploitfixes)
- [NoJoinMessage](https://hangar.papermc.io/MCFurina/NoJoinMessage)
- [LeeesBungeeQueue](https://github.com/XeraPlugins/LeeesBungeeQueue)
- 

## 致谢
- [CrystalKillListener 的作者 GuangChen2333](https://github.com/GuangChen2333)
- [TabooLib](https://tabooproject.org)
- [SpigotMC](https://spigotmc.org)
## 权限
- `anarchy.reload`
- `anarchy.suicide`
- `anarchy.color.name`
- `anarchy.color.message`
- `anarchy.dupe.command`
- `anarchy.dupe.mine-and-place`
- `anarchy.dupe.item-frame`
- `anarchy.dupe.donkey.xin`
- `anarchy.dupe.donkey.org`
- `anarchy.dupe.chicken.xin`
- `anarchy.dupe.chicken.click`

## 构建通用版本

通用版本用于正常使用，其中不包含 TabooLib。

```
./gradlew build
```

## 构建开发版本

供开发者调试，但不可直接使用。

```
./gradlew taboolibBuildApi -PDeleteCode
```
