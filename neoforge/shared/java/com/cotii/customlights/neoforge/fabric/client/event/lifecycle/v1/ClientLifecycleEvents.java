package com.cotii.customlights.neoforge.fabric.client.event.lifecycle.v1;

import com.cotii.customlights.neoforge.fabric.event.Event;
import net.minecraft.client.Minecraft;

/** Forge build: the game is closing (fired from GameShuttingDownEvent). */
public final class ClientLifecycleEvents {
    public static final Event<ClientStopping> CLIENT_STOPPING = new Event<>();

    private ClientLifecycleEvents() {
    }

    @FunctionalInterface
    public interface ClientStopping {
        void onClientStopping(Minecraft client);
    }
}
