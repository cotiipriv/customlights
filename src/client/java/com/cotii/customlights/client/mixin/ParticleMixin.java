package com.cotii.customlights.client.mixin;

import com.cotii.customlights.client.light.ObjectLights;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Exposes a particle's position for its object light. */
@Mixin(Particle.class)
public abstract class ParticleMixin implements ObjectLights.ParticlePosition {
    @Shadow
    protected double x;
    @Shadow
    protected double y;
    @Shadow
    protected double z;
    @Shadow
    protected double xo;
    @Shadow
    protected double yo;
    @Shadow
    protected double zo;

    @Override
    public double customlights$x() {
        return x;
    }

    @Override
    public double customlights$y() {
        return y;
    }

    @Override
    public double customlights$z() {
        return z;
    }

    @Override
    public double customlights$previousX() {
        return xo;
    }

    @Override
    public double customlights$previousY() {
        return yo;
    }

    @Override
    public double customlights$previousZ() {
        return zo;
    }
}
