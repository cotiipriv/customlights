package com.cotii.customlights.neoforge.fabric.client.rendering.v1;

import com.cotii.customlights.neoforge.fabric.event.Event;

/** Forge build: right after the entities are drawn (RenderLevelStageEvent.Stage.AFTER_ENTITIES). */
public final class WorldRenderEvents {
    public static final Event<AfterEntities> AFTER_ENTITIES = new Event<>();

    private WorldRenderEvents() {
    }

    @FunctionalInterface
    public interface AfterEntities {
        void afterEntities(WorldRenderContext context);
    }
}
