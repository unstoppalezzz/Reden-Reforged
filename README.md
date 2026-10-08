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
- [modrinth](https://modrinth.com/mod/reden-reforged) still under review
- [curseforge](https://www.curseforge.com/minecraft/mc-mods/reden-reforged)  
## Known issues

## Planed features

- Backporting to more older versions starting with 1.20

## Added features

 - removed telemetry
 - added better container support when undoing
 - added support for pressure plates and string updates
 - updated to latest version
   
## Credits

Original project: [zly2006/reden-is-what-we-made](https://github.com/zly2006/reden-is-what-we-made)  
Original author: **zly2006**

Special thanks to the original developers and contributors for creating the foundation this project is based on.

## Build

Run one of the two command to build the project

```bash
# every version
./gradlew build
# a single version (replace 1.21.11 with the version you want to build)
./gradlew :1.21.11:build
```
