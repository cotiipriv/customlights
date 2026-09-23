package com.cotii.customlights.client.compat;

/**
 * NeoForge: there is no equivalent of the model loading hook the Fabric build uses to read light markers out of baked
 * models, so lights placed inside item models (Blockbench groups) are not picked up here. Object lights, bone lights on
 * GeckoLib entities and everything else work the same.
 */
public final class ModelMarkerSetup {
    private ModelMarkerSetup() {
    }

    public static void register() {
    }
}
