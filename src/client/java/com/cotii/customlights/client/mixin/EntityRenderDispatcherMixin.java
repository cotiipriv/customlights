package com.cotii.customlights.client.mixin;

import com.cotii.customlights.client.compat.Compat;
import com.cotii.customlights.client.light.MarkerLights;
import com.cotii.customlights.client.light.ObjectLights;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells model light markers which entity they belong to. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void customlights$ownerStart(CallbackInfo ci, @Local(argsOnly = true) Entity entity, @Share("owner") LocalIntRef previous) {
        previous.set(MarkerLights.setOwner(entity.getId()));
        ObjectLights.onEntityRendered(entity, Compat.partialTick(Minecraft.getInstance()));
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void customlights$ownerEnd(CallbackInfo ci, @Share("owner") LocalIntRef previous) {
        MarkerLights.setOwner(previous.get());
    }
}
