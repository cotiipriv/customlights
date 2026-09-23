package com.cotii.customlights.client.network;

import com.cotii.customlights.Customlights;
import com.cotii.customlights.forge.fabric.client.networking.v1.ClientPlayConnectionEvents;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.event.EventNetworkChannel;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/** Forge 1.20.1: the server plugin's channels as raw plugin channels (one JSON string each way, as on Fabric). */
public final class SyncNetwork {
    public static final int MAX_JSON_LENGTH = 1_048_576;
    /** Serverbound plugin messages are limited to 32767 bytes. */
    private static final int MAX_SEND_BYTES = 32000;
    private static final ResourceLocation SYNC = Customlights.id("sync");
    private static final ResourceLocation EDIT = Customlights.id("edit");
    private static final ResourceLocation REGISTER = new ResourceLocation("minecraft", "register");
    private static EventNetworkChannel syncChannel;

    private SyncNetwork() {
    }

    public static void registerSync(Consumer<String> handler) {
        syncChannel = NetworkRegistry.newEventChannel(SYNC, () -> "1", version -> true, version -> true);
        NetworkRegistry.newEventChannel(EDIT, () -> "1", version -> true, version -> true);
        syncChannel.addListener((NetworkEvent event) -> {
            FriendlyByteBuf payload = event.getPayload();
            if (payload == null || !event.getSource().get().getDirection().getReceptionSide().isClient()) {
                return;
            }
            String json = payload.readUtf(MAX_JSON_LENGTH);
            event.getSource().get().enqueueWork(() -> handler.accept(json));
            event.getSource().get().setPacketHandled(true);
        });
        // Paper only sends plugin messages on channels the client announced
        ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> {
            ClientPacketListener connection = client.getConnection();
            if (connection != null) {
                FriendlyByteBuf channels = new FriendlyByteBuf(Unpooled.buffer());
                channels.writeBytes((SYNC + "\0" + EDIT).getBytes(StandardCharsets.UTF_8));
                connection.send(new ServerboundCustomPayloadPacket(REGISTER, channels));
            }
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
        if (connection == null || json.getBytes(StandardCharsets.UTF_8).length > MAX_SEND_BYTES) {
            return false;
        }
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeUtf(json, MAX_SEND_BYTES);
        connection.send(new ServerboundCustomPayloadPacket(EDIT, buffer));
        return true;
    }
}
