package com.cotii.customlights.client.render;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

/**
 * 1.21.5 and later: the game draws through render pipelines on top of its own OpenGL state manager, and its targets are GPU
 * textures without a framebuffer of their own.
 */
public final class Gl {
    private static int mainFramebuffer;
    private static int mainColorId;
    private static int mainDepthId;

    private Gl() {
    }

    // ── Fixed function state
    // ───────────────────────────────────────

    public static void blend(boolean on) {
        if (on) {
            GlStateManager._enableBlend();
        } else {
            GlStateManager._disableBlend();
        }
    }

    public static void blendAddDarken() {
        GlStateManager._blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    public static void blendOver() {
        GlStateManager._blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    public static void defaultBlend() {
        GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
    }

    public static void depthTest(boolean on) {
        if (on) {
            GlStateManager._enableDepthTest();
        } else {
            GlStateManager._disableDepthTest();
        }
    }

    public static void depthMask(boolean on) {
        GlStateManager._depthMask(on);
    }

    public static void cull(boolean on) {
        if (on) {
            GlStateManager._enableCull();
        } else {
            GlStateManager._disableCull();
        }
    }

    public static void colorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        GlStateManager._colorMask(red, green, blue, alpha);
    }

    public static void scissor(int x, int y, int width, int height) {
        GlStateManager._enableScissorTest();
        GlStateManager._scissorBox(x, y, width, height);
    }

    public static void noScissor() {
        GlStateManager._disableScissorTest();
    }

    public static void viewport(int x, int y, int width, int height) {
        GlStateManager._viewport(x, y, width, height);
    }

    public static void clearColor(float red, float green, float blue, float alpha) {
        GL11.glClearColor(red, green, blue, alpha);
    }

    // ── Textures, programs, vertex arrays
    // ──────────────────────────

    public static void bindTexture(int unit, int texture) {
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
        GlStateManager._bindTexture(texture);
        // 1.21.11 and later leave sampler objects on the units, and those would override our textures' filtering
        GL33.glBindSampler(unit, 0);
        if (unit != 0) {
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        }
    }

    public static void activeTexture(int unit) {
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
        GL33.glBindSampler(unit, 0);
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

    public static void unbindVertexArray() {
        GlStateManager._glBindVertexArray(0);
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

    /**
     * The name OpenGL knows a texture by. Some loaders hand out wrapped textures in development (NeoForge puts a
     * validation layer around them), so the real one is asked for until a plain GL texture comes out.
     */
    private static int glId(com.mojang.blaze3d.textures.GpuTexture texture) {
        Object current = texture;
        for (int i = 0; i < 4 && current != null; i++) {
            if (current instanceof GlTexture plain) {
                return plain.glId();
            }
            try {
                current = current.getClass().getMethod("getRealTexture").invoke(current);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return 0;
            }
        }
        return 0;
    }

    /** A framebuffer of our own on the main target's textures (made again when the game remakes them). */
    public static int mainFramebuffer() {
        RenderTarget main = main();
        int color = glId(main.getColorTexture());
        int depth = main.getDepthTexture() == null ? 0 : glId(main.getDepthTexture());
        if (mainFramebuffer == 0 || color != mainColorId || depth != mainDepthId) {
            if (mainFramebuffer == 0) {
                mainFramebuffer = GlStateManager.glGenFramebuffers();
            }
            mainColorId = color;
            mainDepthId = depth;
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, mainFramebuffer);
            GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, color, 0);
            GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depth, 0);
        }
        return mainFramebuffer;
    }

    public static int mainDepthTexture() {
        RenderTarget main = main();
        return main.getDepthTexture() == null ? 0 : glId(main.getDepthTexture());
    }

    public static void bindMainWrite(boolean setViewport) {
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, mainFramebuffer());
        if (setViewport) {
            GlStateManager._viewport(0, 0, mainWidth(), mainHeight());
        }
    }
}
