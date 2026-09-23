package com.cotii.customlights.client.compat;

import net.minecraft.world.item.ItemStack;

/** 1.21.4 and later: which item stack is being drawn right now. */
public final class ItemLayerTracker {
    private static ItemStack current;
    private static boolean claimed;

    private ItemLayerTracker() {
    }

    public static void begin(ItemStack stack) {
        current = stack;
        claimed = false;
    }

    public static void end() {
        current = null;
    }

    public static ItemStack current() {
        return current;
    }

    /** True for the first layer of the item only (its light is placed once). */
    public static boolean claim() {
        if (claimed) {
            return false;
        }
        claimed = true;
        return true;
    }
}
