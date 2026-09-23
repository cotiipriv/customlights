package com.cotii.customlights.neoforge.fabric.client.keybinding.v1;

import net.minecraft.client.KeyMapping;

import java.util.ArrayList;
import java.util.List;

/** Forge build: key mappings are collected here and handed over in RegisterKeyMappingsEvent. */
public final class KeyBindingHelper {
    public static final List<KeyMapping> MAPPINGS = new ArrayList<>();

    private KeyBindingHelper() {
    }

    public static KeyMapping registerKeyBinding(KeyMapping mapping) {
        MAPPINGS.add(mapping);
        return mapping;
    }
}
