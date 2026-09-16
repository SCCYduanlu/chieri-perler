package com.chiericat.perler;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

final class PerlerPackets {
    static final int PROTOCOL = 3;
    static final int REQUEST_STATION = 0;
    static final int CREATE_PROJECT = 1;
    static final int REFILL_FAMILY = 2;
    static final int OPEN_PROJECT = 3;
    static final int PAINT_BATCH = 4;
    static final int FUSE_PROJECT = 5;
    static final int REPRINT_PROJECT = 6;
    static final int ATTACH_PROJECT = 7;

    private PerlerPackets() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("chieri_perler", path);
    }

    record ProjectSummary(String id, String name, String ownerName, int size, int filled,
                          boolean fused, long updatedAt) {
        void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(id, 32);
            buffer.writeUtf(name, 64);
            buffer.writeUtf(ownerName, 32);
            buffer.writeVarInt(size);
            buffer.writeVarInt(filled);
            buffer.writeBoolean(fused);
            buffer.writeLong(updatedAt);
        }

        static ProjectSummary read(RegistryFriendlyByteBuf buffer) {
            return new ProjectSummary(buffer.readUtf(32), buffer.readUtf(64), buffer.readUtf(32),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(), buffer.readLong());
        }
    }

    record StationPayload(int protocol, String stationId, String ownerName, int[] reservoirs,
                          int maxProjects, int beadsPerBatch, int[] boardSizes,
                          List<ProjectSummary> projects) implements CustomPacketPayload {
        static final Type<StationPayload> TYPE = new Type<>(id("station"));
        static final StreamCodec<RegistryFriendlyByteBuf, StationPayload> CODEC = StreamCodec.of(
                (buffer, value) -> value.write(buffer), StationPayload::read);

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(protocol);
            buffer.writeUtf(stationId, 32);
            buffer.writeUtf(ownerName, 32);
            writeInts(buffer, reservoirs, 16);
            buffer.writeVarInt(maxProjects);
            buffer.writeVarInt(beadsPerBatch);
            writeInts(buffer, boardSizes, 16);
            buffer.writeVarInt(projects.size());
            for (ProjectSummary project : projects) project.write(buffer);
        }

        private static StationPayload read(RegistryFriendlyByteBuf buffer) {
            int protocol = buffer.readVarInt();
            String stationId = buffer.readUtf(32);
            String ownerName = buffer.readUtf(32);
            int[] reservoirs = readInts(buffer, 16);
            int maxProjects = buffer.readVarInt();
            int beadsPerBatch = buffer.readVarInt();
            int[] boardSizes = readInts(buffer, 16);
            int count = bounded(buffer.readVarInt(), 0, 64, "工程数量");
            List<ProjectSummary> projects = new ArrayList<>(count);
            for (int i = 0; i < count; i++) projects.add(ProjectSummary.read(buffer));
            return new StationPayload(protocol, stationId, ownerName, reservoirs, maxProjects,
                    beadsPerBatch, boardSizes, List.copyOf(projects));
        }

        @Override public Type<StationPayload> type() { return TYPE; }
    }

    record ProjectPayload(int protocol, String stationId, String projectId, String name,
                          String ownerName, int size, boolean fused, long updatedAt,
                          int[] reservoirs, int[] colors) implements CustomPacketPayload {
        static final Type<ProjectPayload> TYPE = new Type<>(id("project"));
        static final StreamCodec<RegistryFriendlyByteBuf, ProjectPayload> CODEC = StreamCodec.of(
                (buffer, value) -> value.write(buffer), ProjectPayload::read);

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(protocol);
            buffer.writeUtf(stationId, 32);
            buffer.writeUtf(projectId, 32);
            buffer.writeUtf(name, 64);
            buffer.writeUtf(ownerName, 32);
            buffer.writeVarInt(size);
            buffer.writeBoolean(fused);
            buffer.writeLong(updatedAt);
            writeInts(buffer, reservoirs, 16);
            buffer.writeVarInt(colors.length);
            for (int color : colors) buffer.writeShort(color);
        }

        private static ProjectPayload read(RegistryFriendlyByteBuf buffer) {
            int protocol = buffer.readVarInt();
            String stationId = buffer.readUtf(32);
            String projectId = buffer.readUtf(32);
            String name = buffer.readUtf(64);
            String ownerName = buffer.readUtf(32);
            int size = bounded(buffer.readVarInt(), 1, 128, "画板尺寸");
            boolean fused = buffer.readBoolean();
            long updatedAt = buffer.readLong();
            int[] reservoirs = readInts(buffer, 16);
            int expected = Math.multiplyExact(size, size);
            int count = bounded(buffer.readVarInt(), expected, expected, "像素数量");
            int[] colors = new int[count];
            for (int i = 0; i < count; i++) colors[i] = buffer.readShort();
            return new ProjectPayload(protocol, stationId, projectId, name, ownerName, size, fused,
                    updatedAt, reservoirs, colors);
        }

        @Override public Type<ProjectPayload> type() { return TYPE; }
    }

    record ActionPayload(int protocol, String stationId, int action, String projectId, int value,
                         long expectedRevision, int[] pixels, int[] colors) implements CustomPacketPayload {
        static final Type<ActionPayload> TYPE = new Type<>(id("action"));
        static final StreamCodec<RegistryFriendlyByteBuf, ActionPayload> CODEC = StreamCodec.of(
                (buffer, payload) -> payload.write(buffer), ActionPayload::read);

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(protocol);
            buffer.writeUtf(stationId, 32);
            buffer.writeVarInt(action);
            buffer.writeUtf(projectId == null ? "" : projectId, 32);
            buffer.writeVarInt(value);
            buffer.writeLong(expectedRevision);
            int count = pixels == null ? 0 : pixels.length;
            buffer.writeVarInt(count);
            for (int i = 0; i < count; i++) {
                buffer.writeVarInt(pixels[i]);
                buffer.writeShort(colors[i]);
            }
        }

        private static ActionPayload read(RegistryFriendlyByteBuf buffer) {
            int protocol = buffer.readVarInt();
            String stationId = buffer.readUtf(32);
            int action = buffer.readVarInt();
            String projectId = buffer.readUtf(32);
            int value = buffer.readVarInt();
            long revision = buffer.readLong();
            int count = bounded(buffer.readVarInt(), 0, 4096, "绘制数量");
            int[] pixels = new int[count];
            int[] colors = new int[count];
            for (int i = 0; i < count; i++) {
                pixels[i] = buffer.readVarInt();
                colors[i] = buffer.readShort();
            }
            return new ActionPayload(protocol, stationId, action, projectId, value, revision, pixels, colors);
        }

        @Override public Type<ActionPayload> type() { return TYPE; }
    }

    record StatusPayload(int protocol, String stationId, String projectId, boolean error,
                         String message, long revision, int filled, int[] reservoirs)
            implements CustomPacketPayload {
        static final Type<StatusPayload> TYPE = new Type<>(id("status"));
        static final StreamCodec<RegistryFriendlyByteBuf, StatusPayload> CODEC = StreamCodec.of(
                (buffer, value) -> value.write(buffer), StatusPayload::read);

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(protocol);
            buffer.writeUtf(stationId, 32);
            buffer.writeUtf(projectId == null ? "" : projectId, 32);
            buffer.writeBoolean(error);
            buffer.writeUtf(message == null ? "" : message, 256);
            buffer.writeLong(revision);
            buffer.writeVarInt(filled);
            writeInts(buffer, reservoirs, 16);
        }

        private static StatusPayload read(RegistryFriendlyByteBuf buffer) {
            return new StatusPayload(buffer.readVarInt(), buffer.readUtf(32), buffer.readUtf(32),
                    buffer.readBoolean(), buffer.readUtf(256), buffer.readLong(), buffer.readVarInt(),
                    readInts(buffer, 16));
        }

        @Override public Type<StatusPayload> type() { return TYPE; }
    }

    private static void writeInts(RegistryFriendlyByteBuf buffer, int[] values, int maximum) {
        if (values.length > maximum) throw new IllegalArgumentException("数组过长。 ");
        buffer.writeVarInt(values.length);
        for (int value : values) buffer.writeVarInt(value);
    }

    private static int[] readInts(RegistryFriendlyByteBuf buffer, int maximum) {
        int count = bounded(buffer.readVarInt(), 0, maximum, "数组长度");
        int[] values = new int[count];
        for (int i = 0; i < count; i++) values[i] = buffer.readVarInt();
        return values;
    }

    private static int bounded(int value, int minimum, int maximum, String name) {
        if (value < minimum || value > maximum) throw new IllegalArgumentException(name + "无效。 ");
        return value;
    }
}
