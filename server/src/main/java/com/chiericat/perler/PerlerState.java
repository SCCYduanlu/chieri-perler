package com.chiericat.perler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

final class PerlerState {
    static final int SCHEMA = 3;
    int schema = SCHEMA;
    Map<String, Station> stations = new LinkedHashMap<>();

    static PerlerState load(Path path, PerlerConfig config) throws IOException {
        if (!Files.isRegularFile(path)) return new PerlerState();
        PerlerState state = new Gson().fromJson(Files.readString(path, StandardCharsets.UTF_8), PerlerState.class);
        if (state == null) throw new IOException("拼豆状态文件为空。 ");
        state.normalize(config);
        return state;
    }

    void normalize(PerlerConfig config) throws IOException {
        boolean migrateLegacyPalette = schema == 1;
        boolean migrate216Palette = schema == 2;
        if (!migrateLegacyPalette && !migrate216Palette && schema != SCHEMA)
            throw new IOException("不支持的拼豆数据版本：" + schema);
        if (stations == null) stations = new LinkedHashMap<>();
        for (Map.Entry<String, Station> entry : stations.entrySet()) {
            Station station = entry.getValue();
            if (station == null || !entry.getKey().equals(station.id) || station.ownerId == null)
                throw new IOException("拼豆台记录无效：" + entry.getKey());
            if (station.reservoirs == null) station.reservoirs = new int[PerlerPalette.FAMILIES];
            if (station.reservoirs.length != PerlerPalette.FAMILIES)
                station.reservoirs = Arrays.copyOf(station.reservoirs, PerlerPalette.FAMILIES);
            for (int value : station.reservoirs) {
                if (value < 0 || value > config.maxReservoirPerFamily)
                    throw new IOException("拼豆台耗材数量异常：" + station.id);
            }
            if (station.projects == null) station.projects = new LinkedHashMap<>();
            if (station.projects.size() > config.maxProjectsPerTable)
                throw new IOException("拼豆台工程数量超过配置上限：" + station.id);
            for (Project project : station.projects.values()) {
                if (migrateLegacyPalette) project.migrateLegacyPalette();
                else if (migrate216Palette) project.migrate216Palette();
                project.normalize(config);
            }
        }
        schema = SCHEMA;
    }

    void save(Path path) throws IOException {
        byte[] bytes = new GsonBuilder().setPrettyPrinting().create().toJson(this).getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(path.getParent());
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.write(temp, bytes);
        try {
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    Station at(String dimension, int x, int y, int z) {
        for (Station station : stations.values()) {
            if (station.placed && station.x == x && station.y == y && station.z == z
                    && dimension.equals(station.dimension)) return station;
        }
        return null;
    }

    Project findProject(String projectId) {
        for (Station station : stations.values()) {
            Project project = station.projects.get(projectId);
            if (project != null) return project;
        }
        return null;
    }

    static final class Station {
        String id;
        String ownerId;
        String ownerName;
        boolean placed;
        String dimension = "";
        int x;
        int y;
        int z;
        long createdAt;
        long updatedAt;
        int[] reservoirs = new int[PerlerPalette.FAMILIES];
        Map<String, Project> projects = new LinkedHashMap<>();
    }

    static final class Project {
        String id;
        String name;
        String ownerId;
        String ownerName;
        int size;
        int[] colors;
        boolean fused;
        int mapId = -1;
        long createdAt;
        long updatedAt;
        long fusedAt;

        void migrateLegacyPalette() throws IOException {
            if (colors == null) return;
            for (int pixel = 0; pixel < colors.length; pixel++) {
                int color = colors[pixel];
                if (color < -1 || color >= 256)
                    throw new IOException("旧版拼豆工程颜色索引无效：" + id);
                colors[pixel] = PerlerPalette.migrateLegacy(color);
            }
        }

        void migrate216Palette() throws IOException {
            if (colors == null) return;
            for (int pixel = 0; pixel < colors.length; pixel++) {
                int color = colors[pixel];
                if (color < -1 || color >= 216)
                    throw new IOException("216 色版拼豆工程颜色索引无效：" + id);
                colors[pixel] = PerlerPalette.migrate216(color);
            }
        }

        void normalize(PerlerConfig config) throws IOException {
            if (id == null || ownerId == null || name == null || !config.allowsSize(size))
                throw new IOException("拼豆工程元数据无效。 ");
            if (colors == null || colors.length != size * size)
                throw new IOException("拼豆工程画板长度无效：" + id);
            for (int color : colors) {
                if (color < -1 || color >= PerlerPalette.COLORS)
                    throw new IOException("拼豆工程颜色索引无效：" + id);
            }
            if (fused && mapId < 0) throw new IOException("已烫豆工程缺少地图 ID：" + id);
        }

        int filled() {
            int result = 0;
            for (int color : colors) if (color >= 0) result++;
            return result;
        }
    }
}
