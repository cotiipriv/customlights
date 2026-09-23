package com.cotii.customlights.client.mixin;

import com.cotii.customlights.client.light.MarkerLights;
import com.cotii.customlights.client.render.LightRenderer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.x: the frame is drawn from the camera state extracted before it, and both projections (the world's and the first person
 * hand's) are handed to GPU buffers, so they are taken there.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Unique
    private Matrix4f customlights$levelProjection;
    @Unique
    private Matrix4f customlights$handProjection;

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"))
    private GpuBufferSlice customlights$levelProjection(ProjectionMatrixBuffer buffer, Matrix4f projection, Operation<GpuBufferSlice> original) {
        customlights$levelProjection = new Matrix4f(projection);
        return original.call(buffer, projection);
    }

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lnet/minecraft/client/renderer/Projection;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"))
    private GpuBufferSlice customlights$handProjection(ProjectionMatrixBuffer buffer, Projection projection, Operation<GpuBufferSlice> original) {
        customlights$handProjection = projection.getMatrix(new Matrix4f());
        return original.call(buffer, projection);
    }

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V"))
    private void customlights$aroundWorld(LevelRenderer levelRenderer, GraphicsResourceAllocator allocator, DeltaTracker deltaTracker,
                                          boolean renderOutline, CameraRenderState camera, Matrix4fc viewRotation, GpuBufferSlice fog,
                                          Vector4f fogColor, boolean renderSky, Operation<Void> original) {
        LightRenderer.beginFrame(Minecraft.getInstance().gameRenderer.mainCamera(), deltaTracker.getGameTimeDeltaPartialTick(true));
        customlights$handProjection = null;
        MarkerLights.setPhase(MarkerLights.Phase.WORLD);
        try {
            original.call(levelRenderer, allocator, deltaTracker, renderOutline, camera, viewRotation, fog, fogColor, renderSky);
        } finally {
            MarkerLights.setPhase(MarkerLights.Phase.NONE);
        }
        Matrix4f projection = customlights$levelProjection != null ? customlights$levelProjection : new Matrix4f(camera.projectionMatrix);
        LightRenderer.afterWorld(new Matrix4f(viewRotation), projection);
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
