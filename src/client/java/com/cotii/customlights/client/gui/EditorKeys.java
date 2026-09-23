package com.cotii.customlights.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/** Key mappings, up to 1.21.8 (categories are translation keys). */
public final class EditorKeys {
    public static final KeyMapping OPEN = new KeyMapping("key.customlights.editor", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.customlights");

    private EditorKeys() {
    }
}
