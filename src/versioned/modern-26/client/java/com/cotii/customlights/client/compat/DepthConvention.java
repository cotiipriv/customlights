package com.cotii.customlights.client.compat;

import com.mojang.blaze3d.systems.RenderSystem;

/** 26.x keeps depth reversed: 1 is the near plane and 0 is far (nothing drawn), tested with greater or equal. */
public final class DepthConvention {
    private DepthConvention() {
    }

    public static boolean reversed() {
        return true;
    }

    public static boolean zeroToOne() {
        return RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
    }
}
