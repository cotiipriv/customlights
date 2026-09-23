package com.cotii.customlights.neoforge.fabric.client.rendering.v1.world;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.LevelRenderState;

/** 1.21.9 and up: the Fabric API moved this into its own package and handed out the frame's own drawing queue. */
public interface WorldRenderContext extends com.cotii.customlights.neoforge.fabric.client.rendering.v1.WorldRenderContext {
    /** Null on this loader: NeoForge's render events do not hand out the frame's queue. */
    SubmitNodeCollector commandQueue();

    LevelRenderState worldState();

    PoseStack matrices();
}
