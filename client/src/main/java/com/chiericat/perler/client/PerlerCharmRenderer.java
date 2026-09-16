package com.chiericat.perler.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class PerlerCharmRenderer {
    private static final int TEXTURE_SIZE = 128;
    private static final int ART_LEFT = 12;
    private static final int ART_TOP = 22;
    private static final int ART_SIZE = 104;
    private static final Map<String, Identifier> TEXTURES = new HashMap<>();

    private PerlerCharmRenderer() {}

    public static void renderFirstPerson(ItemStack stack, HumanoidArm arm, float age, float attack,
                                         PoseStack pose, Matrix4fc basePose,
                                         SubmitNodeCollector collector, int light) {
        ClientCharmData charm = ClientCharmData.read(stack).orElse(null);
        if (charm == null) return;
        float side = arm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
        PoseStack facing = facingViewer(pose, basePose, side * 0.015f, -0.065f, -0.015f);
        facing.mulPose(Axis.ZP.rotationDegrees(sway(age, attack) * side));
        submit(charm, facing, collector, light, 0.075f);
    }

    public static void renderThirdPerson(ItemStack stack, HumanoidArm arm, float age, float attack,
                                         PoseStack pose, Matrix4fc basePose,
                                         SubmitNodeCollector collector, int light) {
        ClientCharmData charm = ClientCharmData.read(stack).orElse(null);
        if (charm == null) return;
        float side = arm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
        PoseStack facing = facingViewer(pose, basePose, side * 0.035f, -0.14f, -0.01f);
        facing.mulPose(Axis.ZP.rotationDegrees(sway(age, attack) * side));
        submit(charm, facing, collector, light, 0.14f);
    }

    private static PoseStack facingViewer(PoseStack attached, Matrix4fc basePose,
                                          float x, float y, float z) {
        Matrix4f relative = new Matrix4f(basePose).invert().mul(attached.last().pose());
        PoseStack facing = new PoseStack();
        facing.mulPose(basePose);
        facing.translate(relative.m30() + x, relative.m31() + y, relative.m32() + z);
        return facing;
    }

    static float sway(float age, float attack) {
        return (float) Math.sin(age * 0.17f) * 4.0f + attack * 13.0f;
    }

    private static void submit(ClientCharmData charm, PoseStack pose, SubmitNodeCollector collector,
                               int light, float size) {
        MapRenderState state = renderState(charm);
        if (state == null || state.texture == null) return;
        pose.mulPose(Axis.YP.rotationDegrees(180.0f));
        pose.mulPose(Axis.ZP.rotationDegrees(180.0f));
        pose.scale(size, size, size);
        pose.translate(-0.5f, -0.5f, 0.0f);
        pose.scale(1.0f / 128.0f, 1.0f / 128.0f, 1.0f / 128.0f);
        Minecraft.getInstance().getMapRenderer().render(state, pose, collector, true, light);
    }

    private static MapRenderState renderState(ClientCharmData charm) {
        MapRenderState state = new MapRenderState();
        if (charm.hasPixels()) {
            state.texture = texture(charm);
            return state;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || charm.mapId() < 0) return null;
        MapId id = new MapId(charm.mapId());
        MapItemSavedData data = MapItem.getSavedData(id, minecraft.level);
        if (data == null) return null;
        minecraft.getMapRenderer().extractRenderState(id, data, state);
        state.decorations.clear();
        return state;
    }

    static Identifier texture(ClientCharmData charm) {
        int hash = 31 * Arrays.hashCode(charm.colors()) + Arrays.hashCode(charm.filled());
        String key = charm.size() + "_" + Integer.toUnsignedString(hash, 16);
        Identifier cached = TEXTURES.get(key);
        if (cached != null) return cached;
        Identifier id = Identifier.fromNamespaceAndPath("chieri_perler_client", "charm/" + key);
        NativeImage image = buildTexture(charm);
        DynamicTexture texture = new DynamicTexture(() -> "Chieri Perler charm " + key, image);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        TEXTURES.put(key, id);
        return id;
    }

    private static NativeImage buildTexture(ClientCharmData charm) {
        NativeImage image = new NativeImage(TEXTURE_SIZE, TEXTURE_SIZE, true);
        drawLoop(image);
        if (charm.size() <= 32) drawBeads(image, charm);
        else drawDenseArt(image, charm);
        return image;
    }

    private static void drawLoop(NativeImage image) {
        for (int y = 2; y < 28; y++) {
            for (int x = 51; x < 77; x++) {
                double dx = x + 0.5 - 64.0;
                double dy = y + 0.5 - 14.0;
                double distance = Math.sqrt(dx * dx + dy * dy);
                if (distance <= 11.0 && distance >= 6.0) {
                    image.setPixel(x, y, distance > 9.0 ? 0xFF2A2130 : 0xFFFFC857);
                }
            }
        }
        for (int y = 20; y < ART_TOP + 5; y++) {
            for (int x = 60; x < 68; x++) image.setPixel(x, y, 0xFFFFC857);
        }
    }

    private static void drawBeads(NativeImage image, ClientCharmData charm) {
        int sourceSize = charm.size();
        for (int sy = 0; sy < sourceSize; sy++) {
            int top = ART_TOP + sy * ART_SIZE / sourceSize;
            int bottom = ART_TOP + (sy + 1) * ART_SIZE / sourceSize;
            for (int sx = 0; sx < sourceSize; sx++) {
                int pixel = sy * sourceSize + sx;
                if (!charm.isFilled(pixel)) continue;
                int left = ART_LEFT + sx * ART_SIZE / sourceSize;
                int right = ART_LEFT + (sx + 1) * ART_SIZE / sourceSize;
                int rgb = ClientPalette.argb(charm.color(pixel));
                drawBead(image, left, top, right, bottom, rgb);
            }
        }
    }

    private static void drawBead(NativeImage image, int left, int top, int right, int bottom, int color) {
        float cx = (left + right - 1) * 0.5f;
        float cy = (top + bottom - 1) * 0.5f;
        float rx = Math.max(1.0f, (right - left) * 0.5f);
        float ry = Math.max(1.0f, (bottom - top) * 0.5f);
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                float dx = (x - cx) / rx;
                float dy = (y - cy) / ry;
                float radius = dx * dx + dy * dy;
                // Ironed beads fuse into an opaque sheet. Shade the rounded edge
                // instead of cutting transparent corners that reveal the weapon.
                // Unoccupied project cells are still skipped by drawBeads.
                if (radius < 0.075f && right - left >= 5) image.setPixel(x, y, 0xFF3A3340);
                else if (radius > 0.72f) image.setPixel(x, y, darken(color));
                else if (dx < -0.22f && dy < -0.22f) image.setPixel(x, y, lighten(color));
                else image.setPixel(x, y, color);
            }
        }
    }

    private static void drawDenseArt(NativeImage image, ClientCharmData charm) {
        for (int y = 0; y < ART_SIZE; y++) {
            int sy = y * charm.size() / ART_SIZE;
            for (int x = 0; x < ART_SIZE; x++) {
                int sx = x * charm.size() / ART_SIZE;
                int pixel = sy * charm.size() + sx;
                if (charm.isFilled(pixel)) {
                    image.setPixel(ART_LEFT + x, ART_TOP + y,
                            ClientPalette.argb(charm.color(pixel)));
                }
            }
        }
    }

    private static int darken(int argb) {
        int r = ((argb >> 16) & 0xFF) * 3 / 4;
        int g = ((argb >> 8) & 0xFF) * 3 / 4;
        int b = (argb & 0xFF) * 3 / 4;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int lighten(int argb) {
        int r = Math.min(255, ((argb >> 16) & 0xFF) + 34);
        int g = Math.min(255, ((argb >> 8) & 0xFF) + 34);
        int b = Math.min(255, (argb & 0xFF) + 34);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
