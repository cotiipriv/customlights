package com.cotii.customlights.neoforge.fabric.client.event.lifecycle.v1;

import com.cotii.customlights.neoforge.fabric.event.Event;
import net.minecraft.client.Minecraft;

/** Forge build: the end of each client tick (fired from TickEvent.ClientTickEvent). */
public final class ClientTickEvents {
    public static final Event<EndTick> END_CLIENT_TICK = new Event<>();

    private ClientTickEvents() {
    }

    @FunctionalInterface
    public interface EndTick {
        void onEndTick(Minecraft client);
    }
}
