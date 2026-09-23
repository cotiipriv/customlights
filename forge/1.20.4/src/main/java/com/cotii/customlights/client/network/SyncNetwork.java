package com.cotii.customlights.client.network;

import com.cotii.customlights.Customlights;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.EventNetworkChannel;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Forge 1.20.4: the server plugin's channels as raw event channels (one JSON string each way, as on Fabric), optional on
 * both sides so any server works, with or without the plugin.
 */
public final class SyncNetwork {
    public static final int MAX_JSON_LENGTH = 1_048_576;
    /** Serverbound plugin messages are limited to 32767 bytes. */
    private static final int MAX_SEND_BYTES = 32000;
    private static final ResourceLocation SYNC = Customlights.id("sync");
    private static final ResourceLocation EDIT = Customlights.id("edit");
    private static EventNetworkChannel editChannel;

    private SyncNetwork() {
    }

    public static void registerSync(Consumer<String> handler) {
        EventNetworkChannel syncChannel = ChannelBuilder.named(SYNC).optional().eventNetworkChannel();
        editChannel = ChannelBuilder.named(EDIT).optional().eventNetworkChannel();
        syncChannel.addListener((CustomPayloadEvent event) -> {
            FriendlyByteBuf payload = event.getPayload();
            if (payload == null || !event.getSource().isClientSide()) {
                return;
            }
            String json = payload.readUtf(MAX_JSON_LENGTH);
            event.getSource().enqueueWork(() -> handler.accept(json));
            event.getSource().setPacketHandled(true);
        });
    }

    /** The plugin answers the join hello; there is no channel list to wait for on Forge. */
    public static void registerEdits(Runnable onAvailable) {
    }

    public static boolean canSendEdits() {
        return Minecraft.getInstance().getConnection() != null;
    }

    public static boolean sendEdit(String json) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null || editChannel == null || json.getBytes(StandardCharsets.UTF_8).length > MAX_SEND_BYTES) {
            return false;
        }
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeUtf(json, MAX_SEND_BYTES);
        editChannel.send(buffer, connection.getConnection());
        return true;
    }
}
