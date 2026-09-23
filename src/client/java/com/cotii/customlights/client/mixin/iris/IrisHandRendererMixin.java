package com.cotii.customlights.client.mixin.iris;

import com.cotii.customlights.client.light.MarkerLights;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * With a shader pack, Iris (Oculus on Forge) draws the first person hand itself, in view space, in the middle of the world
 * pass: item lights drawn there are hand lights.
 */
@Pseudo
@Mixin(targets = {"net.irisshaders.iris.pathways.HandRenderer", "net.coderbot.iris.pathways.HandRenderer"}, remap = false)
public abstract class IrisHandRendererMixin {
    @Inject(method = {"renderSolid", "renderTranslucent"}, at = @At("HEAD"), require = 0, remap = false)
    private void customlights$handStart(CallbackInfo ci) {
        MarkerLights.setIrisHand(true);
    }

    @Inject(method = {"renderSolid", "renderTranslucent"}, at = @At("RETURN"), require = 0, remap = false)
    private void customlights$handEnd(CallbackInfo ci) {
        MarkerLights.setIrisHand(false);
    }
}
