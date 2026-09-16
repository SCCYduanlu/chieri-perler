# Chieri Perler / Chieri 拼豆工坊

A Fabric mod for creating, storing and displaying fuse-bead artwork in Minecraft. The server owns all project data and validates every edit; the optional client companion provides a full-screen 291-colour editor, exact-RGB artwork rendering and animated equipment charms.

这是一个适用于 Minecraft Fabric 的拼豆模组。服务端负责材料、工程、领地权限与持久化；可选客户端模组提供全屏 291 色编辑器、原始 RGB 成品渲染，以及会随武器和工具移动的拼豆挂饰。

## Features / 功能

- Persistent perler tables and multiple projects in `16×16`, `32×32`, `64×64` and `128×128` sizes.
- A fixed 291-entry palette with code, series, Chinese/English names and RGB values.
- Server-authoritative inventory consumption, project ownership, range checks and atomic saves.
- Pencil, eraser and eyedropper tools; continuous stroke drawing; zoom, pan, search and 50-step undo/redo.
- Fuse completed work into a map item, then place it on walls, floors or ceilings.
- Attach artwork to a weapon or tool as a charm; compatible clients render it with the held-item transform.
- SGUI fallback for Java clients without the companion mod and for Bedrock players.
- Flan claim-permission integration.

## Repository layout / 仓库结构

- `server/` — required server-side gameplay, persistence, SGUI fallback and permission checks.
- `client/` — optional Java client editor and renderers. Do not install this JAR on the server.

## Requirements / 运行环境

- Minecraft Java Edition `26.2`
- Java `25`
- Fabric Loader `0.19.3` or newer
- Fabric API `0.158.0+26.2` or newer
- Server only: [SGUI `2.1.0+26.2`](https://github.com/Patbox/sgui) and [Flan `1.12.8`](https://github.com/Flemmli97/Flan)

## Build / 构建

```bash
./gradlew clean test build
```

On Windows:

```powershell
.\gradlew.bat clean test build
```

The built JARs are written to:

- `server/build/libs/chieri-perler-3.1.0.jar`
- `client/build/libs/chieri-perler-client-3.1.0.jar`

## Install / 安装

1. Put the server JAR, Fabric API, SGUI and Flan in the Fabric server's `mods` directory.
2. Java players who want the full editor and exact-colour rendering put the client JAR and Fabric API in their instance's `mods` directory.
3. Restart Minecraft completely after replacing the client JAR.

Players without the companion client can still use the server-side SGUI. Exact-RGB art and equipment charms require the client mod to be visible as intended.

## Basic play / 基本玩法

1. Craft and place a perler table, then right-click it.
2. Refill a colour family with `1 dye + 1 honeycomb`; one refill adds 128 beads.
3. Create a project, select a palette colour and draw.
4. Consume `1 coal + 1 empty map` to fuse a non-empty project.
5. Place the result directly on a surface, or install it onto the main-hand weapon/tool from the perler table.

Commands include `/pindou`, `/pindou craft`, `/pindou charm`, `/pindou uncharm` and `/pindou admin ...`.

Server data is stored under `world/data/chieri-perler/`. Do not commit or share a live server's data directory.

## Palette notice / 色表说明

The RGB table was transcribed from a user-supplied `Mard 291.xlsx` reference. The spreadsheet itself is not redistributed. Palette codes and product names may be trademarks of their respective owners; this project is unaffiliated with any bead manufacturer. See [docs/palette.md](docs/palette.md).

## License

Source code is released under the [MIT License](LICENSE). Minecraft, Fabric, SGUI, Flan and any palette/product names remain the property of their respective owners.

