package com.cotii.customlights.client.mixin;

import com.cotii.customlights.client.compat.ItemLayerTracker;
import com.cotii.customlights.client.compat.ItemStackHolder;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.21.9 and later: the render state remembers its item stack and announces it while its layers are handed over. */
@Mixin(ItemStackRenderState.class)
public abstract class ItemStackRenderStateMixin implements ItemStackHolder {
    @Unique
    private ItemStack customlights$stack;

    @Override
    public void customlights$setStack(ItemStack stack) {
        customlights$stack = stack;
    }

    @Inject(method = "submit", at = @At("HEAD"))
    private void customlights$begin(CallbackInfo ci) {
        ItemLayerTracker.begin(customlights$stack);
    }

    @Inject(method = "submit", at = @At("RETURN"))
    private void customlights$end(CallbackInfo ci) {
        ItemLayerTracker.end();
    }
}
