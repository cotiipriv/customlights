package com.cotii.customlights.client.gui;

import net.minecraft.client.Minecraft;

/** Opens the editor from its key or from {@code /customlight editor [id]} (once the chat screen has closed). */
public final class EditorOpener {
    private static boolean requested;
    private static String requestedId;

    private EditorOpener() {
    }

    public static void request(String id) {
        requested = true;
        requestedId = id;
    }

    public static void tick(Minecraft minecraft) {
        boolean key = false;
        while (EditorKeys.OPEN.consumeClick()) {
            key = true;
        }
        if (minecraft.player == null) {
            requested = false;
            return;
        }
        if ((key || requested) && minecraft.screen == null) {
            if (requestedId != null) {
                LightEditor.loadForEditing(requestedId);
            }
            requested = false;
            requestedId = null;
            minecraft.setScreen(new LightEditorScreen());
        }
    }
}
