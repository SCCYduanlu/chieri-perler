package com.chiericat.perler.client.mixin;

import com.chiericat.perler.client.PerlerCharmItemLayer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelResolver.class)
abstract class ItemModelResolverMixin {
    @Inject(method = "updateForTopItem", at = @At("TAIL"))
    private void chieriPerler$appendCharm(ItemStackRenderState state, ItemStack stack,
                                        ItemDisplayContext context, Level level,
                                        ItemOwner owner, int seed, CallbackInfo ci) {
        PerlerCharmItemLayer.append(state, stack, context);
    }
}
