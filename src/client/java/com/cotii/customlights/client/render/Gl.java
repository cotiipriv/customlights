package com.cotii.customlights.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * Every OpenGL call of the light pass that touches state the game remembers (blending, depth, bound textures, programs,
 * framebuffers).
 */
public final class Gl {
    private Gl() {
    }

    // ── Fixed function state
    // ───────────────────────────────────────

    public static void blend(boolean on) {
        if (on) {
            RenderSystem.enableBlend();
        } else {
            RenderSystem.disableBlend();
        }
    }

    /** Light adds up in color; its alpha darkens what was there (ONE, ONE / ONE, ONE_MINUS_SRC_ALPHA). */
    public static void blendAddDarken() {
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
    }

    /** Premultiplied color over what was there (ONE, ONE_MINUS_SRC_ALPHA). */
    public static void blendOver() {
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
    }

    public static void defaultBlend() {
        RenderSystem.defaultBlendFunc();
    }

    public static void depthTest(boolean on) {
        if (on) {
            RenderSystem.enableDepthTest();
        } else {
            RenderSystem.disableDepthTest();
        }
    }

    public static void depthMask(boolean on) {
        RenderSystem.depthMask(on);
    }

    public static void cull(boolean on) {
        if (on) {
            RenderSystem.enableCull();
        } else {
            RenderSystem.disableCull();
        }
    }

    public static void colorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        RenderSystem.colorMask(red, green, blue, alpha);
    }

    public static void scissor(int x, int y, int width, int height) {
        RenderSystem.enableScissor(x, y, width, height);
    }

    public static void noScissor() {
        RenderSystem.disableScissor();
    }

    public static void viewport(int x, int y, int width, int height) {
        GlStateManager._viewport(x, y, width, height);
    }

    public static void clearColor(float red, float green, float blue, float alpha) {
        GlStateManager._clearColor(red, green, blue, alpha);
    }

    // ── Textures, programs, vertex arrays
    // ──────────────────────────

    /** Binds a 2D texture to a texture unit (0 and up), leaving unit 0 active. */
    public static void bindTexture(int unit, int texture) {
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
        GlStateManager._bindTexture(texture);
        if (unit != 0) {
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        }
    }

    public static void activeTexture(int unit) {
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
    }

    public static int genTexture() {
        return GlStateManager._genTexture();
    }

    public static void deleteTexture(int texture) {
        GlStateManager._deleteTexture(texture);
    }

    public static void texParameter(int target, int name, int value) {
        GlStateManager._texParameter(target, name, value);
    }

    public static void useProgram(int program) {
        GlStateManager._glUseProgram(program);
    }

    public static int genVertexArray() {
        return GlStateManager._glGenVertexArrays();
    }

    public static void bindVertexArray(int array) {
        GlStateManager._glBindVertexArray(array);
    }

    /** Leaves no vertex array bound, the way the game expects after the light pass. */
    public static void unbindVertexArray() {
        com.mojang.blaze3d.vertex.VertexBuffer.unbind();
    }

    // ── Framebuffers
    // ───────────────────────────────────────────────

    public static int genFramebuffer() {
        return GlStateManager.glGenFramebuffers();
    }

    public static void bindFramebuffer(int target, int framebuffer) {
        GlStateManager._glBindFramebuffer(target, framebuffer);
    }

    public static void attachTexture(int target, int attachment, int texture) {
        GlStateManager._glFramebufferTexture2D(target, attachment, GL11.GL_TEXTURE_2D, texture, 0);
    }

    public static void blit(int width, int height, int mask) {
        GlStateManager._glBlitFrameBuffer(0, 0, width, height, 0, 0, width, height, mask, GL11.GL_NEAREST);
    }

    // ── The game's main target
    // ─────────────────────────────────────

    private static RenderTarget main() {
        return Minecraft.getInstance().getMainRenderTarget();
    }

    public static int mainWidth() {
        return main().width;
    }

    public static int mainHeight() {
        return main().height;
    }

    public static int mainFramebuffer() {
        return main().frameBufferId;
    }

    public static int mainDepthTexture() {
        return main().getDepthTextureId();
    }

    /** Draws into the main target again (and its viewport when asked). */
    public static void bindMainWrite(boolean setViewport) {
        main().bindWrite(setViewport);
    }
}
