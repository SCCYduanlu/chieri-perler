package com.chiericat.perler.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;

final class PerlerCharmHud {
    private PerlerCharmHud() {}

    static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !minecraft.options.getCameraType().isFirstPerson()) return;
        ClientCharmData charm = ClientCharmData.read(minecraft.player.getMainHandItem()).orElse(null);
        if (charm == null || !charm.hasPixels()) return;

        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
        float attack = minecraft.player.getAttackAnim(partialTick);
        float side = minecraft.player.getMainArm() == HumanoidArm.RIGHT ? 1.0f : -1.0f;
        // Relative screen placement remains consistent at different GUI scales.
        int size = Math.max(12, Math.round(graphics.guiHeight() * 0.10f));
        float centerX = graphics.guiWidth() * (side > 0 ? 0.895f : 0.105f);
        float centerY = graphics.guiHeight() * 0.76f + attack * graphics.guiHeight() * 0.035f;
        float angle = (float) Math.toRadians(
                PerlerCharmRenderer.sway(minecraft.player.tickCount + partialTick, attack) * side * 0.55f);
        Identifier texture = PerlerCharmRenderer.texture(charm);

        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX + side * attack * size * 0.25f, centerY);
        graphics.pose().rotate(angle);
        // This overload takes x0, y0, x1, y1 and u0, u1, v0, v1 (not UV pairs).
        graphics.blit(texture, -size / 2, -size / 2, size / 2, size / 2,
                0.0f, 1.0f, 0.0f, 1.0f);
        graphics.pose().popMatrix();
    }
}
