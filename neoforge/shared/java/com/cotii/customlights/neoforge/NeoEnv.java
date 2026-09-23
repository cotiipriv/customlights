package com.cotii.customlights.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

/** Which side the game is running. FML changed how this is asked for in 21.11, so that version has its own copy. */
public final class NeoEnv {
    private NeoEnv() {
    }

    public static boolean isClient() {
        return FMLEnvironment.dist == Dist.CLIENT;
    }
}
