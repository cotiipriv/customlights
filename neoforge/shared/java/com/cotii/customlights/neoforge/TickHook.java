package com.cotii.customlights.neoforge;

import com.cotii.customlights.neoforge.fabric.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** The end of a client tick. NeoForge split this event in 21.0, so 1.20.4 brings its own copy of this file. */
public final class TickHook {
    private TickHook() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(TickHook::onClientTick);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientTickEvents.END_CLIENT_TICK.fire(listener -> listener.onEndTick(minecraft));
    }
}
