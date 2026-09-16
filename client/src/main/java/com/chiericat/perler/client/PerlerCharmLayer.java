package com.chiericat.perler.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

final class PerlerCharmLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    PerlerCharmLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light,
                       AvatarRenderState state, float yRot, float xRot) {
        renderArm(state, state.rightHandItemStack, HumanoidArm.RIGHT, pose, collector, light);
        renderArm(state, state.leftHandItemStack, HumanoidArm.LEFT, pose, collector, light);
    }

    private void renderArm(AvatarRenderState state, ItemStack stack, HumanoidArm arm,
                           PoseStack pose, SubmitNodeCollector collector, int light) {
        Matrix4f basePose = new Matrix4f(pose.last().pose());
        pose.pushPose();
        getParentModel().translateToHand(state, arm, pose);
        PerlerCharmRenderer.renderThirdPerson(stack, arm, state.ageInTicks, state.attackTime,
                pose, basePose, collector, light);
        pose.popPose();
    }
}
