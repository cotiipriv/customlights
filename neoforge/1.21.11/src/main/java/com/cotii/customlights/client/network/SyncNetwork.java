package com.cotii.customlights.client.network;

import com.cotii.customlights.Customlights;
import com.cotii.customlights.neoforge.NeoBus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * NeoForge: the server plugin's two channels as optional payloads (one JSON string each way, as on Fabric), so any
 * server works, with or without the plugin.
 */
public final class SyncNetwork {
    public static final int MAX_JSON_LENGTH = 1_048_576;
    /** Serverbound plugin messages are limited to 32767 bytes. */
    private static final int MAX_SEND_BYTES = 32000;
    private static final CustomPacketPayload.Type<JsonPayload> SYNC = new CustomPacketPayload.Type<>(Customlights.id("sync"));
    private static final CustomPacketPayload.Type<EditPayload> EDIT = new CustomPacketPayload.Type<>(Customlights.id("edit"));
    private static boolean registered;

    private SyncNetwork() {
    }

    /** One JSON string, written the way the plugin writes it (a length and then the text). */
    public record JsonPayload(String json) implements CustomPacketPayload {
        static final StreamCodec<io.netty.buffer.ByteBuf, JsonPayload> CODEC =
                StreamCodec.composite(ByteBufCodecs.stringUtf8(MAX_JSON_LENGTH), JsonPayload::json, JsonPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return SYNC;
        }
    }

    public record EditPayload(String json) implements CustomPacketPayload {
        static final StreamCodec<io.netty.buffer.ByteBuf, EditPayload> CODEC =
                StreamCodec.composite(ByteBufCodecs.stringUtf8(MAX_JSON_LENGTH), EditPayload::json, EditPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return EDIT;
        }
    }

    public static void registerSync(Consumer<String> handler) {
        IEventBus bus = NeoBus.get();
        if (bus == null || registered) {
            return;
        }
        registered = true;
        bus.addListener((RegisterPayloadHandlersEvent event) -> {
            PayloadRegistrar registrar = event.registrar("1").optional();
            registrar.playToClient(SYNC, JsonPayload.CODEC, (payload, context) -> context.enqueueWork(() -> handler.accept(payload.json())));
            registrar.playToServer(EDIT, EditPayload.CODEC, (payload, context) -> {
            });
        });
    }

    /** The plugin answers the join hello; there is no channel list to wait for here. */
    public static void registerEdits(Runnable onAvailable) {
    }

    public static boolean canSendEdits() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(EDIT);
    }

    public static boolean sendEdit(String json) {
        if (!canSendEdits() || json.getBytes(StandardCharsets.UTF_8).length > MAX_SEND_BYTES) {
            return false;
        }
        ClientPacketDistributor.sendToServer(new EditPayload(json));
        return true;
    }
}
