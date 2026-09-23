package com.cotii.customlights.client.network;

import com.cotii.customlights.Customlights;
import com.cotii.customlights.neoforge.NeoBus;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlerEvent;
import net.neoforged.neoforge.network.registration.IPayloadRegistrar;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * NeoForge 1.20.4: the server plugin's two channels as optional payloads (one JSON string each way, as on Fabric), so
 * any server works, with or without the plugin.
 */
public final class SyncNetwork {
    public static final int MAX_JSON_LENGTH = 1_048_576;
    /** Serverbound plugin messages are limited to 32767 bytes. */
    private static final int MAX_SEND_BYTES = 32000;
    private static final ResourceLocation SYNC = Customlights.id("sync");
    private static final ResourceLocation EDIT = Customlights.id("edit");
    private static boolean registered;

    private SyncNetwork() {
    }

    /** One JSON string, written the way the plugin writes it (a length and then the text). */
    public record JsonPayload(ResourceLocation id, String json) implements CustomPacketPayload {
        @Override
        public void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(json, MAX_JSON_LENGTH);
        }
    }

    public static void registerSync(Consumer<String> handler) {
        IEventBus bus = NeoBus.get();
        if (bus == null || registered) {
            return;
        }
        registered = true;
        bus.addListener((RegisterPayloadHandlerEvent event) -> {
            IPayloadRegistrar registrar = event.registrar(Customlights.MOD_ID).optional();
            registrar.play(SYNC, buffer -> new JsonPayload(SYNC, buffer.readUtf(MAX_JSON_LENGTH)),
                    builder -> builder.client((payload, context) -> context.workHandler().submitAsync(() -> handler.accept(payload.json()))));
            registrar.play(EDIT, buffer -> new JsonPayload(EDIT, buffer.readUtf(MAX_JSON_LENGTH)),
                    builder -> builder.server((payload, context) -> {
                    }));
        });
    }

    /** The plugin answers the join hello; there is no channel list to wait for here. */
    public static void registerEdits(Runnable onAvailable) {
    }

    public static boolean canSendEdits() {
        return Minecraft.getInstance().getConnection() != null;
    }

    public static boolean sendEdit(String json) {
        if (!canSendEdits() || json.getBytes(StandardCharsets.UTF_8).length > MAX_SEND_BYTES) {
            return false;
        }
        PacketDistributor.SERVER.noArg().send(new JsonPayload(EDIT, json));
        return true;
    }
}
