package com.cotii.customlights.client.mixin;

import com.cotii.customlights.client.light.MarkerLights;
import com.cotii.customlights.client.light.ObjectLights;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells model light markers which block entity they belong to, and finds beacon beams. */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private <E extends BlockEntity> void customlights$ownerStart(E blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                                                                CallbackInfo ci, @Share("owner") LocalIntRef previous) {
        // Negative so it never matches an entity id
        previous.set(MarkerLights.setOwner(blockEntity.getBlockPos().hashCode() | Integer.MIN_VALUE));
        ObjectLights.onBlockEntityRendered(blockEntity);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private <E extends BlockEntity> void customlights$ownerEnd(E blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                                                              CallbackInfo ci, @Share("owner") LocalIntRef previous) {
        MarkerLights.setOwner(previous.get());
    }
}
