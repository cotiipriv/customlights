package com.cotii.customlights.client.mixin;

import com.cotii.customlights.client.light.MarkerLights;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.x: entities and block entities of the level are read into render states before the frame is drawn, so their object
 * lights are found then; this marks that as the world (not a menu preview or a shadow pass).
 */
@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
    @Inject(method = "extract", at = @At("HEAD"))
    private void customlights$worldStart(CallbackInfo ci) {
        MarkerLights.setPhase(MarkerLights.Phase.WORLD);
    }

    @Inject(method = "extract", at = @At("RETURN"))
    private void customlights$worldEnd(CallbackInfo ci) {
        MarkerLights.setPhase(MarkerLights.Phase.NONE);
    }
}
