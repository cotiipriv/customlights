package com.cotii.customlights.client.mixin;

import com.cotii.customlights.client.light.MarkerLights;
import com.cotii.customlights.client.render.LightRenderer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.9 and later (as 1.21.6, plus a separate projection for culling): the world is drawn with its fog passed along, and
 * the projection lives in a GPU buffer, so the first person hand's projection is taken where the game makes it.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Unique
    private Matrix4f customlights$handProjection;

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V"))
    private void customlights$aroundWorld(LevelRenderer levelRenderer, GraphicsResourceAllocator allocator, DeltaTracker deltaTracker,
                                          boolean renderOutline, Camera camera, Matrix4f viewRotation, Matrix4f projection, Matrix4f cullingProjection,
                                          GpuBufferSlice fog, Vector4f fogColor, boolean renderSky, Operation<Void> original) {
        LightRenderer.beginFrame(camera, deltaTracker.getGameTimeDeltaPartialTick(true));
        customlights$handProjection = null;
        MarkerLights.setPhase(MarkerLights.Phase.WORLD);
        try {
            original.call(levelRenderer, allocator, deltaTracker, renderOutline, camera, viewRotation, projection, cullingProjection, fog, fogColor, renderSky);
        } finally {
            MarkerLights.setPhase(MarkerLights.Phase.NONE);
        }
        LightRenderer.afterWorld(viewRotation, projection);
    }

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/CachedPerspectiveProjectionMatrixBuffer;getBuffer(IIF)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"))
    private GpuBufferSlice customlights$handProjection(net.minecraft.client.renderer.CachedPerspectiveProjectionMatrixBuffer buffer, int width,
                                                       int height, float fov, Operation<GpuBufferSlice> original) {
        // The hand's projection only exists inside that buffer: the same matrix is made again here
        customlights$handProjection = ((CachedPerspectiveAccessor) buffer).customlights$createProjectionMatrix(width, height, fov);
        return original.call(buffer, width, height, fov);
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"))
    private void customlights$beforeHand(CallbackInfo ci) {
        LightRenderer.beforeHand();
        MarkerLights.setPhase(MarkerLights.Phase.HAND);
    }

    @Inject(method = "renderItemInHand", at = @At("RETURN"))
    private void customlights$afterHand(CallbackInfo ci) {
        MarkerLights.setPhase(MarkerLights.Phase.NONE);
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void customlights$afterLevel(DeltaTracker deltaTracker, CallbackInfo ci) {
        LightRenderer.afterHand(customlights$handProjection);
    }
}
