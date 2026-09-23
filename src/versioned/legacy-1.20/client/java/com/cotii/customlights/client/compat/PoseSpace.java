package com.cotii.customlights.client.compat;

/**
 * 1.20.x draws models in view space: the world pass carries the view rotation in its pose and the first person hand pass
 * starts at the camera, looking down -Z.
 */
public final class PoseSpace {
    private PoseSpace() {
    }

    public static boolean viewSpace() {
        return true;
    }
}
