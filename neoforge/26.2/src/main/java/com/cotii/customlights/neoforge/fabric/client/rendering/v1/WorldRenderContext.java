package com.cotii.customlights.neoforge.fabric.client.rendering.v1;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;

/** 26.x: what a level render callback gets (this version draws through the frame's queue, not a buffer source). */
public interface WorldRenderContext {
    Camera camera();

    net.minecraft.client.DeltaTracker tickCounter();

    PoseStack matrixStack();

    float tickDelta();
}
