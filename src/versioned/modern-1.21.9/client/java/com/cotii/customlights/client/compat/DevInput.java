package com.cotii.customlights.client.compat;

import com.cotii.customlights.client.mixin.ClientAvatarStateAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;

/** 1.21.9 and later: input arrives as events, and the walking sway lives in the player's avatar state. */
public final class DevInput {
    private DevInput() {
    }

    public static void openChat(Minecraft minecraft) {
        minecraft.setScreen(new ChatScreen("", false));
    }

    public static void typeChar(Minecraft minecraft, char character) {
        minecraft.screen.charTyped(new CharacterEvent(character, 0));
    }

    public static void pressKey(Minecraft minecraft, int key, int modifiers) {
        minecraft.screen.keyPressed(new KeyEvent(key, 0, modifiers));
    }

    /** As if walking: the view bobs as much as it can. */
    public static void bob(LocalPlayer player) {
        ClientAvatarStateAccessor state = (ClientAvatarStateAccessor) player.avatarState();
        state.customlights$setWalkDist(0.25f);
        state.customlights$setWalkDistO(0.25f);
        state.customlights$setBob(0.1f);
        state.customlights$setBobO(0.1f);
    }
}
