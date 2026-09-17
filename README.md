# Reden-Reforged

[![CurseForge Downloads](https://cf.way2muchnoise.eu/1693182.svg)](https://www.curseforge.com/minecraft/mc-mods/reden-reforged)

**Redstone EDEN, reforged.**  
Undo & Redo | Minecraft Debugging | Redstone Version Control

Reden-Reforged is a fork of the original Reden project, focused on improving the experience for redstone builders, testers, and technical Minecraft players.

## Features

- Undo & redo for player-caused changes
- Redstone debugging tools
- Useful quality-of-life tools for machine development

## Why this fork?

This fork exists because the original project has not been updated in over a year.

## Dependencies

- [MaLiLib](https://modrinth.com/mod/malilib)
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)

## Mod Platforms
- modrinth still under review
- [curseforge](https://www.curseforge.com/minecraft/mc-mods/reden-reforged)  
## Known issues
 - breaks when redstone uses moving entitys

## Added features

 - removed telemetry
 - added better container support when undoing

## Credits

Original project: [zly2006/reden-is-what-we-made](https://github.com/zly2006/reden-is-what-we-made)  
Original author: **zly2006**

Special thanks to the original developers and contributors for creating the foundation this project is based on.

## Build

If you want to build for pre-Minecraft 26 versions, you need to use the mapping version that is in the root

```bash
./gradlew build
