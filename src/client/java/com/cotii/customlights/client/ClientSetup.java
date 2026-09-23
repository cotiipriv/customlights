package com.cotii.customlights.client;

import com.cotii.customlights.client.command.LightCommands;
import com.cotii.customlights.client.dev.DevAutoTest;
import com.cotii.customlights.client.gui.EditorKeys;
import com.cotii.customlights.client.gui.EditorOpener;
import com.cotii.customlights.client.gui.ObjectPreview;
import com.cotii.customlights.client.light.LightManager;
import com.cotii.customlights.client.light.MarkerLights;
import com.cotii.customlights.client.light.ObjectLights;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/** Registers everything with Fabric API. */
public final class ClientSetup {
    private ClientSetup() {
    }

    public static void initialize() {
        com.cotii.customlights.client.network.SyncNetwork.registerSync(com.cotii.customlights.client.network.ServerLightSync::handle);
        com.cotii.customlights.client.network.ServerEdits.register();
        com.cotii.customlights.client.render.LightRenderer.loadSettings();
        KeyBindingHelper.registerKeyBinding(EditorKeys.OPEN);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> LightCommands.register(dispatcher));
        ClientTickEvents.END_CLIENT_TICK.register(LightManager::tick);
        ClientTickEvents.END_CLIENT_TICK.register(EditorOpener::tick);
        ClientTickEvents.END_CLIENT_TICK.register(ObjectPreview::tick);
        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> ObjectLights.onChunkLoaded(chunk));
        ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> ObjectLights.onChunkUnloaded(chunk));
        ObjectPreview.register();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(LightManager::onDisconnect));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> LightManager.saveNow());
        com.cotii.customlights.client.compat.ModelMarkerSetup.register();
        DevAutoTest.register();
    }

}
