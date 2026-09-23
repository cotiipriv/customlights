package com.cotii.customlights.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;

/** Typing and walking as a player would, for the development test bot only (see DevAutoTest). */
public final class DevInput {
    private DevInput() {
    }

    public static void openChat(Minecraft minecraft) {
        minecraft.setScreen(new ChatScreen(""));
    }

    public static void typeChar(Minecraft minecraft, char character) {
        minecraft.screen.charTyped(character, 0);
    }

    public static void pressKey(Minecraft minecraft, int key, int modifiers) {
        minecraft.screen.keyPressed(key, 0, modifiers);
    }

    /** As if walking: the view bobs as much as it can. */
    public static void bob(LocalPlayer player) {
        player.walkDist = 0.25f;
        player.walkDistO = 0.25f;
        player.bob = 0.1f;
        player.oBob = 0.1f;
    }
}
