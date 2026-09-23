package com.cotii.customlights.client.network;

import com.cotii.customlights.Customlights;
import net.fabricmc.fabric.api.client.networking.v1.C2SPlayChannelEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * The two plugin channels, as this game version sends them: {@code customlights:sync} from the server plugin and {@code
 * customlights:edit} back to it.
 */
public final class SyncNetwork {
    public static final int MAX_JSON_LENGTH = 1_048_576;
    /** Serverbound plugin messages are limited to 32767 bytes. */
    private static final int MAX_SEND_BYTES = 32000;

    private SyncNetwork() {
    }

    /** Listens for the plugin's messages. */
    public static void registerSync(Consumer<String> handler) {
        PayloadTypeRegistry.playS2C().register(SyncPayload.TYPE, SyncPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(SyncPayload.TYPE, (payload, context) -> context.client().execute(() -> handler.accept(payload.json())));
    }

    /** Prepares the channel back to the plugin; {@code onAvailable} runs when the server accepts it. */
    public static void registerEdits(Runnable onAvailable) {
        PayloadTypeRegistry.playC2S().register(EditPayload.TYPE, EditPayload.CODEC);
        C2SPlayChannelEvents.REGISTER.register((handler, sender, client, channels) -> {
            if (channels.contains(EditPayload.TYPE.id())) {
                client.execute(onAvailable);
            }
        });
    }

    public static boolean canSendEdits() {
        return ClientPlayNetworking.canSend(EditPayload.TYPE);
    }

    /** False when the server does not take them or the message is too big for one packet. */
    public static boolean sendEdit(String json) {
        if (!canSendEdits() || json.getBytes(StandardCharsets.UTF_8).length > MAX_SEND_BYTES) {
            return false;
        }
        ClientPlayNetworking.send(new EditPayload(json));
        return true;
    }

    private record SyncPayload(String json) implements CustomPacketPayload {
        private static final CustomPacketPayload.Type<SyncPayload> TYPE = new CustomPacketPayload.Type<>(Customlights.id("sync"));
        private static final StreamCodec<RegistryFriendlyByteBuf, SyncPayload> CODEC = StreamCodec.of(
                (buffer, payload) -> buffer.writeUtf(payload.json, MAX_JSON_LENGTH),
                buffer -> new SyncPayload(buffer.readUtf(MAX_JSON_LENGTH))
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private record EditPayload(String json) implements CustomPacketPayload {
        private static final CustomPacketPayload.Type<EditPayload> TYPE = new CustomPacketPayload.Type<>(Customlights.id("edit"));
        private static final StreamCodec<RegistryFriendlyByteBuf, EditPayload> CODEC = StreamCodec.of(
                (buffer, payload) -> buffer.writeUtf(payload.json, MAX_SEND_BYTES),
                buffer -> new EditPayload(buffer.readUtf(MAX_SEND_BYTES))
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
