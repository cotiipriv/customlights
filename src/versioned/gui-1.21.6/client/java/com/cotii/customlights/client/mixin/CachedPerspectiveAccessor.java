package com.cotii.customlights.client.mixin;

import net.minecraft.client.renderer.CachedPerspectiveProjectionMatrixBuffer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 1.21.6 and later: the projection behind a cached projection buffer (the first person hand's one). */
@Mixin(CachedPerspectiveProjectionMatrixBuffer.class)
public interface CachedPerspectiveAccessor {
    @Invoker("createProjectionMatrix")
    Matrix4f customlights$createProjectionMatrix(int width, int height, float fov);
}
