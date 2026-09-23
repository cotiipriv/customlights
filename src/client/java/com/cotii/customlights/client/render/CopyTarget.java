package com.cotii.customlights.client.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * A copy of the main target: a color texture and a depth texture on one framebuffer, the same size and depth format as the
 * game's own, so copying into it is a plain blit on every game version.
 */
final class CopyTarget {
    int framebuffer;
    int color;
    int depth;
    int width;
    int height;

    /** Matches the main target's size (and depth format), making the textures again when it changed. */
    void ensure(int width, int height) {
        if (framebuffer != 0 && this.width == width && this.height == height) {
            return;
        }
        this.width = width;
        this.height = height;
        if (framebuffer == 0) {
            framebuffer = Gl.genFramebuffer();
            color = Gl.genTexture();
            depth = Gl.genTexture();
        }
        Gl.bindTexture(0, color);
        setupTexture();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        // The depth copy must have the very format of the game's depth, or blitting into it fails
        Gl.bindTexture(0, Gl.mainDepthTexture());
        int depthFormat = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT);
        Gl.bindTexture(0, depth);
        setupTexture();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, depthFormat, width, height, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (java.nio.ByteBuffer) null);
        Gl.bindTexture(0, 0);
        Gl.bindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        Gl.attachTexture(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, color);
        Gl.attachTexture(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, depth);
    }

    private static void setupTexture() {
        Gl.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        Gl.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        Gl.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
        Gl.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
    }

    /** Copies the main target into this one (color and depth, or only what {@code mask} asks for). */
    void copyFromMain(int mask) {
        Gl.bindFramebuffer(GL30.GL_READ_FRAMEBUFFER, Gl.mainFramebuffer());
        Gl.bindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
        Gl.blit(width, height, mask);
    }
}
