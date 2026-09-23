package com.cotii.customlights.client.mixin;

import com.cotii.customlights.client.light.MarkerLights;
import com.cotii.customlights.client.render.LightRenderer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.20.1 and 1.20.4: the world is drawn with a pose stack and the partial tick as a float, so the light pass reads the view
 * rotation from that stack instead of the matrix the newer versions pass in.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V"))
    private void customlights$aroundWorld(LevelRenderer levelRenderer, PoseStack poseStack, float partialTick, long finishTime, boolean renderOutline,
                                          Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projection,
                                          Operation<Void> original) {
        LightRenderer.beginFrame(camera, partialTick);
        MarkerLights.setViewRotation(poseStack.last().pose());
        MarkerLights.setPhase(MarkerLights.Phase.WORLD);
        try {
            original.call(levelRenderer, poseStack, partialTick, finishTime, renderOutline, camera, gameRenderer, lightTexture, projection);
        } finally {
            MarkerLights.setPhase(MarkerLights.Phase.NONE);
        }
        LightRenderer.afterWorld(new Matrix4f(poseStack.last().pose()), projection);
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
    private void customlights$afterLevel(float partialTick, long finishTime, PoseStack poseStack, CallbackInfo ci) {
        LightRenderer.afterHand(RenderSystem.getProjectionMatrix());
    }
}
