package com.chiericat.perler;

import io.github.flemmli97.flan.api.ClaimHandler;
import io.github.flemmli97.flan.api.permission.BuiltinPermission;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;

final class PerlerNetworking {
    private PerlerNetworking() {}

    static void register() {
        PayloadTypeRegistry.clientboundPlay().registerLarge(PerlerPackets.StationPayload.TYPE,
                PerlerPackets.StationPayload.CODEC, 256 * 1024);
        PayloadTypeRegistry.clientboundPlay().registerLarge(PerlerPackets.ProjectPayload.TYPE,
                PerlerPackets.ProjectPayload.CODEC, 512 * 1024);
        PayloadTypeRegistry.clientboundPlay().register(PerlerPackets.StatusPayload.TYPE,
                PerlerPackets.StatusPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().registerLarge(PerlerPackets.ActionPayload.TYPE,
                PerlerPackets.ActionPayload.CODEC, 256 * 1024);
        ServerPlayNetworking.registerGlobalReceiver(PerlerPackets.ActionPayload.TYPE, (payload, context) ->
                context.server().execute(() -> handle(context.player(), payload)));
    }

    static void openOrFallback(ServerPlayer player, PerlerState.Station station) {
        if (!ServerPlayNetworking.canSend(player, PerlerPackets.StationPayload.TYPE)) {
            PerlerGui.openMain(player, station);
            return;
        }
        sendStation(player, station);
    }

    private static void handle(ServerPlayer player, PerlerPackets.ActionPayload payload) {
        PerlerController controller = PerlerMod.controller;
        if (controller == null) return;
        PerlerState.Station station = controller.state.stations.get(payload.stationId());
        PerlerState.Project project = station == null ? null : station.projects.get(payload.projectId());
        try {
            if (payload.protocol() != PerlerPackets.PROTOCOL)
                throw new IllegalArgumentException("客户端拼豆协议版本不匹配，请更新客户端模组。 ");
            requireNearbyAccess(player, station);
            switch (payload.action()) {
                case PerlerPackets.REQUEST_STATION -> sendStation(player, station);
                case PerlerPackets.CREATE_PROJECT -> {
                    project = controller.createProject(player, station, payload.value());
                    sendStation(player, station);
                    sendProject(player, station, project);
                }
                case PerlerPackets.REFILL_FAMILY -> {
                    controller.refill(player, station, payload.value());
                    sendStation(player, station);
                }
                case PerlerPackets.OPEN_PROJECT -> {
                    if (project == null) throw new IllegalArgumentException("工程不存在。 ");
                    sendProject(player, station, project);
                }
                case PerlerPackets.PAINT_BATCH -> {
                    if (project == null) throw new IllegalArgumentException("工程不存在。 ");
                    controller.paintBatch(player, station, project, payload.expectedRevision(),
                            payload.pixels(), payload.colors());
                    sendStatus(player, station, project, false, "进度已保存");
                }
                case PerlerPackets.FUSE_PROJECT -> {
                    if (project == null) throw new IllegalArgumentException("工程不存在。 ");
                    controller.fuse(player, station, project);
                    sendProject(player, station, project);
                }
                case PerlerPackets.REPRINT_PROJECT -> {
                    if (project == null) throw new IllegalArgumentException("工程不存在。 ");
                    controller.reprint(player, project);
                    sendStatus(player, station, project, false, "成品副本已放入背包");
                }
                case PerlerPackets.ATTACH_PROJECT -> {
                    if (project == null) throw new IllegalArgumentException("工程不存在。 ");
                    controller.attachCharmFromTable(player, project);
                    sendStatus(player, station, project, false, "挂饰已安装到主手装备");
                }
                default -> throw new IllegalArgumentException("未知的拼豆操作。 ");
            }
        } catch (Exception exception) {
            sendStatus(player, station, project, true, PerlerMod.safe(exception));
            if (station != null && project != null && ServerPlayNetworking.canSend(player, PerlerPackets.ProjectPayload.TYPE))
                sendProject(player, station, project);
        }
    }

    private static void requireNearbyAccess(ServerPlayer player, PerlerState.Station station) {
        if (station == null || !station.placed) throw new IllegalArgumentException("拼豆台不存在或已经被打包。 ");
        if (!player.level().dimension().identifier().toString().equals(station.dimension))
            throw new IllegalArgumentException("你离拼豆台太远。 ");
        double dx = player.getX() - (station.x + 0.5);
        double dy = player.getY() - (station.y + 0.5);
        double dz = player.getZ() - (station.z + 0.5);
        if (dx * dx + dy * dy + dz * dz > 144.0) throw new IllegalArgumentException("你离拼豆台太远。 ");
        BlockPos pos = new BlockPos(station.x, station.y, station.z);
        if (!ClaimHandler.canInteract(player, pos, BuiltinPermission.OPENCONTAINER))
            throw new IllegalArgumentException("你没有该领地的容器交互权限。 ");
    }

    private static void sendStation(ServerPlayer player, PerlerState.Station station) {
        var projects = station.projects.values().stream()
                .sorted((a, b) -> Long.compare(b.updatedAt, a.updatedAt))
                .map(project -> new PerlerPackets.ProjectSummary(project.id, project.name, project.ownerName,
                        project.size, project.filled(), project.fused, project.updatedAt))
                .toList();
        ServerPlayNetworking.send(player, new PerlerPackets.StationPayload(PerlerPackets.PROTOCOL,
                station.id, station.ownerName, station.reservoirs.clone(),
                PerlerMod.controller.config.maxProjectsPerTable,
                PerlerMod.controller.config.beadsPerBatch,
                PerlerMod.controller.config.boardSizes.clone(), projects));
    }

    private static void sendProject(ServerPlayer player, PerlerState.Station station, PerlerState.Project project) {
        ServerPlayNetworking.send(player, new PerlerPackets.ProjectPayload(PerlerPackets.PROTOCOL,
                station.id, project.id, project.name, project.ownerName, project.size, project.fused,
                project.updatedAt, station.reservoirs.clone(), project.colors.clone()));
    }

    private static void sendStatus(ServerPlayer player, PerlerState.Station station,
                                   PerlerState.Project project, boolean error, String message) {
        String stationId = station == null ? "" : station.id;
        String projectId = project == null ? "" : project.id;
        long revision = project == null ? -1L : project.updatedAt;
        int filled = project == null ? 0 : project.filled();
        int[] reservoirs = station == null ? new int[16] : Arrays.copyOf(station.reservoirs, 16);
        ServerPlayNetworking.send(player, new PerlerPackets.StatusPayload(PerlerPackets.PROTOCOL,
                stationId, projectId, error, message, revision, filled, reservoirs));
    }
}
