package com.cotii.customlights.client.compat;

import com.cotii.customlights.client.mixin.GuiGraphicsAccessor;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

/**
 * 1.21.6 and later: the screen is drawn later from what was submitted, so the gizmo's quads are gathered and handed over as
 * one element of the screen (already placed with the current pose).
 */
public final class GuiCanvas {
    private final Matrix3x2f pose;
    private float[] points = new float[256];
    private int[] colors = new int[64];
    private int quads;

    private GuiCanvas(Matrix3x2f pose) {
        this.pose = pose;
    }

    public static GuiCanvas begin(GuiGraphics graphics) {
        return new GuiCanvas(new Matrix3x2f(graphics.pose()));
    }

    public void quad(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, int color) {
        if ((quads + 1) * 8 > points.length) {
            points = java.util.Arrays.copyOf(points, points.length * 2);
            colors = java.util.Arrays.copyOf(colors, colors.length * 2);
        }
        int at = quads * 8;
        points[at] = x1;
        points[at + 1] = y1;
        points[at + 2] = x2;
        points[at + 3] = y2;
        points[at + 4] = x3;
        points[at + 5] = y3;
        points[at + 6] = x4;
        points[at + 7] = y4;
        colors[quads] = color;
        quads++;
    }

    public void end(GuiGraphics graphics) {
        if (quads == 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ScreenRectangle screen = new ScreenRectangle(0, 0, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
        ((GuiGraphicsAccessor) graphics).customlights$guiRenderState().submitGuiElement(new Quads(pose, points, colors, quads, screen));
    }

    public static void raise(GuiGraphics graphics, float depth) {
        graphics.pose().pushMatrix();
        graphics.nextStratum();
    }

    public static void lower(GuiGraphics graphics) {
        graphics.pose().popMatrix();
    }

    /** The gathered quads as one screen element; each is drawn in both windings. */
    private record Quads(Matrix3x2f pose, float[] points, int[] colors, int count, ScreenRectangle bounds) implements GuiElementRenderState {
        @Override
        public void buildVertices(VertexConsumer consumer, float depth) {
            Vector2f point = new Vector2f();
            for (int quad = 0; quad < count; quad++) {
                int at = quad * 8;
                int color = colors[quad];
                for (int corner : new int[]{0, 1, 2, 3, 3, 2, 1, 0}) {
                    pose.transformPosition(points[at + corner * 2], points[at + corner * 2 + 1], point);
                    consumer.addVertex(point.x, point.y, depth).setColor(color);
                }
            }
        }

        @Override
        public RenderPipeline pipeline() {
            return RenderPipelines.GUI;
        }

        @Override
        public TextureSetup textureSetup() {
            return TextureSetup.noTexture();
        }

        @Override
        public ScreenRectangle scissorArea() {
            return null;
        }
    }
}
