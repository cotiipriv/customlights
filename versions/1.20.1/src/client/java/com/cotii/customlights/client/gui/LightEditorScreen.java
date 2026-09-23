package com.cotii.customlights.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 1.21.1 screen adapter for {@link LightEditor}. */
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
    public void renderBackground(GuiGraphics graphics) {
        // No blur or darkening: the world behind shows the live preview
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        editor.render(graphics, font, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return editor.mouseClicked(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return editor.mouseReleased(button) || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return editor.mouseDragged(mouseX, mouseY, button, deltaX, deltaY) || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        return editor.mouseScrolled(mouseX, mouseY, verticalAmount) || super.mouseScrolled(mouseX, mouseY, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return editor.keyPressed(keyCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return editor.charTyped(chr) || super.charTyped(chr, modifiers);
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
