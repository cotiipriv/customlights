package com.cotii.customlights.neoforge;

import net.neoforged.bus.api.IEventBus;

/** The mod's own event bus, kept for the parts of the shared code that need to register on it (the plugin channels). */
public final class NeoBus {
    private static IEventBus bus;

    private NeoBus() {
    }

    static void set(IEventBus modEventBus) {
        bus = modEventBus;
    }

    public static IEventBus get() {
        return bus;
    }
}
