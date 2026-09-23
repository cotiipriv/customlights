package com.cotii.customlights.client.mixin;

import com.cotii.customlights.client.light.ObjectLights;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.21.9 and later: an entity is read into a render state before it is drawn, and that is the last moment the entity itself
 * is at hand, so its object light is placed there.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "extractEntity", at = @At("HEAD"))
    private void customlights$entityLight(Entity entity, float partialTick, CallbackInfoReturnable<?> cir) {
        ObjectLights.onEntityRendered(entity, partialTick);
    }
}
