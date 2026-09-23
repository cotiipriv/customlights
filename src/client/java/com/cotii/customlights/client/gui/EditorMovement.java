package com.cotii.customlights.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.lwjgl.glfw.GLFW;

/**
 * Lets the player walk while the editor is open: screens get the key events, so the movement keys are read from the keyboard
 * itself every tick.
 */
final class EditorMovement {
    private EditorMovement() {
    }

    private static KeyMapping[] movementKeys(Options options) {
        return new KeyMapping[]{options.keyUp, options.keyDown, options.keyLeft, options.keyRight, options.keyJump, options.keyShift, options.keySprint};
    }

    private static boolean isHeld(Minecraft minecraft, KeyMapping mapping) {
        InputConstants.Key key = InputConstants.getKey(mapping.saveString());
        return key.getType() == InputConstants.Type.KEYSYM && key.getValue() >= 0
                && GLFW.glfwGetKey(minecraft.getWindow().getWindow(), key.getValue()) == GLFW.GLFW_PRESS;
    }

    static boolean matches(KeyMapping mapping, int keyCode) {
        InputConstants.Key key = InputConstants.getKey(mapping.saveString());
        return key.getType() == InputConstants.Type.KEYSYM && key.getValue() == keyCode;
    }

    /** Every tick: movement keys follow the keyboard; nothing moves while typing or dragging. */
    static void update(Minecraft minecraft, boolean blocked) {
        for (KeyMapping mapping : movementKeys(minecraft.options)) {
            mapping.setDown(!blocked && isHeld(minecraft, mapping));
        }
    }

    /** Chat, command and inventory keys close the editor and open their screen through the vanilla key handling. */
    static boolean switchScreen(Minecraft minecraft, int keyCode, Runnable close) {
        Options options = minecraft.options;
        for (KeyMapping mapping : new KeyMapping[]{options.keyChat, options.keyCommand, options.keyInventory}) {
            if (matches(mapping, keyCode)) {
                close.run();
                KeyMapping.click(InputConstants.getKey(mapping.saveString()));
                return true;
            }
        }
        return false;
    }

    /** Movement keys never do anything else in the editor. */
    static boolean isMovementKey(Minecraft minecraft, int keyCode) {
        for (KeyMapping mapping : movementKeys(minecraft.options)) {
            if (matches(mapping, keyCode)) {
                return true;
            }
        }
        return false;
    }
}
