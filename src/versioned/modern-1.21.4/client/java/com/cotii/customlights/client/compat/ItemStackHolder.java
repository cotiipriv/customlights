package com.cotii.customlights.client.compat;

import net.minecraft.world.item.ItemStack;

/** Added to the item render state: the stack it was set up for. */
public interface ItemStackHolder {
    void customlights$setStack(ItemStack stack);
}
