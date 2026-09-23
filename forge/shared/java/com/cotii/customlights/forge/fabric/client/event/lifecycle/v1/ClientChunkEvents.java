package com.cotii.customlights.forge.fabric.client.event.lifecycle.v1;

import com.cotii.customlights.forge.fabric.event.Event;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;

/** Forge build: client chunks loaded and unloaded (fired from ChunkEvent on the client level). */
public final class ClientChunkEvents {
    public static final Event<Load> CHUNK_LOAD = new Event<>();
    public static final Event<Unload> CHUNK_UNLOAD = new Event<>();

    private ClientChunkEvents() {
    }

    @FunctionalInterface
    public interface Load {
        void onChunkLoad(ClientLevel level, LevelChunk chunk);
    }

    @FunctionalInterface
    public interface Unload {
        void onChunkUnload(ClientLevel level, LevelChunk chunk);
    }
}
