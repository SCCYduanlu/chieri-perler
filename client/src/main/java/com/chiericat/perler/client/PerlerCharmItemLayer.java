package com.chiericat.perler.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.lang.reflect.Field;
import java.util.function.Consumer;

/** An extra item model layer sharing every transform with the equipped tool. */
public final class PerlerCharmItemLayer implements SpecialModelRenderer<ClientCharmData> {
    private static final PerlerCharmItemLayer INSTANCE = new PerlerCharmItemLayer();
    private static final Field LAYERS = field(ItemStackRenderState.class, "layers");
    private static final Field ITEM_TRANSFORM = field(ItemStackRenderState.LayerRenderState.class, "itemTransform");
    private static final Field LOCAL_TRANSFORM = field(ItemStackRenderState.LayerRenderState.class, "localTransform");

    private static Field field(Class<?> type, String name) {
        try {
            Field result = type.getDeclaredField(name);
            result.setAccessible(true);
            return result;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Minecraft 26.2 item model field unavailable: " + name, error);
        }
    }

    public static void append(ItemStackRenderState state, ItemStack stack, ItemDisplayContext context) {
        if (context != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                && context != ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                && context != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                && context != ItemDisplayContext.THIRD_PERSON_LEFT_HAND) return;
        if (state.isEmpty()) return;
        ClientCharmData charm = ClientCharmData.read(stack).orElse(null);
        if (charm == null || !charm.hasPixels()) return;
        try {
            ItemStackRenderState.LayerRenderState source =
                    ((ItemStackRenderState.LayerRenderState[]) LAYERS.get(state))[0];
            ItemTransform transform = (ItemTransform) ITEM_TRANSFORM.get(source);
            Matrix4fc local = (Matrix4fc) LOCAL_TRANSFORM.get(source);
            ItemStackRenderState.LayerRenderState layer = state.newLayer();
            layer.setItemTransform(transform);
            layer.setLocalTransform(local);
            layer.setupSpecialModel(INSTANCE, charm);
            layer.setUsesBlockLight(false);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Cannot attach perler layer to the tool model", error);
        }
    }

    @Override
    public void submit(ClientCharmData charm, PoseStack pose, SubmitNodeCollector collector,
                       int light, int overlay, boolean foil, int outlineColor) {
        if (charm == null || !charm.hasPixels()) return;
        Minecraft client = Minecraft.getInstance();
        float time = client.level == null ? 0 : client.level.getGameTime();
        float angle = (float) Math.sin(time * 0.12f) * 3.0f;
        // Item-space ring centre: the upper handle, directly below the pickaxe head.
        // Choose the camera-facing surface so the near side stays readable without
        // drawing a second ghost charm through the transparent parts of the texture.
        boolean back = new Matrix4f(pose.last().pose()).invert().m32() < 0.5f;
        drawFace(charm, pose, collector, light, back ? 0.461f : 0.539f,
                back, back ? -angle : angle);
    }

    private static void drawFace(ClientCharmData charm, PoseStack pose, SubmitNodeCollector collector,
                                 int light, float z, boolean back, float angle) {
        pose.pushPose();
        pose.translate(0.5625f, 0.5625f, z);
        pose.mulPose(Axis.ZP.rotationDegrees(angle));
        float size = 0.16f;
        float left = -0.5f * size;
        float right = 0.5f * size;
        float top = (14.0f / 128.0f) * size;
        float bottom = -(114.0f / 128.0f) * size;
        float uLeft = back ? 1 : 0;
        float uRight = back ? 0 : 1;
        collector.submitCustomGeometry(pose, RenderTypes.text(PerlerCharmRenderer.texture(charm)),
                (matrix, vertices) -> {
                    vertices.addVertex(matrix, left, top, 0).setColor(-1).setUv(uLeft, 0).setLight(light);
                    vertices.addVertex(matrix, left, bottom, 0).setColor(-1).setUv(uLeft, 1).setLight(light);
                    vertices.addVertex(matrix, right, bottom, 0).setColor(-1).setUv(uRight, 1).setLight(light);
                    vertices.addVertex(matrix, right, top, 0).setColor(-1).setUv(uRight, 0).setLight(light);
                });
        pose.popPose();
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        output.accept(new Vector3f(0.4f, 0.3f, 0.45f));
        output.accept(new Vector3f(0.73f, 0.62f, 0.55f));
    }

    @Override
    public ClientCharmData extractArgument(ItemStack stack) {
        return ClientCharmData.read(stack).orElse(null);
    }
}
