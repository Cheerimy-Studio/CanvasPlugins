<div align="center">
<h1>AnarchyCore-NextGen</h1>

English | [中文](README-cn.md)
</div>

## Depends

- [CrystalKillListener v2.0](https://github.com/GuangChen2333/CrystalKillListener/releases/tag/v2.0), it still can use even it's archived

## Features
All the features can be enabled or disabled in `config.yml`.

### Duplications
- [x] Chicken Dupe (with periods setting)
- [x] Command Dupe
- [x] Donkey Dupe
- [x] Item Frame Dupe
- [x] Mine and Place Dupe (x+1)

### Miscellaneous
- [x] Player Statistics (Kills, Deaths, Joins, Quits and permission placeholders)
- [x] Suicide Command (Jump to Vanilla command if the sender is op)
- [x] Custom Chat Color

### Limits
- [x] Crystal Speed Limit
- [x] End Portal TNT Limit

## Thanks
- [GuangChen2333, the author of CrystalKillListener](https://github.com/GuangChen2333)
- [TabooLib](https://tabooproject.org)
- [SpigotMC](https://spigotmc.org)

## Permissions
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

## Build Universal Version

The universal version is for normally using, and there's no TabooLib in it.

```
./gradlew build
```

## Build Development Version

This version is for developers debugging, and it can't use normally.

```
./gradlew taboolibBuildApi -PDeleteCode
```