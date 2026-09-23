package com.cotii.customlights.client.mixin;

import com.cotii.customlights.client.light.ObjectLights;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Particles of a type with an object light carry that light. */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Inject(method = "createParticle", at = @At("RETURN"))
    private void customlights$track(ParticleOptions options, double x, double y, double z, double speedX, double speedY, double speedZ,
                                    CallbackInfoReturnable<Particle> cir) {
        ObjectLights.onParticleCreated(cir.getReturnValue(), options.getType());
    }
}
