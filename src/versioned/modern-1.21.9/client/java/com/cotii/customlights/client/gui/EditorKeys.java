package com.cotii.customlights.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/** Key mappings, 1.21.9 and later (categories are registered records). */
public final class EditorKeys {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(ResourceLocation.fromNamespaceAndPath("customlights", "main"));
    public static final KeyMapping OPEN = new KeyMapping("key.customlights.editor", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);

    private EditorKeys() {
    }
}
