package com.cotii.customlights.client.compat;

import com.cotii.customlights.client.mixin.RenderChunkInfoAccessor;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

/** 1.20.1: the sections being drawn come from the render chunk infos in the frustum. */
public final class VisibleSections {
    private VisibleSections() {
    }

    public static boolean fill(LongSet out) {
        var levelRenderer = Minecraft.getInstance().levelRenderer;
        for (Object info : ((com.cotii.customlights.client.mixin.LevelRendererAccessor) levelRenderer).customlights$renderChunksInFrustum()) {
            ChunkRenderDispatcher.RenderChunk chunk = ((RenderChunkInfoAccessor) info).customlights$chunk();
            if (chunk == null) {
                continue;
            }
            BlockPos origin = chunk.getOrigin();
            out.add(SectionPos.asLong(origin.getX() >> 4, origin.getY() >> 4, origin.getZ() >> 4));
        }
        return !out.isEmpty();
    }
}
