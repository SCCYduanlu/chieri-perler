package com.chiericat.perler.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PerlerCharmRendererTest {
    @Test
    void attackAddsAVisibleSwingWithoutBreakingIdleSway() {
        float idle = PerlerCharmRenderer.sway(0.0f, 0.0f);
        float attacking = PerlerCharmRenderer.sway(0.0f, 1.0f);
        assertEquals(0.0f, idle);
        assertEquals(13.0f, attacking);
        for (int tick = 0; tick < 200; tick++) {
            assertTrue(Math.abs(PerlerCharmRenderer.sway(tick, 0.0f)) <= 4.001f);
        }
    }
}
