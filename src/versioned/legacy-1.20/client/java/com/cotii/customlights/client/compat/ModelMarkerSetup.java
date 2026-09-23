package com.cotii.customlights.client.compat;

/**
 * 1.20.1 and 1.20.4: the model loading API cannot report the file a baked model came from, so lights inside item models
 * (Blockbench groups) are not picked up on these versions.
 */
public final class ModelMarkerSetup {
    private ModelMarkerSetup() {
    }

    public static void register() {
    }
}
