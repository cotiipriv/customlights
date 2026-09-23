package com.cotii.customlights.client.mixin;

import com.cotii.customlights.client.light.ObjectLights;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.21.9 and later: a block entity is read into a render state before it is drawn, the last moment the block entity itself
 * is at hand, so its object light (and a beacon's beam) is found there.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
    @Inject(method = "tryExtractRenderState", at = @At("HEAD"))
    private <E extends BlockEntity> void customlights$blockEntityLight(E blockEntity, float partialTick, ModelFeatureRenderer.CrumblingOverlay crumbling,
                                                                     CallbackInfoReturnable<?> cir) {
        ObjectLights.onBlockEntityRendered(blockEntity);
    }
}
