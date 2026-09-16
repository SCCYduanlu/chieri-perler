package com.chiericat.perler.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StudioLayoutTest {
    @Test
    void compactLayoutFitsTheReported478By270Gui() {
        StudioLayout layout = StudioLayout.of(478, 270);

        assertTrue(layout.compact());
        assertEquals(96, layout.leftWidth());
        assertTrue(layout.leftX() + layout.leftWidth() < layout.boardX());
        assertTrue(layout.boardX() + layout.boardWidth() < layout.paletteX());
        assertTrue(layout.paletteX() + layout.paletteWidth() <= 478);
        assertTrue(layout.top() + layout.panelHeight() <= 270);
        assertTrue(layout.boardWidth() >= 200);
        assertPaletteGridFits(layout);
    }

    @Test
    void wideLayoutKeepsThreeIndependentColumns() {
        StudioLayout layout = StudioLayout.of(1920, 1080);

        assertTrue(!layout.compact());
        assertTrue(layout.leftX() + layout.leftWidth() < layout.boardX());
        assertTrue(layout.boardX() + layout.boardWidth() < layout.paletteX());
        assertEquals(234, layout.paletteWidth());
        assertPaletteGridFits(layout);
    }

    private static void assertPaletteGridFits(StudioLayout layout) {
        int used = layout.paletteColumns() * layout.paletteSwatch()
                + (layout.paletteColumns() - 1) * layout.paletteGap();
        assertTrue(used <= layout.paletteWidth() - layout.palettePadding() * 2);
        assertTrue(layout.paletteRowsVisible() > 0);
    }
}
