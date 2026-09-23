package com.cotii.customlights.neoforge;

import com.cotii.customlights.neoforge.fabric.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TickEvent;

/** 1.20.4: one tick event with a phase, as on Forge. */
public final class TickHook {
    private TickHook() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(TickHook::onClientTick);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Minecraft minecraft = Minecraft.getInstance();
            ClientTickEvents.END_CLIENT_TICK.fire(listener -> listener.onEndTick(minecraft));
        }
    }
}
