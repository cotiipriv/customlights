package com.cotii.customlights.client.mixin;

import com.cotii.customlights.client.compat.ItemStackHolder;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.21.9 and later: tells the render state which stack its layers are for (its owner is any item owner now). */
@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
    @Inject(method = "appendItemLayers", at = @At("HEAD"))
    private void customlights$rememberStack(ItemStackRenderState state, ItemStack stack, net.minecraft.world.item.ItemDisplayContext context,
                                             net.minecraft.world.level.Level level, net.minecraft.world.entity.ItemOwner owner, int seed,
                                             CallbackInfo ci) {
        ((ItemStackHolder) state).customlights$setStack(stack);
    }
}
