package com.cotii.customlights.neoforge.fabric.client.rendering.v1.level;

import com.cotii.customlights.neoforge.fabric.event.Event;

/** 26.x: the level render callbacks, fired from RenderLevelStageEvent. */
public final class LevelRenderEvents {
    public static final Event<CollectSubmits> COLLECT_SUBMITS = new Event<>();
    public static final Event<AfterEntities> AFTER_ENTITIES = new Event<>();

    private LevelRenderEvents() {
    }

    @FunctionalInterface
    public interface CollectSubmits {
        void collectSubmits(LevelRenderContext context);
    }

    @FunctionalInterface
    public interface AfterEntities {
        void afterEntities(LevelRenderContext context);
    }
}
