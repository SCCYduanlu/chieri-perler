package com.chiericat.perler.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;

public final class PerlerClient implements ClientModInitializer {
    private static ClientPackets.StationPayload lastStation;
    private static PerlerStudioScreen active;

    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.clientboundPlay().registerLarge(ClientPackets.StationPayload.TYPE,
                ClientPackets.StationPayload.CODEC, 256 * 1024);
        PayloadTypeRegistry.clientboundPlay().registerLarge(ClientPackets.ProjectPayload.TYPE,
                ClientPackets.ProjectPayload.CODEC, 512 * 1024);
        PayloadTypeRegistry.clientboundPlay().register(ClientPackets.StatusPayload.TYPE,
                ClientPackets.StatusPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().registerLarge(ClientPackets.ActionPayload.TYPE,
                ClientPackets.ActionPayload.CODEC, 256 * 1024);

        ClientPlayNetworking.registerGlobalReceiver(ClientPackets.StationPayload.TYPE, (payload, context) ->
                context.client().execute(() -> receiveStation(payload)));
        ClientPlayNetworking.registerGlobalReceiver(ClientPackets.ProjectPayload.TYPE, (payload, context) ->
                context.client().execute(() -> receiveProject(payload)));
        ClientPlayNetworking.registerGlobalReceiver(ClientPackets.StatusPayload.TYPE, (payload, context) ->
                context.client().execute(() -> receiveStatus(payload)));
    }

    static void send(int action, String stationId, String projectId, int value, long revision,
                     int[] pixels, int[] colors) {
        ClientPlayNetworking.send(new ClientPackets.ActionPayload(ClientPackets.PROTOCOL, stationId,
                action, projectId == null ? "" : projectId, value, revision,
                pixels == null ? new int[0] : pixels, colors == null ? new int[0] : colors));
    }

    static void clear(PerlerStudioScreen screen) {
        if (active == screen) active = null;
    }

    private static void receiveStation(ClientPackets.StationPayload payload) {
        if (payload.protocol() != ClientPackets.PROTOCOL) return;
        lastStation = payload;
        if (active != null && active.stationId().equals(payload.stationId())) {
            active.receiveStation(payload);
            return;
        }
        active = new PerlerStudioScreen(payload);
        Minecraft.getInstance().setScreenAndShow(active);
    }

    private static void receiveProject(ClientPackets.ProjectPayload payload) {
        if (payload.protocol() != ClientPackets.PROTOCOL) return;
        if (lastStation == null || !lastStation.stationId().equals(payload.stationId())) return;
        if (active == null || !active.stationId().equals(payload.stationId())) {
            active = new PerlerStudioScreen(lastStation);
            Minecraft.getInstance().setScreenAndShow(active);
        }
        active.receiveProject(payload);
    }

    private static void receiveStatus(ClientPackets.StatusPayload payload) {
        if (payload.protocol() == ClientPackets.PROTOCOL && active != null
                && active.stationId().equals(payload.stationId())) active.receiveStatus(payload);
    }
}
