# 2.4.0 挂饰模型检查

## 2.4.1 烫豆透明缝隙回归（2026-09-14）

- 修复前新增三项贴图测试均复现透明接缝；填实豆粒边角后，`gradle test build` 四项测试全部通过。
- 覆盖 16/32/64/128 尺寸、有豆格子完全不透明、外部与作者留空区域透明，以及保留阴影/高光。
- 对运行中客户端的同一件 32×32 作品读取实际动态贴图：330 颗白豆对应 3504 个贴图像素，透明像素从 600 变为 0，白色区域的不透明颜色仍只有白色及灰色阴影。
- 仅热替换贴图生成方法，并释放此模组自己的两张旧缓存贴图；未改物品、作品或服务端数据。
- 实际截图和原始统计在 `temp/perler-solid-before/` 与 `temp/perler-solid-after/`；游戏当时打开暂停菜单，因此帧截图有模糊，透明覆盖率由未模糊的原生贴图核实。
- 持久化新版为 2.4.1，已更新服务器 `client-mods` 分发目录；当前实例 JAR 由 `tools/install-perler-solid-after-exit.ps1` 等游戏正常退出后替换，旧版备份到 `E:\MinecraftServers\Backups\chieri-perler-client-solid-20260914`。

## 2.4.0 历史记录

验证环境：2026-09-14，Minecraft 26.2 / Fabric；正在运行的 2.3.0 客户端通过定向热更新加载与新版 mixin 相同的 resolver-tail 钩子。未重启服务器，未修改玩家物品数据。

- `gradle test build`：通过。
- `tools/PerlerAttachmentCheckAgent.java`：运行在 Minecraft 渲染线程，读取主手镐子的实际挂饰数据（作品 3），检查四种手持视角均有且仅有一层挂饰。
- 第一/第三人称 × 左/右手，每种检查十组平移旋转矩阵。基础物品层和挂饰层的最终矩阵全部一致（容差 0.000001），因此挂环与物品上的固定位置保持一致。
- 复用同一 render state，从带挂饰的镐子切换到没有标记的副本，再切换空手：旧挂饰层全部消失。
- GUI、GROUND、FIXED 不添加挂饰层。
- 客户端调用不发包的双参数 `LivingEntity.swing`，截取待机、挥动和恢复帧；挂饰确实随镐子移动，未固定在屏幕角落。检查时玩家打开暂停菜单，截图背景有游戏自身的模糊效果。
- 热更新同时移除旧 HUD 和两种玩家模型上的旧 `PerlerCharmLayer`，避免重复显示。

原始输出：工作区 `temp/perler-attachment-check.txt`、`temp/perler-legacy-removal.txt` 和 `temp/perler-model-*.png`。

新版 JAR 包含正式 `ItemModelResolverMixin`；运行中的旧 JAR 在游戏退出前被系统锁定，所以持久化安装使用隐藏的一次性辅助进程。退出游戏后以 SHA-256 校验替换，旧版保留在 E 盘备份。此次没有强制退出客户端验证全新启动；下次启动应以 `perler-update/install-status.txt` 的 SUCCESS 及客户端模组列表 2.4.0 为准。
