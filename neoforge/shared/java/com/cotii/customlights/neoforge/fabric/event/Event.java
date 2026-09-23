package com.cotii.customlights.neoforge.fabric.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Forge build: the few Fabric API events the shared code registers to, kept as plain listener lists that the Forge glue
 * (CustomlightsForge) fires from the matching Forge events.
 */
public final class Event<T> {
    private final List<T> listeners = new CopyOnWriteArrayList<>();

    public void register(T listener) {
        listeners.add(listener);
    }

    public void fire(Consumer<T> call) {
        for (T listener : listeners) {
            call.accept(listener);
        }
    }
}
