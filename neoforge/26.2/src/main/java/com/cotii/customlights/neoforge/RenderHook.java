package com.cotii.customlights.neoforge;

import com.cotii.customlights.neoforge.fabric.client.rendering.v1.level.LevelRenderContext;
import com.cotii.customlights.neoforge.fabric.client.rendering.v1.level.LevelRenderEvents;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/** 26.x: each stage is its own event and the frame's state comes with it. */
public final class RenderHook {
    private RenderHook() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RenderHook::onAfterFeatures);
        NeoForge.EVENT_BUS.addListener(RenderHook::onAfterSky);
    }

    private static void onAfterFeatures(RenderLevelStageEvent.AfterOpaqueFeatures event) {
        LevelRenderEvents.AFTER_ENTITIES.fire(listener -> listener.afterEntities(context(event)));
    }

    /** Where the editor's preview would collect its drawing. */
    private static void onAfterSky(RenderLevelStageEvent.AfterSky event) {
        LevelRenderEvents.COLLECT_SUBMITS.fire(listener -> listener.collectSubmits(context(event)));
    }

    private static LevelRenderContext context(RenderLevelStageEvent event) {
        return new LevelRenderContext() {
            @Override
            public Camera camera() {
                return Minecraft.getInstance().gameRenderer.getMainCamera();
            }

            @Override
            public net.minecraft.client.DeltaTracker tickCounter() {
                return Minecraft.getInstance().getDeltaTracker();
            }

            @Override
            public PoseStack matrixStack() {
                return event.getPoseStack();
            }

            @Override
            public PoseStack poseStack() {
                return event.getPoseStack();
            }

            @Override
            public SubmitNodeCollector submitNodeCollector() {
                // NeoForge's render events do not hand out the frame's queue, so whatever draws through it is skipped
                return null;
            }

            @Override
            public LevelRenderState levelState() {
                return event.getLevelRenderState();
            }

            @Override
            public float tickDelta() {
                return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
            }
        };
    }
}
