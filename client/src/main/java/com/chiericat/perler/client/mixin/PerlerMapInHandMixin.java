package com.chiericat.perler.client.mixin;

import com.chiericat.perler.client.PerlerArtRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
abstract class PerlerMapInHandMixin {
    @Shadow @Final private MapRenderState mapRenderState;

    @Inject(method = "renderMap", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/MapRenderer;render(Lnet/minecraft/client/renderer/state/MapRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ZI)V"))
    private void chieriPerler$replaceMapTexture(PoseStack pose, SubmitNodeCollector collector,
                                                int light, ItemStack stack, CallbackInfo ci) {
        PerlerArtRenderer.apply(stack, mapRenderState);
    }
}
