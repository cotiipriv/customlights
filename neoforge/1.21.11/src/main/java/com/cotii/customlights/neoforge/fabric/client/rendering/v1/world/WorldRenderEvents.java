package com.cotii.customlights.neoforge.fabric.client.rendering.v1.world;

import com.cotii.customlights.neoforge.fabric.event.Event;

/** 1.21.9 and up: the world render callbacks, fired from RenderLevelStageEvent. */
public final class WorldRenderEvents {
    public static final Event<BeforeEntities> BEFORE_ENTITIES = new Event<>();
    public static final Event<AfterEntities> AFTER_ENTITIES = new Event<>();

    private WorldRenderEvents() {
    }

    @FunctionalInterface
    public interface BeforeEntities {
        void beforeEntities(WorldRenderContext context);
    }

    @FunctionalInterface
    public interface AfterEntities {
        void afterEntities(WorldRenderContext context);
    }
}
