package com.cotii.customlights.client.mixin;

import com.cotii.customlights.client.light.MarkerLights;
import com.cotii.customlights.client.render.LightRenderer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.21.4 and later: the world is drawn through a frame graph (with a resource allocator, without the light texture). */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"))
    private void customlights$aroundWorld(LevelRenderer levelRenderer, com.mojang.blaze3d.resource.GraphicsResourceAllocator allocator,
                                          DeltaTracker deltaTracker, boolean renderOutline, Camera camera, GameRenderer gameRenderer,
                                          Matrix4f viewRotation, Matrix4f projection, Operation<Void> original) {
        LightRenderer.beginFrame(camera, deltaTracker.getGameTimeDeltaPartialTick(true));
        MarkerLights.setPhase(MarkerLights.Phase.WORLD);
        try {
            original.call(levelRenderer, allocator, deltaTracker, renderOutline, camera, gameRenderer, viewRotation, projection);
        } finally {
            MarkerLights.setPhase(MarkerLights.Phase.NONE);
        }
        LightRenderer.afterWorld(viewRotation, projection);
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
        LightRenderer.afterHand(RenderSystem.getProjectionMatrix());
    }
}
