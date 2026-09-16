package com.chiericat.perler.client.mixin;

import com.chiericat.perler.client.PerlerCharmRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
abstract class ItemInHandRendererMixin {
    @Unique
    private Matrix4f chieriPerler$basePose;

    @Inject(method = "submitArmWithItem", at = @At("HEAD"))
    private void chieriPerler$captureBasePose(AbstractClientPlayer player, float partialTick, float pitch,
                                               InteractionHand hand, float swingProgress, ItemStack stack,
                                               float equipProgress, PoseStack pose, SubmitNodeCollector collector,
                                               int light, CallbackInfo ci) {
        chieriPerler$basePose = new Matrix4f(pose.last().pose());
    }

    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V", shift = At.Shift.BEFORE))
    private void chieriPerler$renderCharm(AbstractClientPlayer player, float partialTick, float pitch,
                                          InteractionHand hand, float swingProgress, ItemStack stack,
                                          float equipProgress, PoseStack pose, SubmitNodeCollector collector,
                                          int light, CallbackInfo ci) {
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND
                ? player.getMainArm() : player.getMainArm().getOpposite();
        PerlerCharmRenderer.renderFirstPerson(stack, arm, player.tickCount + partialTick,
                swingProgress, pose, chieriPerler$basePose, collector, light);
    }
}
