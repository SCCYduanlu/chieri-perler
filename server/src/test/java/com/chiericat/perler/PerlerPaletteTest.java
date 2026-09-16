package com.chiericat.perler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PerlerPaletteTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.start();
    }

    @Test
    void exposesExactly291WorkbookColors() {
        Set<Integer> colors = new HashSet<>();
        for (int color = 0; color < PerlerPalette.COLORS; color++) colors.add(PerlerPalette.rgb(color));
        assertEquals(291, PerlerPalette.COLORS);
        assertEquals(290, colors.size());
        assertEquals("A1", PerlerPalette.code(0));
        assertEquals(0xFAF4C8, PerlerPalette.rgb(0));
        assertEquals("ZG8", PerlerPalette.code(290));
        assertEquals(0xAB91C0, PerlerPalette.rgb(290));
    }

    @Test
    void everyColorHasAConsumableFamilyAndVanillaFallback() {
        for (int color = 0; color < PerlerPalette.COLORS; color++) {
            int family = PerlerPalette.family(color);
            assertTrue(family >= 0 && family < PerlerPalette.FAMILIES);
            assertNotEquals(0, PerlerPalette.mapByte(color));
            assertTrue(PerlerPalette.label(color).contains(PerlerPalette.NAMES[family]));
        }
        for (int family = 0; family < PerlerPalette.FAMILIES; family++) {
            assertTrue(PerlerPalette.colorsForFamily(family).length > 0);
        }
    }

    @Test
    void preservesDistinctWorkbookCodesWithTheSameRgb() {
        int q4 = -1;
        int r11 = -1;
        for (int color = 0; color < PerlerPalette.COLORS; color++) {
            if ("Q4".equals(PerlerPalette.code(color))) q4 = color;
            if ("R11".equals(PerlerPalette.code(color))) r11 = color;
        }
        assertNotEquals(q4, r11);
        assertEquals(0xFFEBFA, PerlerPalette.rgb(q4));
        assertEquals(PerlerPalette.rgb(q4), PerlerPalette.rgb(r11));
    }
}
