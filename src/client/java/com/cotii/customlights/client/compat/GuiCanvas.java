package com.cotii.customlights.client.compat;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;

/** Free-form quads in the editor (the gizmo), and lifting a popup above the rest of the screen. */
public final class GuiCanvas {
    private final VertexConsumer buffer;
    private final Matrix4f pose;

    private GuiCanvas(VertexConsumer buffer, Matrix4f pose) {
        this.buffer = buffer;
        this.pose = pose;
    }

    public static GuiCanvas begin(GuiGraphics graphics) {
        return new GuiCanvas(Compat.guiBuffer(graphics), new Matrix4f(graphics.pose().last().pose()));
    }

    /** A filled quad; both windings are drawn, so it shows whichever way its corners go. */
    public void quad(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, int color) {
        Compat.vertex(buffer, pose, x1, y1, 0.0f, color);
        Compat.vertex(buffer, pose, x2, y2, 0.0f, color);
        Compat.vertex(buffer, pose, x3, y3, 0.0f, color);
        Compat.vertex(buffer, pose, x4, y4, 0.0f, color);
        Compat.vertex(buffer, pose, x4, y4, 0.0f, color);
        Compat.vertex(buffer, pose, x3, y3, 0.0f, color);
        Compat.vertex(buffer, pose, x2, y2, 0.0f, color);
        Compat.vertex(buffer, pose, x1, y1, 0.0f, color);
    }

    public void end(GuiGraphics graphics) {
        graphics.flush();
    }

    /** What is drawn next goes above everything drawn so far (until {@link #lower}). */
    public static void raise(GuiGraphics graphics, float depth) {
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, 0.0f, depth);
    }

    public static void lower(GuiGraphics graphics) {
        graphics.flush();
        graphics.pose().popPose();
    }
}
