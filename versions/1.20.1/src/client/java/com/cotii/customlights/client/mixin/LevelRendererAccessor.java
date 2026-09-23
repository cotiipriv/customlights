package com.cotii.customlights.client.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 1.20.1: the chunks in the frustum are kept as render chunk infos. */
@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {
    @Accessor("renderChunksInFrustum")
    ObjectArrayList<?> customlights$renderChunksInFrustum();
}
