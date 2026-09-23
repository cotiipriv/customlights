package com.cotii.customlights.neoforge;

import com.cotii.customlights.neoforge.fabric.client.rendering.v1.world.WorldRenderContext;
import com.cotii.customlights.neoforge.fabric.client.rendering.v1.world.WorldRenderEvents;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Where the lights are drawn. From 21.8 on each stage is its own event, and on 1.21.9 and up the event carries the
 * frame's render state instead of the camera and the partial tick.
 */
public final class RenderHook {
    private RenderHook() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RenderHook::onAfterEntities);
        NeoForge.EVENT_BUS.addListener(RenderHook::onAfterSky);
    }

    private static void onAfterEntities(RenderLevelStageEvent.AfterEntities event) {
        WorldRenderEvents.AFTER_ENTITIES.fire(listener -> listener.afterEntities(context(event)));
    }

    /** As close as this loader gets to "before the entities". */
    private static void onAfterSky(RenderLevelStageEvent.AfterSky event) {
        WorldRenderEvents.BEFORE_ENTITIES.fire(listener -> listener.beforeEntities(context(event)));
    }

    private static WorldRenderContext context(RenderLevelStageEvent event) {
        return new WorldRenderContext() {
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
            public PoseStack matrices() {
                return event.getPoseStack();
            }

            @Override
            public MultiBufferSource consumers() {
                return Minecraft.getInstance().renderBuffers().bufferSource();
            }

            @Override
            public SubmitNodeCollector commandQueue() {
                // NeoForge's render events do not hand out the frame's queue, so whatever draws through it is skipped
                return null;
            }

            @Override
            public LevelRenderState worldState() {
                return event.getLevelRenderState();
            }

            @Override
            public float tickDelta() {
                return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
            }
        };
    }
}
