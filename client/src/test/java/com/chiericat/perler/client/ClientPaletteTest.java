package com.chiericat.perler.client;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ClientPaletteTest {
    @Test
    void containsExactly291WorkbookCodes() {
        var colors = new HashSet<Integer>();
        for (int color = 0; color < ClientPalette.COLORS; color++) colors.add(ClientPalette.rgb(color));
        assertEquals(291, ClientPalette.COLORS);
        assertEquals(290, colors.size());
        assertEquals("A1", ClientPalette.code(0));
        assertEquals("#FAF4C8", ClientPalette.hex(0));
        assertEquals("ZG8", ClientPalette.code(290));
        assertEquals("#AB91C0", ClientPalette.hex(290));
    }

    @Test
    void searchesChineseEnglishHexRgbAndOneBasedNumber() {
        assertTrue(ClientPalette.search("白").contains(0));
        assertTrue(ClientPalette.search("white").contains(0));
        assertTrue(ClientPalette.search(ClientPalette.hex(0)).contains(0));
        int rgb = ClientPalette.rgb(0);
        String triplet = ((rgb >> 16) & 255) + "," + ((rgb >> 8) & 255) + "," + (rgb & 255);
        assertTrue(ClientPalette.search(triplet).contains(0));
        assertTrue(ClientPalette.search("291").contains(290));
        assertTrue(ClientPalette.search("荧光").contains(278));
    }
}
