package com.cotii.customlights.neoforge.fabric.client.rendering.v1;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;

/** NeoForge build: what a world render callback gets (from RenderLevelStageEvent). */
public interface WorldRenderContext {
    Camera camera();

    /** How far into the tick this pass is (1.21 and up). */
    net.minecraft.client.DeltaTracker tickCounter();

    PoseStack matrixStack();

    MultiBufferSource consumers();

    float tickDelta();
}
