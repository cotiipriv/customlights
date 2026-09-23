package com.cotii.customlights.neoforge;

import com.cotii.customlights.neoforge.fabric.client.rendering.v1.WorldRenderContext;
import com.cotii.customlights.neoforge.fabric.client.rendering.v1.WorldRenderEvents;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Where the lights are drawn: right after the entities. From 21.8 on, each stage is its own event. */
public final class RenderHook {
    private RenderHook() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RenderHook::onAfterEntities);
    }

    private static void onAfterEntities(RenderLevelStageEvent.AfterEntities event) {
        WorldRenderEvents.AFTER_ENTITIES.fire(listener -> listener.afterEntities(context(event)));
    }

    private static WorldRenderContext context(RenderLevelStageEvent event) {
        return new WorldRenderContext() {
            @Override
            public Camera camera() {
                return event.getCamera();
            }

            @Override
            public net.minecraft.client.DeltaTracker tickCounter() {
                return event.getPartialTick();
            }

            @Override
            public PoseStack matrixStack() {
                return event.getPoseStack();
            }

            @Override
            public MultiBufferSource consumers() {
                return Minecraft.getInstance().renderBuffers().bufferSource();
            }

            @Override
            public float tickDelta() {
                return event.getPartialTick().getGameTimeDeltaPartialTick(false);
            }
        };
    }
}
