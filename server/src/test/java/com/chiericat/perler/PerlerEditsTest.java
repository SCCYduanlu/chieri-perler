package com.chiericat.perler;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class PerlerEditsTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.start();
    }

    @Test
    void paintsReplacesAndErasesAsOneAtomicPlan() {
        int red = PerlerPalette.representative(14);
        int blue = PerlerPalette.representative(11);
        int[] colors = {-1, red, blue};
        int[] reservoirs = new int[16];
        reservoirs[14] = 2;
        reservoirs[11] = 2;

        var result = PerlerEdits.apply(colors, reservoirs,
                new int[]{0, 1, 2}, new int[]{red, blue, -1});

        assertArrayEquals(new int[]{red, blue, -1}, result.colors());
        assertEquals(2, result.reservoirs()[14]);
        assertEquals(2, result.reservoirs()[11]);
        assertArrayEquals(new int[]{-1, red, blue}, colors);
    }

    @Test
    void insufficientStockRejectsTheWholePlanWithoutMutatingInputs() {
        int red = PerlerPalette.representative(14);
        int[] colors = {-1, -1};
        int[] reservoirs = new int[16];
        int[] original = colors.clone();
        int[] stock = reservoirs.clone();

        assertThrows(IllegalArgumentException.class, () -> PerlerEdits.apply(colors, reservoirs,
                new int[]{0, 1}, new int[]{red, red}));

        assertArrayEquals(original, colors);
        assertArrayEquals(stock, reservoirs);
    }
}
