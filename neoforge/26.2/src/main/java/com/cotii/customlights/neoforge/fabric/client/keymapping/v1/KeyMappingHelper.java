package com.cotii.customlights.neoforge.fabric.client.keymapping.v1;

import net.minecraft.client.KeyMapping;

import java.util.ArrayList;
import java.util.List;

/** Forge build: key mappings are collected here and handed over in RegisterKeyMappingsEvent. */
public final class KeyMappingHelper {
    public static final List<KeyMapping> MAPPINGS = new ArrayList<>();

    private KeyMappingHelper() {
    }

    public static KeyMapping registerKeyMapping(KeyMapping mapping) {
        MAPPINGS.add(mapping);
        return mapping;
    }
}
