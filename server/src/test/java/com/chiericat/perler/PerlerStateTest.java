package com.chiericat.perler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

final class PerlerStateTest {
    @TempDir Path temp;

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.start();
    }

    @Test
    void roundTripsPlacedStationReservoirsAndProjectPixels() throws Exception {
        PerlerConfig config = new PerlerConfig();
        config.validate();
        PerlerState state = new PerlerState();
        PerlerState.Station station = new PerlerState.Station();
        station.id = "TTEST";
        station.ownerId = "owner";
        station.ownerName = "Tester";
        station.placed = true;
        station.dimension = "minecraft:overworld";
        station.x = 10;
        station.y = 64;
        station.z = -8;
        station.reservoirs[4] = 127;
        PerlerState.Project project = new PerlerState.Project();
        project.id = "PTEST";
        project.name = "作品 1";
        project.ownerId = "owner";
        project.ownerName = "Tester";
        project.size = 16;
        project.colors = new int[256];
        Arrays.fill(project.colors, -1);
        project.colors[17] = PerlerPalette.representative(4);
        station.projects.put(project.id, project);
        state.stations.put(station.id, station);

        Path path = temp.resolve("state.json");
        state.save(path);
        PerlerState loaded = PerlerState.load(path, config);

        assertEquals(127, loaded.stations.get("TTEST").reservoirs[4]);
        assertEquals(PerlerPalette.representative(4), loaded.findProject("PTEST").colors[17]);
        assertNotNull(loaded.at("minecraft:overworld", 10, 64, -8));
        assertNull(loaded.at("minecraft:overworld", 11, 64, -8));
    }

    @Test
    void migratesLegacy256ColorProjectsToNearestStandardColor() throws Exception {
        PerlerConfig config = new PerlerConfig();
        config.validate();
        PerlerState state = new PerlerState();
        state.schema = 1;
        PerlerState.Station station = new PerlerState.Station();
        station.id = "TLEGACY";
        station.ownerId = "owner";
        PerlerState.Project project = new PerlerState.Project();
        project.id = "PLEGACY";
        project.name = "旧作品";
        project.ownerId = "owner";
        project.size = 16;
        project.colors = new int[256];
        Arrays.fill(project.colors, -1);
        project.colors[0] = 255;
        station.projects.put(project.id, project);
        state.stations.put(station.id, station);
        Path path = temp.resolve("legacy.json");
        state.save(path);

        PerlerState loaded = PerlerState.load(path, config);

        assertEquals(PerlerState.SCHEMA, loaded.schema);
        assertEquals(PerlerPalette.migrateLegacy(255), loaded.findProject("PLEGACY").colors[0]);
    }

    @Test
    void migratesVersion2PaletteWithoutReinterpretingIndices() throws Exception {
        PerlerConfig config = new PerlerConfig();
        config.validate();
        PerlerState state = new PerlerState();
        state.schema = 2;
        PerlerState.Station station = new PerlerState.Station();
        station.id = "T216";
        station.ownerId = "owner";
        PerlerState.Project project = new PerlerState.Project();
        project.id = "P216";
        project.name = "216 色作品";
        project.ownerId = "owner";
        project.size = 16;
        project.colors = new int[256];
        Arrays.fill(project.colors, -1);
        project.colors[0] = 215;
        station.projects.put(project.id, project);
        state.stations.put(station.id, station);
        Path path = temp.resolve("palette216.json");
        state.save(path);

        PerlerState loaded = PerlerState.load(path, config);

        assertEquals(PerlerState.SCHEMA, loaded.schema);
        assertEquals(PerlerPalette.migrate216(215), loaded.findProject("P216").colors[0]);
    }
}
