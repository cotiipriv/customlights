package com.cotii.customlights.neoforge;

import com.cotii.customlights.neoforge.fabric.client.rendering.v1.WorldRenderContext;
import com.cotii.customlights.neoforge.fabric.client.rendering.v1.WorldRenderEvents;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/** 1.20.4: the stages still share one event and the partial tick is a plain float. */
public final class RenderHook {
    private RenderHook() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RenderHook::onRenderStage);
    }

    private static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        WorldRenderContext context = new WorldRenderContext() {
            @Override
            public Camera camera() {
                return event.getCamera();
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
                return event.getPartialTick();
            }
        };
        WorldRenderEvents.AFTER_ENTITIES.fire(listener -> listener.afterEntities(context));
    }
}
