<div align="center">

# DatapackLoader

Automatically add datapacks to your Minecraft server

**Current version: v1.4.3 | Folia / Canvas 26.2**

</div>

## Introduction

DatapackLoader automatically adds datapacks to your server. Supports URL import, manual drop, and starter datapack mode. Fully compatible with Folia / Canvas multithreaded world generation.

Based on [lichenaut/DatapackLoader](https://github.com/lichenaut/DatapackLoader).

## Compatibility

| Item | Requirement |
|---|---|
| Server Core | Folia / Canvas 26.2 (MC 26.2), backward compatible with Spigot / Paper |
| Java Version | 25+ |

## Download

- **Pre-built**: Visit [Releases](../../releases) to download
- **Build yourself**: `gradle shadowJar`

Output: `build/libs/DatapackLoader-1.4.3.jar`

## Methods

There are three methods for adding datapacks:

1. Paste a URL into the `/dl import <url>` console command
2. Drag and drop into the plugin's `datapacks` folder (`plugins/DatapackLoader/datapacks/`)
3. Enable `starter-datapack` in `config.yml`

## Commands

| Command | Permission | Description |
|---|---|---|
| `/dl` | `datapackloader.command` | Base command |
| `/dl help` | `datapackloader.command.help` | Link to documentation |
| `/dl import <url>` | (console only) | Import a datapack from a .zip URL |
| `/dltp <worldname>` | `datapackloader.command.tp` | Teleport to world center in spectator |

## Notes

- Datapacks are stored in `plugins/DatapackLoader/datapacks/`
- New datapacks require a server restart to apply
- Folia multithreaded chunk generation is fully supported

## Dependency

- **Optional soft dependency**: [Cheerimy-Studio](https://github.com/Cheerimy-Studio/MinecraftPlugins) — integrity verification

## License

[GPL-3.0](LICENSE.txt)
