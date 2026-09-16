package com.chiericat.perler;

import io.github.flemmli97.flan.api.ClaimHandler;
import io.github.flemmli97.flan.api.permission.BuiltinPermission;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.BlockHitResult;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

final class PerlerController {
    final MinecraftServer server;
    final PerlerConfig config;
    final PerlerState state;
    private final Path statePath;
    private int auditTicks;

    PerlerController(MinecraftServer server, PerlerConfig config, PerlerState state, Path statePath) {
        this.server = server;
        this.config = config;
        this.state = state;
        this.statePath = statePath;
    }

    void start() throws Exception {
        refreshFusedMaps();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) ensureEntityArt(entity);
        }
        save();
    }

    void shutdown() {
        try {
            save();
        } catch (Exception exception) {
            PerlerMod.LOG.error("Unable to save perler state during shutdown", exception);
        }
    }

    void tick() {
        if (++auditTicks < 200) return;
        auditTicks = 0;
        boolean changed = false;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean inventoryChanged = false;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                PerlerState.Project project = state.findProject(PerlerItems.projectId(stack));
                if (project != null && project.fused) {
                    if (PerlerItems.hasCharm(stack)) inventoryChanged |= PerlerItems.ensureCharmPixels(stack, project);
                    if (PerlerItems.isArt(stack)) inventoryChanged |= PerlerItems.ensureArtPixels(stack, project);
                }
            }
            if (inventoryChanged) InventoryOps.changed(player);
        }
        for (PerlerState.Station station : state.stations.values()) {
            if (!station.placed) continue;
            ServerLevel level = level(station.dimension);
            BlockPos pos = new BlockPos(station.x, station.y, station.z);
            if (level != null && level.hasChunkAt(pos) && !level.getBlockState(pos).is(Blocks.CRAFTER)) {
                station.placed = false;
                station.updatedAt = System.currentTimeMillis();
                changed = true;
                PerlerMod.LOG.warn("PERLER_STATION_MISSING id={} pos={} {} {} {}", station.id,
                        station.dimension, station.x, station.y, station.z);
            }
        }
        if (changed) {
            try {
                save();
            } catch (Exception exception) {
                PerlerMod.LOG.error("Unable to persist missing perler station audit", exception);
            }
        }
    }

    void ensureEntityArt(Entity entity) {
        if (!(entity instanceof ItemFrame frame)) return;
        ItemStack stack = frame.getItem();
        if (!PerlerItems.isArt(stack)) return;
        PerlerState.Project project = state.findProject(PerlerItems.projectId(stack));
        if (project != null && project.fused && PerlerItems.ensureArtPixels(stack, project)) frame.setItem(stack, false);
    }

    InteractionResult useBlock(ServerPlayer player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (PerlerItems.isTable(held)) {
            try {
                place(player, held, hit);
                return InteractionResult.SUCCESS_SERVER;
            } catch (Exception exception) {
                PerlerMod.message(player, PerlerMod.safe(exception));
                return InteractionResult.FAIL;
            }
        }
        if (PerlerItems.isArt(held)) {
            try {
                placeArt(player, held, hit);
                return InteractionResult.SUCCESS_SERVER;
            } catch (Exception exception) {
                PerlerMod.message(player, PerlerMod.safe(exception));
                return InteractionResult.FAIL;
            }
        }
        PerlerState.Station station = state.at(player.level().dimension().identifier().toString(),
                hit.getBlockPos().getX(), hit.getBlockPos().getY(), hit.getBlockPos().getZ());
        if (station == null) return InteractionResult.PASS;
        if (!ClaimHandler.canInteract(player, hit.getBlockPos(), BuiltinPermission.OPENCONTAINER)) {
            PerlerMod.message(player, "你没有该领地的容器交互权限。 ");
            return InteractionResult.FAIL;
        }
        PerlerNetworking.openOrFallback(player, station);
        player.level().playSound(null, hit.getBlockPos(), SoundEvents.UI_LOOM_SELECT_PATTERN,
                SoundSource.BLOCKS, 0.7f, 1.1f);
        return InteractionResult.SUCCESS_SERVER;
    }

    boolean beforeBreak(ServerLevel level, ServerPlayer player, BlockPos pos) {
        PerlerState.Station station = state.at(level.dimension().identifier().toString(), pos.getX(), pos.getY(), pos.getZ());
        if (station == null) return true;
        if (!ClaimHandler.canInteract(player, pos, BuiltinPermission.BREAK)) return false;
        if (!owns(player, station)) {
            PerlerMod.message(player, "只有拼豆台主人或管理员可以打包这个台子。 ");
            return false;
        }
        try {
            station.placed = false;
            station.dimension = "";
            station.updatedAt = System.currentTimeMillis();
            save();
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            giveOrDrop(player, PerlerItems.table(station.id));
            PerlerMod.message(player, "已打包拼豆台 " + station.id + "，材料和 " + station.projects.size() + " 个工程仍完整保存。 ");
            return false;
        } catch (Exception exception) {
            station.placed = true;
            station.dimension = level.dimension().identifier().toString();
            PerlerMod.message(player, "打包失败，为保护工程已取消破坏：" + PerlerMod.safe(exception));
            return false;
        }
    }

    void craft(ServerPlayer player) throws Exception {
        InventoryOps.require(player, Items.CRAFTER, 1, "合成器");
        InventoryOps.require(player, Items.GLASS_PANE, 5, "玻璃板");
        InventoryOps.require(player, Items.HONEYCOMB, 1, "蜜脾");
        InventoryOps.require(player, Items.REDSTONE, 2, "红石");
        if (!InventoryOps.canFit(player, PerlerItems.table(""))) throw new IllegalArgumentException("背包没有空位。 ");
        InventoryOps.remove(player, Items.CRAFTER, 1);
        InventoryOps.remove(player, Items.GLASS_PANE, 5);
        InventoryOps.remove(player, Items.HONEYCOMB, 1);
        InventoryOps.remove(player, Items.REDSTONE, 2);
        InventoryOps.add(player, PerlerItems.table(""));
        PerlerMod.message(player, "已制作拼豆台；放置后才会分配永久台子 ID。 ");
    }

    void giveBlank(ServerPlayer player) {
        giveOrDrop(player, PerlerItems.table(""));
        PerlerMod.message(player, "已获得一个新拼豆台。 ");
    }

    void recover(ServerPlayer player, String id) throws Exception {
        PerlerState.Station station = state.stations.get(id.toUpperCase(Locale.ROOT));
        if (station == null) throw new IllegalArgumentException("没有这个拼豆台 ID。 ");
        if (station.placed) throw new IllegalArgumentException("该拼豆台仍记录为已放置，不能复制恢复。 ");
        giveOrDrop(player, PerlerItems.table(station.id));
        PerlerMod.message(player, "已恢复拼豆台 " + station.id + " 的搬运物品。 ");
    }

    PerlerState.Project createProject(ServerPlayer player, PerlerState.Station station, int size) throws Exception {
        requirePlaced(station);
        if (!config.allowsSize(size)) throw new IllegalArgumentException("不支持这个拼豆板尺寸。 ");
        if (station.projects.size() >= config.maxProjectsPerTable)
            throw new IllegalArgumentException("这个拼豆台最多保存 " + config.maxProjectsPerTable + " 个工程。 ");
        long owned = station.projects.values().stream().filter(project -> project.ownerId.equals(player.getUUID().toString())).count();
        PerlerState.Project project = new PerlerState.Project();
        project.id = uniqueId("P");
        project.name = "作品 " + (owned + 1);
        project.ownerId = player.getUUID().toString();
        project.ownerName = player.getGameProfile().name();
        project.size = size;
        project.colors = new int[size * size];
        Arrays.fill(project.colors, -1);
        project.createdAt = project.updatedAt = System.currentTimeMillis();
        station.projects.put(project.id, project);
        station.updatedAt = project.updatedAt;
        save();
        return project;
    }

    void refill(ServerPlayer player, PerlerState.Station station, int family) throws Exception {
        requirePlaced(station);
        if (family < 0 || family >= PerlerPalette.FAMILIES) throw new IllegalArgumentException("色系无效。 ");
        if (station.reservoirs[family] > config.maxReservoirPerFamily - config.beadsPerBatch)
            throw new IllegalArgumentException("这个色系的拼豆仓已经装满。 ");
        InventoryOps.require(player, PerlerPalette.DYES[family], 1, PerlerPalette.NAMES[family] + "色染料");
        InventoryOps.require(player, Items.HONEYCOMB, 1, "蜜脾");
        InventoryOps.remove(player, PerlerPalette.DYES[family], 1);
        InventoryOps.remove(player, Items.HONEYCOMB, 1);
        station.reservoirs[family] += config.beadsPerBatch;
        station.updatedAt = System.currentTimeMillis();
        save();
        player.level().playSound(null, player.blockPosition(), SoundEvents.BEEHIVE_WORK,
                SoundSource.PLAYERS, 0.8f, 1.35f);
        PerlerMod.message(player, PerlerPalette.NAMES[family] + "系补充 " + config.beadsPerBatch
                + " 颗，现在共有 " + station.reservoirs[family] + " 颗。 ");
    }

    void paint(ServerPlayer player, PerlerState.Station station, PerlerState.Project project,
               int pixel, int selectedColor) throws Exception {
        requireEditable(player, station, project);
        if (pixel < 0 || pixel >= project.colors.length) throw new IllegalArgumentException("像素位置无效。 ");
        if (selectedColor < 0 || selectedColor >= PerlerPalette.COLORS) throw new IllegalArgumentException("颜色无效。 ");
        int old = project.colors[pixel];
        if (old == selectedColor) {
            project.colors[pixel] = -1;
            station.reservoirs[PerlerPalette.family(old)]++;
        } else {
            int family = PerlerPalette.family(selectedColor);
            if (station.reservoirs[family] <= 0)
                throw new IllegalArgumentException(PerlerPalette.NAMES[family] + "系耗材不足，请先去材料仓补充。 ");
            station.reservoirs[family]--;
            if (old >= 0) station.reservoirs[PerlerPalette.family(old)]++;
            project.colors[pixel] = selectedColor;
        }
        project.updatedAt = station.updatedAt = System.currentTimeMillis();
        save();
    }

    void paintBatch(ServerPlayer player, PerlerState.Station station, PerlerState.Project project,
                    long expectedRevision, int[] pixels, int[] selectedColors) throws Exception {
        requireEditable(player, station, project);
        if (pixels == null || selectedColors == null || pixels.length != selectedColors.length
                || pixels.length == 0 || pixels.length > 4096) {
            throw new IllegalArgumentException("单次绘制数据无效。 ");
        }
        if (expectedRevision != project.updatedAt) {
            throw new IllegalArgumentException("工程已在其他窗口更新，已重新同步，请再试一次。 ");
        }

        PerlerEdits.Result edit = PerlerEdits.apply(project.colors, station.reservoirs, pixels, selectedColors);
        int[] nextColors = edit.colors();
        int[] nextReservoirs = edit.reservoirs();

        int[] oldColors = project.colors;
        int[] oldReservoirs = station.reservoirs;
        long oldProjectUpdatedAt = project.updatedAt;
        long oldStationUpdatedAt = station.updatedAt;
        project.colors = nextColors;
        station.reservoirs = nextReservoirs;
        project.updatedAt = Math.max(System.currentTimeMillis(), oldProjectUpdatedAt + 1);
        station.updatedAt = Math.max(project.updatedAt, oldStationUpdatedAt + 1);
        try {
            save();
        } catch (Exception exception) {
            project.colors = oldColors;
            station.reservoirs = oldReservoirs;
            project.updatedAt = oldProjectUpdatedAt;
            station.updatedAt = oldStationUpdatedAt;
            throw exception;
        }
    }

    void fuse(ServerPlayer player, PerlerState.Station station, PerlerState.Project project) throws Exception {
        requireEditable(player, station, project);
        if (project.filled() == 0) throw new IllegalArgumentException("空白拼豆板不能烫豆。 ");
        String missing = PerlerRequirements.missingFuseMaterials(
                InventoryOps.count(player, Items.COAL), InventoryOps.count(player, Items.MAP));
        if (!missing.isEmpty()) throw new IllegalArgumentException(missing);
        MapId id = createMap(player.level(), project);
        project.fused = true;
        project.mapId = id.id();
        project.fusedAt = project.updatedAt = station.updatedAt = System.currentTimeMillis();
        save();
        InventoryOps.remove(player, Items.COAL, 1);
        InventoryOps.remove(player, Items.MAP, 1);
        giveOrDrop(player, PerlerItems.art(project));
        player.level().playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE,
                SoundSource.PLAYERS, 0.9f, 1.25f);
        player.level().sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0,
                player.getZ(), 24, 0.5, 0.6, 0.5, 0.05);
        PerlerMod.message(player, "烫豆完成！成品已发放，工程现已锁定。手持成品右键方块表面可直接张贴。 ");
    }

    void reprint(ServerPlayer player, PerlerState.Project project) throws Exception {
        if (!project.fused) throw new IllegalArgumentException("工程尚未烫豆。 ");
        InventoryOps.require(player, Items.MAP, 1, "空地图");
        MapId id = new MapId(project.mapId);
        if (player.level().getMapData(id) == null) {
            id = createMap(player.level(), project);
            project.mapId = id.id();
            project.updatedAt = System.currentTimeMillis();
            save();
        }
        InventoryOps.remove(player, Items.MAP, 1);
        giveOrDrop(player, PerlerItems.art(project));
        PerlerMod.message(player, "已使用 1 张空地图制作成品副本。 ");
    }

    void attachCharm(ServerPlayer player) {
        ItemStack weapon = player.getMainHandItem();
        ItemStack art = player.getOffhandItem();
        if (!PerlerItems.isArt(art)) throw new IllegalArgumentException("副手必须持本服务器制作的拼豆成品。 ");
        PerlerState.Project project = state.findProject(PerlerItems.projectId(art));
        if (project == null || !project.fused) throw new IllegalArgumentException("找不到这个拼豆成品的原工程。 ");
        PerlerItems.attachCharm(weapon, project);
        art.shrink(1);
        InventoryOps.changed(player);
        PerlerMod.message(player, "已把“" + project.name + "”固定为主手武器或工具挂饰。 ");
    }

    void attachCharmFromTable(ServerPlayer player, PerlerState.Project project) {
        if (!project.fused) throw new IllegalArgumentException("工程尚未烫豆。 ");
        ItemStack weapon = player.getMainHandItem();
        InventoryOps.require(player, Items.MAP, 1, "空地图");
        PerlerItems.attachCharm(weapon, project);
        InventoryOps.remove(player, Items.MAP, 1);
        InventoryOps.changed(player);
        PerlerMod.message(player, "已使用 1 张空地图，把“" + project.name
                + "”从拼豆台安装为主手武器或工具挂饰。 ");
    }

    void detachCharm(ServerPlayer player) {
        PerlerItems.Charm charm = PerlerItems.charm(player.getMainHandItem());
        PerlerState.Project project = state.findProject(charm.projectId());
        if (project == null || !project.fused) throw new IllegalArgumentException("原拼豆工程不存在，无法安全拆除。 ");
        PerlerItems.removeCharm(player.getMainHandItem());
        InventoryOps.changed(player);
        giveOrDrop(player, PerlerItems.art(project));
        PerlerMod.message(player, "已拆下拼豆挂饰并返还成品。 ");
    }

    void charmEffect(ServerPlayer player) {
        if (!PerlerItems.hasCharm(player.getMainHandItem())) return;
        ServerLevel level = player.level();
        level.sendParticles(ParticleTypes.WAX_ON, player.getX(), player.getY() + 1.1, player.getZ(),
                10, 0.55, 0.45, 0.55, 0.08);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(),
                3, 0.3, 0.3, 0.3, 0.025);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.35f, 1.75f);
    }

    boolean removeDirectArt(ServerPlayer player, Entity entity) {
        if (!(entity instanceof ItemFrame frame) || !frame.entityTags().contains(PerlerItems.DIRECT_ART_TAG)) return false;
        if (!ClaimHandler.canInteract(player, frame.blockPosition(), BuiltinPermission.BREAK)) {
            PerlerMod.message(player, "你没有该领地的破坏权限，不能取下拼豆成品。 ");
            return true;
        }
        ItemStack art = frame.getItem().copy();
        frame.setItem(ItemStack.EMPTY, false);
        frame.discard();
        if (!player.hasInfiniteMaterials() && PerlerItems.isArt(art)) giveOrDrop(player, art);
        player.level().playSound(null, frame.blockPosition(), SoundEvents.ITEM_FRAME_REMOVE_ITEM,
                SoundSource.PLAYERS, 0.8f, 1.15f);
        PerlerMod.message(player, player.hasInfiniteMaterials()
                ? "已移除直接张贴的拼豆成品。 " : "已取下拼豆成品并放回背包。 ");
        return true;
    }

    String status() {
        int projects = state.stations.values().stream().mapToInt(station -> station.projects.size()).sum();
        long fused = state.stations.values().stream().flatMap(station -> station.projects.values().stream())
                .filter(project -> project.fused).count();
        long placed = state.stations.values().stream().filter(station -> station.placed).count();
        return "拼豆工坊运行正常｜台子 " + state.stations.size() + "（已放置 " + placed + "）｜工程 "
                + projects + "｜成品 " + fused + "｜每次补充 " + config.beadsPerBatch + " 颗";
    }

    private void place(ServerPlayer player, ItemStack stack, BlockHitResult hit) throws Exception {
        if (!player.mayBuild()) throw new IllegalArgumentException("当前游戏模式不能放置方块。 ");
        ServerLevel level = player.level();
        BlockPos clicked = hit.getBlockPos();
        BlockPos target = level.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(hit.getDirection());
        if (level.isOutsideBuildHeight(target) || !level.getWorldBorder().isWithinBounds(target))
            throw new IllegalArgumentException("这个位置不能放置拼豆台。 ");
        if (!level.getBlockState(target).canBeReplaced()) throw new IllegalArgumentException("放置位置被占用。 ");
        if (!ClaimHandler.canInteract(player, target, BuiltinPermission.PLACE))
            throw new IllegalArgumentException("你没有该领地的放置权限。 ");

        String id = PerlerItems.stationId(stack).toUpperCase(Locale.ROOT);
        PerlerState.Station station;
        if (id.isBlank()) {
            station = new PerlerState.Station();
            station.id = uniqueId("T");
            station.ownerId = player.getUUID().toString();
            station.ownerName = player.getGameProfile().name();
            station.createdAt = System.currentTimeMillis();
            state.stations.put(station.id, station);
        } else {
            station = state.stations.get(id);
            if (station == null) throw new IllegalArgumentException("这个拼豆台 ID 不存在或来自其他世界。 ");
            if (!owns(player, station)) throw new IllegalArgumentException("只有台子主人或管理员可以重新放置它。 ");
            if (station.placed) throw new IllegalArgumentException("该台子已经放置，已阻止重复复制。 ");
        }
        level.setBlock(target, Blocks.CRAFTER.defaultBlockState(), Block.UPDATE_ALL);
        station.placed = true;
        station.dimension = level.dimension().identifier().toString();
        station.x = target.getX();
        station.y = target.getY();
        station.z = target.getZ();
        station.updatedAt = System.currentTimeMillis();
        try {
            save();
        } catch (Exception exception) {
            level.setBlock(target, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            station.placed = false;
            if (id.isBlank()) state.stations.remove(station.id);
            throw exception;
        }
        if (!player.isCreative()) stack.shrink(1);
        InventoryOps.changed(player);
        PerlerMod.message(player, "拼豆台已部署，ID：" + station.id + "；右键打开工坊。 ");
    }

    private void placeArt(ServerPlayer player, ItemStack stack, BlockHitResult hit) {
        if (!player.mayBuild()) throw new IllegalArgumentException("当前游戏模式不能张贴拼豆成品。 ");
        ServerLevel level = player.level();
        Direction face = hit.getDirection();
        BlockPos target = hit.getBlockPos().relative(face);
        if (level.isOutsideBuildHeight(target) || !level.getWorldBorder().isWithinBounds(target))
            throw new IllegalArgumentException("这个位置不能张贴拼豆成品。 ");
        if (!ClaimHandler.canInteract(player, target, BuiltinPermission.PLACE))
            throw new IllegalArgumentException("你没有该领地的放置权限。 ");

        ItemFrame frame = new ItemFrame(level, target, face);
        frame.setInvisible(true);
        frame.addTag(PerlerItems.DIRECT_ART_TAG);
        if (face.getAxis().isVertical()) {
            frame.setRotation(Math.floorMod(player.getDirection().get2DDataValue() * 2, 8));
        }
        ItemStack displayed = stack.copy();
        displayed.setCount(1);
        frame.setItem(displayed, false);
        if (!frame.survives()) throw new IllegalArgumentException("该表面空间不足，或已经贴有其他装饰。 ");
        if (!level.addFreshEntity(frame)) throw new IllegalStateException("成品张贴失败，请换一个位置重试。 ");
        frame.playPlacementSound();
        if (!player.hasInfiniteMaterials()) stack.shrink(1);
        InventoryOps.changed(player);
        String surface = face == Direction.UP ? "地面" : face == Direction.DOWN ? "天花板" : "墙面";
        PerlerMod.message(player, "已将拼豆成品直接贴在" + surface + "；左键成品可取回。 ");
    }

    private MapId createMap(ServerLevel level, PerlerState.Project project) {
        MapId id = level.getFreeMapId();
        MapItemSavedData map = MapItemSavedData.createFresh(0, 0, (byte) 0,
                false, false, level.dimension()).locked();
        writeMap(map, project);
        level.setMapData(id, map);
        return id;
    }

    private void refreshFusedMaps() {
        for (PerlerState.Station station : state.stations.values()) {
            ServerLevel level = level(station.dimension);
            if (level == null) continue;
            for (PerlerState.Project project : station.projects.values()) {
                if (!project.fused || project.mapId < 0) continue;
                MapItemSavedData map = level.getMapData(new MapId(project.mapId));
                if (map != null) writeMap(map, project);
            }
        }
    }

    private void writeMap(MapItemSavedData map, PerlerState.Project project) {
        byte[] pixels = new byte[128 * 128];
        for (int y = 0; y < 128; y++) {
            int sourceY = y * project.size / 128;
            for (int x = 0; x < 128; x++) {
                int sourceX = x * project.size / 128;
                int color = project.colors[sourceY * project.size + sourceX];
                pixels[y * 128 + x] = color < 0 ? 0 : PerlerPalette.mapByte(color);
            }
        }
        map.colors = pixels;
        map.setDirty();
    }

    private void requireEditable(ServerPlayer player, PerlerState.Station station, PerlerState.Project project) {
        requirePlaced(station);
        if (!project.ownerId.equals(player.getUUID().toString()) && !PerlerMod.isAdmin(player))
            throw new IllegalArgumentException("只有工程创建者或管理员可以编辑。 ");
        if (project.fused) throw new IllegalArgumentException("工程已经烫豆锁定，不能继续修改。 ");
    }

    private void requirePlaced(PerlerState.Station station) {
        if (station == null || !station.placed) throw new IllegalArgumentException("拼豆台当前未放置。 ");
    }

    private boolean owns(ServerPlayer player, PerlerState.Station station) {
        return station.ownerId.equals(player.getUUID().toString()) || PerlerMod.isAdmin(player);
    }

    private String uniqueId(String prefix) {
        String id;
        do id = prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
        while (state.stations.containsKey(id) || state.findProject(id) != null);
        return id;
    }

    private ServerLevel level(String dimension) {
        for (ServerLevel level : server.getAllLevels())
            if (level.dimension().identifier().toString().equals(dimension)) return level;
        return null;
    }

    private void giveOrDrop(ServerPlayer player, ItemStack stack) {
        ItemStack copy = stack.copy();
        if (!player.getInventory().add(copy) || !copy.isEmpty()) player.drop(copy, false);
        InventoryOps.changed(player);
    }

    synchronized void save() throws Exception {
        state.save(statePath);
    }
}
