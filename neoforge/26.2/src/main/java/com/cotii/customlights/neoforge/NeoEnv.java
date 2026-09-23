package com.cotii.customlights.neoforge;

import net.neoforged.fml.loading.FMLEnvironment;

/** 1.21.11: the side is asked for through a method now. */
public final class NeoEnv {
    private NeoEnv() {
    }

    public static boolean isClient() {
        return FMLEnvironment.getDist().isClient();
    }
}
