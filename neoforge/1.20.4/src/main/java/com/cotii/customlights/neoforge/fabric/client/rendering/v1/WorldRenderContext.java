package com.cotii.customlights.neoforge.fabric.client.rendering.v1;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;

/** 1.20.4: what a world render callback gets (there is no delta tracker on this version). */
public interface WorldRenderContext {
    Camera camera();

    PoseStack matrixStack();

    MultiBufferSource consumers();

    float tickDelta();
}
