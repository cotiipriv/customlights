package com.cotii.customlights.client.network;

import com.cotii.customlights.Customlights;
import net.fabricmc.fabric.api.client.networking.v1.C2SPlayChannelEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/** 1.20.1 and 1.20.4: plugin channels still send a raw buffer instead of a typed payload. */
public final class SyncNetwork {
    public static final int MAX_JSON_LENGTH = 1_048_576;
    /** Serverbound plugin messages are limited to 32767 bytes. */
    private static final int MAX_SEND_BYTES = 32000;
    private static final ResourceLocation SYNC = Customlights.id("sync");
    private static final ResourceLocation EDIT = Customlights.id("edit");

    private SyncNetwork() {
    }

    public static void registerSync(Consumer<String> handler) {
        ClientPlayNetworking.registerGlobalReceiver(SYNC, (client, listener, buffer, sender) -> {
            String json = buffer.readUtf(MAX_JSON_LENGTH);
            client.execute(() -> handler.accept(json));
        });
    }

    public static void registerEdits(Runnable onAvailable) {
        C2SPlayChannelEvents.REGISTER.register((listener, sender, client, channels) -> {
            if (channels.contains(EDIT)) {
                client.execute(onAvailable);
            }
        });
    }

    public static boolean canSendEdits() {
        return ClientPlayNetworking.canSend(EDIT);
    }

    public static boolean sendEdit(String json) {
        if (!canSendEdits() || json.getBytes(StandardCharsets.UTF_8).length > MAX_SEND_BYTES) {
            return false;
        }
        FriendlyByteBuf buffer = PacketByteBufs.create();
        buffer.writeUtf(json, MAX_SEND_BYTES);
        ClientPlayNetworking.send(EDIT, buffer);
        return true;
    }
}
