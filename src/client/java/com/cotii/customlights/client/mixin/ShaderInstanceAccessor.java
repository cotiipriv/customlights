package com.cotii.customlights.client.mixin;

import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the light pass tell vanilla its cached shader program is no longer bound. */
@Mixin(ShaderInstance.class)
public interface ShaderInstanceAccessor {
    @Accessor("lastProgramId")
    static void customlights$setLastProgramId(int id) {
        throw new AssertionError();
    }

    @Accessor("lastAppliedShader")
    static void customlights$setLastAppliedShader(ShaderInstance shader) {
        throw new AssertionError();
    }
}
