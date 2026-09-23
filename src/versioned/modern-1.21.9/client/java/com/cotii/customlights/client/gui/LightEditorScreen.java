package com.cotii.customlights.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Screen adapter for {@link LightEditor}, 1.21.9 and later (input arrives as events). */
public class LightEditorScreen extends Screen {
    private final LightEditor editor = new LightEditor(this::onClose);

    public LightEditorScreen() {
        super(Component.literal("CustomLights Editor"));
    }

    @Override
    protected void init() {
        editor.resize(width, height);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // No blur or darkening: the world behind shows the live preview
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        editor.render(graphics, font, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return editor.mouseClicked(event.x(), event.y(), event.button()) || super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return editor.mouseReleased(event.button()) || super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        return editor.mouseDragged(event.x(), event.y(), event.button(), deltaX, deltaY) || super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return editor.mouseScrolled(mouseX, mouseY, verticalAmount) || super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return editor.keyPressed(event.key(), event.modifiers()) || super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        // The editor's text fields take one character at a time
        boolean handled = false;
        for (char chr : Character.toChars(event.codepoint())) {
            handled |= editor.charTyped(chr);
        }
        return handled || super.charTyped(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        editor.tick();
    }

    @Override
    public void removed() {
        editor.onClosed();
        super.removed();
    }
}
