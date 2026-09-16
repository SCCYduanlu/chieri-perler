package com.chiericat.perler.client;

import com.mojang.blaze3d.platform.NativeImage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class PerlerCharmTextureTest {
    private static NativeImage render(int size, byte[] filled) throws Exception {
        byte[] colors = new byte[size * size * 2];
        int white = 0;
        while (!"T1".equals(ClientPalette.code(white))) white++;
        for (int pixel = 0; pixel < size * size; pixel++) {
            colors[pixel * 2] = (byte) (white >>> 8);
            colors[pixel * 2 + 1] = (byte) white;
        }
        ClientCharmData data = new ClientCharmData("texture-test", 0, size, colors, filled);
        Method build = PerlerCharmRenderer.class.getDeclaredMethod("buildTexture", ClientCharmData.class);
        build.setAccessible(true);
        return (NativeImage) build.invoke(null, data);
    }

    @Test
    void fusedBeadCellsNeverExposeTheWeaponBehindThem() throws Exception {
        for (int size : new int[]{16, 32, 64, 128}) {
            byte[] filled = new byte[(size * size + 7) / 8];
            Arrays.fill(filled, (byte) 0xFF);
            try (NativeImage image = render(size, filled)) {
                for (int y = 22; y < 126; y++) {
                    for (int x = 12; x < 116; x++) {
                        assertEquals(255, image.getPixel(x, y) >>> 24,
                                "Transparent seam: size=" + size + ", x=" + x + ", y=" + y);
                    }
                }
            }
        }
    }

    @Test
    void onlyOccupiedCellsAreFilledAndIntentionalEmptySpacesRemainTransparent() throws Exception {
        int size = 32;
        byte[] filled = new byte[size * size / 8];
        for (int y = 10; y <= 20; y++) {
            for (int x = 10; x <= 20; x++) {
                if (x == 15 && y == 15) continue;
                int index = y * size + x;
                filled[index >> 3] |= (byte) (1 << (index & 7));
            }
        }
        try (NativeImage image = render(size, filled)) {
            for (int y = 4; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int index = y * size + x;
                    int expectedAlpha = (filled[index >> 3] & (1 << (index & 7))) == 0 ? 0 : 255;
                    for (int py = 22 + y * 104 / size; py < 22 + (y + 1) * 104 / size; py++) {
                        for (int px = 12 + x * 104 / size; px < 12 + (x + 1) * 104 / size; px++) {
                            assertEquals(expectedAlpha, image.getPixel(px, py) >>> 24);
                        }
                    }
                }
            }
            assertEquals(0, image.getPixel(0, 0));
            assertEquals(0, image.getPixel(127, 127));
        }
    }

    @Test
    void filledWhiteBeadsKeepOpaqueReliefShadingWithoutBlueArtifacts() throws Exception {
        byte[] filled = new byte[16 * 16 / 8];
        Arrays.fill(filled, (byte) 0xFF);
        Set<Integer> shades = new HashSet<>();
        try (NativeImage image = render(16, filled)) {
            for (int y = 22; y < 126; y++) {
                for (int x = 12; x < 116; x++) {
                    int color = image.getPixel(x, y);
                    assertEquals(255, color >>> 24);
                    shades.add(color);
                }
            }
        }
        assertTrue(shades.size() >= 3, "Keep highlights, bead colour and shadows");
        assertTrue(shades.stream().allMatch(color -> color == 0xFF3A3340
                || Math.abs(((color >> 16) & 255) - (color & 255)) <= 8));
    }
}
