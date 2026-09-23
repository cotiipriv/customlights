package com.cotii.customlights.forge.fabric.client.rendering.v1;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;

/** Forge build: what a world render callback gets (from RenderLevelStageEvent). */
public interface WorldRenderContext {
    Camera camera();

    PoseStack matrixStack();

    MultiBufferSource consumers();

    float tickDelta();
}
