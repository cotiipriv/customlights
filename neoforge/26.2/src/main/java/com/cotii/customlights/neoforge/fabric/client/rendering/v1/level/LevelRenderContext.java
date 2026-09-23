package com.cotii.customlights.neoforge.fabric.client.rendering.v1.level;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;

/** 26.x: what a level render callback gets. */
public interface LevelRenderContext extends com.cotii.customlights.neoforge.fabric.client.rendering.v1.WorldRenderContext {
    /** Null on this loader: NeoForge's render events do not hand out the frame's queue. */
    SubmitNodeCollector submitNodeCollector();

    LevelRenderState levelState();

    PoseStack poseStack();
}
