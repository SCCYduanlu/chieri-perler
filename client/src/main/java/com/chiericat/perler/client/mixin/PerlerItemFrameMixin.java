package com.chiericat.perler.client.mixin;

import com.chiericat.perler.client.PerlerArtRenderer;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemFrameRenderer.class)
abstract class PerlerItemFrameMixin<T extends ItemFrame> {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/decoration/ItemFrame;Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;F)V",
            at = @At("TAIL"))
    private void chieriPerler$replaceMapTexture(T frame, ItemFrameRenderState state,
                                                float partialTick, CallbackInfo ci) {
        PerlerArtRenderer.apply(frame.getItem(), state.mapRenderState);
    }
}
