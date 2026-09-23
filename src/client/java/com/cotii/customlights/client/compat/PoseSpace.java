package com.cotii.customlights.client.compat;

/**
 * Where the poses of drawn models end: from 1.20.5 on in camera-relative world axes, in the world pass and in the first
 * person hand pass alike (the game undoes the view rotation in the hand's pose).
 */
public final class PoseSpace {
    private PoseSpace() {
    }

    public static boolean viewSpace() {
        return false;
    }
}
