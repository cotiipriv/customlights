package com.cotii.customlights.client.compat;

/**
 * 1.21.5 and later: baked models are gone (items are drawn from plain quad lists), so lights inside item models (Blockbench
 * groups) are not picked up on these versions.
 */
public final class ModelMarkerSetup {
    private ModelMarkerSetup() {
    }

    public static void register() {
    }
}
