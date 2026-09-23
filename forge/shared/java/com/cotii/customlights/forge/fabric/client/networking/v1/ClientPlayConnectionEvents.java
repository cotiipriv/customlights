package com.cotii.customlights.forge.fabric.client.networking.v1;

import com.cotii.customlights.forge.fabric.event.Event;
import com.cotii.customlights.forge.fabric.networking.v1.PacketSender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/** Forge build: joining and leaving a world or server (ClientPlayerNetworkEvent.LoggingIn / LoggingOut). */
public final class ClientPlayConnectionEvents {
    public static final Event<Join> JOIN = new Event<>();
    public static final Event<Disconnect> DISCONNECT = new Event<>();

    private ClientPlayConnectionEvents() {
    }

    @FunctionalInterface
    public interface Join {
        void onPlayReady(ClientPacketListener handler, PacketSender sender, Minecraft client);
    }

    @FunctionalInterface
    public interface Disconnect {
        void onPlayDisconnect(ClientPacketListener handler, Minecraft client);
    }
}
