package com.cotii.customlights.client.compat;

/**
 * How the game stores depth: up to 1.21.11 0 is the near plane and 1 is far (nothing drawn), and the depth range is OpenGL's
 * usual -1..1.
 */
public final class DepthConvention {
    private DepthConvention() {
    }

    public static boolean reversed() {
        return false;
    }

    public static boolean zeroToOne() {
        return false;
    }
}
