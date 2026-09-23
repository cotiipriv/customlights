package com.cotii.customlights.client.render;

import com.cotii.customlights.client.light.LightManager;
import com.cotii.customlights.client.light.MarkerLights;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.joml.FrustumIntersection;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

/** Screen-space colored lights for 1.21.1. */
public final class LightRenderer {
    public static final int MAX_LIGHTS = 256;

    /** How much the lights may cost. */
    public enum Quality {
        LOW(12, 96, 8, 4, 8, 4.0f),
        MEDIUM(16, 160, 16, 2, 4, 8.0f),
        HIGH(24, MAX_LIGHTS, 32, 2, 4, 12.0f),
        ULTRA(40, MAX_LIGHTS, 64, 1, 2, 24.0f);

        /**
         * Atmosphere samples, lights and hazy lights at most, and how much smaller than the screen the light and atmosphere
         * targets are.
         */
        final int steps, lights, hazyLights, lightDivisor, hazeDivisor;
        final float screens;

        Quality(int steps, int lights, int hazyLights, int lightDivisor, int hazeDivisor, float screens) {
            this.screens = screens;
            this.steps = steps;
            this.lights = lights;
            this.hazyLights = hazyLights;
            this.lightDivisor = lightDivisor;
            this.hazeDivisor = hazeDivisor;
        }
    }

    /** Loads the saved quality level. */
    public static void loadSettings() {
        com.google.gson.JsonObject settings = com.cotii.customlights.client.storage.LightStorage.loadSettings();
        if (settings != null && settings.has("quality")) {
            try {
                quality = Quality.valueOf(settings.get("quality").getAsString().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public static void setQuality(Quality level) {
        quality = level;
        com.google.gson.JsonObject settings = new com.google.gson.JsonObject();
        settings.addProperty("quality", level.name().toLowerCase(java.util.Locale.ROOT));
        com.cotii.customlights.client.storage.LightStorage.saveSettings(settings);
    }

    /** Lights smaller than this on screen (pixels of radius) are not drawn. */
    private static final float MIN_PIXELS = 1.0f;
    /** Their atmosphere is skipped below this. */
    private static final float MIN_HAZE_PIXELS = 4.0f;
    /** Above this many hazy lights, each gets fewer samples. */
    private static final int HAZE_SHARE = 8;

    private static final LightFrame FRAME = new LightFrame();
    private static final Matrix4f WORLD_PROJECTION = new Matrix4f();
    private static final Matrix4f VIEW_ROTATION = new Matrix4f();
    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    private static final Matrix4f INVERSE = new Matrix4f();
    private static final Matrix3f VIEW_TO_WORLD = new Matrix3f();
    private static final FrustumIntersection FRUSTUM = new FrustumIntersection();
    private static final Vector4f CORNER = new Vector4f();
    private static final int[] SCISSOR = new int[4];
    private static final float[] POINT = new float[3];
    private static final Vector4f EYE = new Vector4f();
    private static final Matrix4f EYE_INVERSE = new Matrix4f();

    private static LightShader gbufferShader;
    private static LightShader surfaceShader;
    private static LightShader atmosphereShader;
    private static LightShader compositeShader;
    private static boolean shaderFailed;

    /** True when the mod could not build its own shaders (the lights are off). */
    public static boolean shaderFailed() {
        return shaderFailed;
    }
    private static final CopyTarget worldDepth = new CopyTarget();
    private static final CopyTarget sceneCopy = new CopyTarget();
    private static int vertexArray;
    private static final FloatTarget GBUFFER = new FloatTarget(GL30.GL_RGBA32F, GL11.GL_NEAREST);
    // Linear so the composite can tell with one read whether a pixel has any light nearby
    private static final FloatTarget LIGHTING = new FloatTarget(GL30.GL_RGBA16F, GL11.GL_LINEAR);
    private static final FloatTarget HAZE = new FloatTarget(GL30.GL_RGBA16F, GL11.GL_LINEAR);

    private static Camera camera;
    private static float partialTick;
    private static boolean worldCaptured;
    private static boolean handDrawn;
    private static Matrix4f handProjection = new Matrix4f();
    /** The latest world view, for the editor's gizmo. */
    private static final Matrix4f LAST_VIEW_PROJECTION = new Matrix4f();
    private static boolean hasLastView;

    /** Toggled with {@code /customlight toggle}. */
    public static boolean enabled = true;
    public static Quality quality = Quality.MEDIUM;
    /** Lights drawn in the last frame (and how many had atmosphere), for {@code /customlight stats}. */
    public static int lastDrawn;
    public static int lastHazy;
    public static int lastCollected;
    /** Development: 2 skips the atmosphere, 4 the surfaces, 8 the G-buffer. */
    public static int debugSkip;
    /** Development: logs the lights of the next frame. */
    public static int traceFrames;
    public static boolean probeDepth;
    public static boolean probeHand;

    public static boolean debugDump;

    private LightRenderer() {
    }

    /** Camera-relative world position to clip space of the last frame, or false when unknown. */
    public static boolean project(float x, float y, float z, Vector4f out) {
        if (!hasLastView) {
            return false;
        }
        out.set(x, y, z, 1.0f);
        LAST_VIEW_PROJECTION.transform(out);
        return true;
    }

    public static Matrix4f lastViewProjection() {
        return hasLastView ? LAST_VIEW_PROJECTION : null;
    }

    /** Right before the world is drawn; the camera is set up. */
    public static void beginFrame(Camera frameCamera, float partial) {
        camera = frameCamera;
        partialTick = partial;
        worldCaptured = false;
        handDrawn = false;
        MarkerLights.beginFrame(frameCamera.getPosition(), frameCamera.rotation(), partial);
    }

    /** Camera position the last frame's lights are relative to. */
    private static net.minecraft.world.phys.Vec3 lastCameraPos = net.minecraft.world.phys.Vec3.ZERO;

    /** Whether the last frame had a light within {@code radius} of a world position (for the development tests). */
    public static boolean lightNear(double x, double y, double z, double radius) {
        for (int i = 0; i < FRAME.size(); i++) {
            RenderLight light = FRAME.get(i);
            double dx = lastCameraPos.x + light.x - x;
            double dy = lastCameraPos.y + light.y - y;
            double dz = lastCameraPos.z + light.z - z;
            if (dx * dx + dy * dy + dz * dz <= radius * radius) {
                return true;
            }
        }
        return false;
    }

    /** The world is drawn and the main target still holds its depth. */
    public static void afterWorld(Matrix4f viewRotation, Matrix4f projection) {
        VIEW_ROTATION.set(viewRotation);
        WORLD_PROJECTION.set(projection);
        LAST_VIEW_PROJECTION.set(projection).mul(viewRotation);
        hasLastView = true;
        if (!enabled || shaderFailed || camera == null) {
            return;
        }
        worldDepth.ensure(Gl.mainWidth(), Gl.mainHeight());
        worldDepth.copyFromMain(GL11.GL_DEPTH_BUFFER_BIT);
        captureSections();
        Gl.bindMainWrite(false);
        worldCaptured = true;
    }

    /** The main target's depth was cleared and the first person hand is about to be drawn into it. */
    public static void beforeHand() {
        // Shader packs draw the hand inside their own pipeline: the depth left in the main target is not the hand's
        handDrawn = !ShaderCompat.shaderPackInUse();
    }

    /** End of the level pass (hand included): adds the lights. */
    public static void afterHand(Matrix4f projection) {
        FRAME.clear();
        lastDrawn = 0;
        lastHazy = 0;
        if (!worldCaptured) {
            return;
        }
        worldCaptured = false;
        handProjection = handDrawn && projection != null ? new Matrix4f(projection) : new Matrix4f(WORLD_PROJECTION);
        if (probeHand) {
            probeHand = false;
            com.cotii.customlights.Customlights.LOGGER.info("[devtest] probe shader pack {} hand drawn {} hand projection {} world {}", ShaderCompat.shaderPackInUse(), handDrawn, projection, WORLD_PROJECTION);
        }
        LightManager.collect(FRAME, camera, partialTick);
        lastCameraPos = camera.getPosition();
        lastCollected = FRAME.size();
        if (FRAME.size() == 0 || !ensureShaders()) {
            return;
        }
        int width = Gl.mainWidth();
        int height = Gl.mainHeight();
        VIEW_PROJECTION.set(WORLD_PROJECTION).mul(VIEW_ROTATION);
        FRUSTUM.set(VIEW_PROJECTION);
        int collectedNow = lastCollected;
        int hazy = prepareLights(width, height);
        if (traceFrames > 0) {
            traceFrames--;
            StringBuilder small = new StringBuilder();
            for (int i = 0; i < FRAME.size(); i++) {
                RenderLight each = FRAME.get(i);
                if (each.radius < 0.2f) small.append(String.format(" %.2f,%.2f,%.2f i=%.2f", each.x, each.y, each.z, each.intensity));
            }
            com.cotii.customlights.Customlights.LOGGER.info("[devtest] trace {} collected {} kept{}", collectedNow, FRAME.size(), small);
        }
        if (debugDump) {
            debugDump = false;
            com.cotii.customlights.Customlights.LOGGER.info("[devtest] frame: {} collected, {} kept, projection {}", lastCollected, FRAME.size(), WORLD_PROJECTION);
            for (int i = 0; i < FRAME.size(); i++) {
                RenderLight light = FRAME.get(i);
                com.cotii.customlights.Customlights.LOGGER.info("[devtest] light {} at {} {} {} r={} d={} i={} a={} rgb={},{},{} bound={} steps={}", light.shape, light.x, light.y,
                        light.z, light.radius, light.distance, light.intensity, light.atmosphere, light.red, light.green, light.blue, light.boundRadius,
                        light.hazeSteps);
            }
        }
        if (FRAME.size() == 0) {
            return;
        }

        checkErrors("before the lights");
        GpuTimer.beginFrame();
        OcclusionGrids.beginFrame();
        checkErrors("timer start");
        GpuTimer.begin(GpuTimer.Pass.COPY);
        sceneCopy.ensure(width, height);
        sceneCopy.copyFromMain(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        Gl.depthTest(false);
        Gl.depthMask(false);
        Gl.cull(false);
        Gl.bindVertexArray(vertexArray);

        // Everything but the final composite runs at a lower resolution, except when a light is stopped by blocks (its
        // shadow has to land on the exact pixel the block covers) or when one of the shapes that move by themselves is
        // on screen: their thin, moving edges show every missing pixel, so those frames light at full resolution
        int lightDivisor = quality.lightDivisor;
        for (int i = 0; i < FRAME.size() && lightDivisor > 1; i++) {
            RenderLight light = FRAME.get(i);
            if (castsShadows(light) || light.shape.animated()) {
                lightDivisor = 1;
            }
        }
        int lowW = divide(width, lightDivisor);
        int lowH = divide(height, lightDivisor);

        checkErrors("copy");
        // 1.
        GpuTimer.begin(GpuTimer.Pass.GBUFFER);
        Gl.blend(false);
        Gl.colorMask(true, true, true, true);
        GBUFFER.ensure(lowW, lowH);
        GBUFFER.bindAndClear(lowW, lowH);
        bindTexture(4, 0);
        Gl.useProgram(gbufferShader.program);
        bindTexture(1, worldDepth.depth);
        bindTexture(2, sceneCopy.depth);
        sceneUniforms(gbufferShader, width, height);
        gbufferShader.set2f("TargetSize", lowW, lowH);
        gbufferShader.set1i("Divisor", lightDivisor);
        gbufferShader.set1i("WorldDepth", 1);
        gbufferShader.set1i("HandDepth", 2);
        gbufferShader.set1i("HasHand", handDrawn ? 1 : 0);
        if ((debugSkip & 8) == 0) {
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        }
        bindTexture(4, GBUFFER.texture);

        checkErrors("gbuffer");
        // 2.
        GpuTimer.begin(GpuTimer.Pass.SURFACES);
        Gl.blend(true);
        LIGHTING.ensure(lowW, lowH);
        LIGHTING.bindAndClear(lowW, lowH);
        // Light adds up and darkness combines on its own, so the order the lights are drawn in never matters
        Gl.blendAddDarken();
        Gl.useProgram(surfaceShader.program);
        sceneUniforms(surfaceShader, width, height);
        surfaceShader.set2f("TargetSize", lowW, lowH);
        for (int i = 0; i < FRAME.size(); i++) {
            RenderLight light = FRAME.get(i);
            if ((debugSkip & 4) != 0 || !scissor(light, lowW, lowH, 1)) {
                continue;
            }
            Gl.scissor(SCISSOR[0], SCISSOR[1], SCISSOR[2], SCISSOR[3]);
            lightUniforms(surfaceShader, light);
            occlusionUniforms(surfaceShader, light);
            surfaceShader.set4f("LightColor", light.red * light.surfaceGain, light.green * light.surfaceGain, light.blue * light.surfaceGain, light.intensity);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            lastDrawn++;
        }
        Gl.noScissor();

        checkErrors("surfaces");
        // 3.
        boolean haze = hazy > 0 && (debugSkip & 2) == 0;
        // The atmosphere is very soft: it can be smaller still
        // Luminosity and the shapes that move by themselves both show every step of the haze, so it is drawn finer
        // while one of them is on screen
        int hazeDivisor = quality.hazeDivisor;
        for (int i = 0; i < FRAME.size() && hazeDivisor > lightDivisor; i++) {
            RenderLight light = FRAME.get(i);
            if (light.luminosity > 0.0f || light.shape.animated()) {
                hazeDivisor = lightDivisor;
            }
        }
        int hazeW = divide(width, hazeDivisor);
        int hazeH = divide(height, hazeDivisor);
        if (haze) {
            GpuTimer.begin(GpuTimer.Pass.ATMOSPHERE);
            HAZE.ensure(hazeW, hazeH);
            HAZE.bindAndClear(hazeW, hazeH);
            Gl.blendAddDarken();
            Gl.useProgram(atmosphereShader.program);
            sceneUniforms(atmosphereShader, width, height);
            atmosphereShader.set2f("TargetSize", hazeW, hazeH);
            atmosphereShader.set1f("FarDistance", Minecraft.getInstance().gameRenderer.getRenderDistance());
            for (int i = 0; i < FRAME.size(); i++) {
                RenderLight light = FRAME.get(i);
                if (light.hazeSteps <= 0 || !scissor(light, hazeW, hazeH, 2)) {
                    continue;
                }
                Gl.scissor(SCISSOR[0], SCISSOR[1], SCISSOR[2], SCISSOR[3]);
                lightUniforms(atmosphereShader, light);
                occlusionUniforms(atmosphereShader, light);
                atmosphereShader.set1i("AtmosphereSteps", light.hazeSteps);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
                lastHazy++;
            }
            Gl.noScissor();
        }

        checkErrors("atmosphere");
        // 4.
        if (lastDrawn > 0 || lastHazy > 0) {
            GpuTimer.begin(GpuTimer.Pass.COMPOSITE);
            Gl.bindMainWrite(true);
            Gl.colorMask(true, true, true, false);
            Gl.blendOver();
            Gl.blend(false);
            Gl.useProgram(compositeShader.program);
            sceneUniforms(compositeShader, width, height);
            bindTexture(0, sceneCopy.color);
            bindTexture(1, worldDepth.depth);
            bindTexture(2, sceneCopy.depth);
            bindTexture(3, LIGHTING.texture);
            bindTexture(5, haze ? HAZE.texture : 0);
            compositeShader.set1f("AreaLight", cameraLight);
            compositeShader.set1i("SceneColor", 0);
            compositeShader.set1i("WorldDepth", 1);
            compositeShader.set1i("HandDepth", 2);
            compositeShader.set1i("Lighting", 3);
            compositeShader.set1i("Atmosphere", 5);
            compositeShader.set1i("HasHand", handDrawn ? 1 : 0);
            compositeShader.set1i("HasAtmosphere", haze ? 1 : 0);
            compositeShader.set2f("TargetSize", lowW, lowH);
            compositeShader.set2f("HazeSize", hazeW, hazeH);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        }

        checkErrors("composite");
        GpuTimer.endFrame();
        checkErrors("timer end");
        Gl.unbindVertexArray();
        for (int unit = 6; unit >= 0; unit--) {
            bindTexture(unit, 0);
        }
        Gl.useProgram(0);
        com.cotii.customlights.client.compat.Compat.forgetBoundShader();
        Gl.bindMainWrite(true);

        Gl.colorMask(true, true, true, true);
        Gl.defaultBlend();
        Gl.blend(false);
        Gl.cull(true);
        Gl.depthMask(true);
        Gl.depthTest(true);
    }

    private static boolean ensureShaders() {
        if (shaderFailed) {
            return false;
        }
        if (surfaceShader == null) {
            gbufferShader = LightShader.create("gbuffer.fsh");
            surfaceShader = LightShader.create("surface.fsh");
            atmosphereShader = LightShader.create("atmosphere.fsh");
            compositeShader = LightShader.create("composite.fsh");
            if (gbufferShader == null || surfaceShader == null || atmosphereShader == null || compositeShader == null) {
                shaderFailed = true;
                return false;
            }
            vertexArray = Gl.genVertexArray();
        }
        return true;
    }

    private static final long STARTED = System.nanoTime();

    /** Seconds since the game started, wrapped so it never grows too big for a float. */
    private static float time() {
        return (float) ((System.nanoTime() - STARTED) / 1.0e9d % 3600.0d);
    }

    private static void sceneUniforms(LightShader shader, int width, int height) {
        shader.set1i("GBuffer", 4);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer matrix = stack.mallocFloat(16);
            WORLD_PROJECTION.invert(INVERSE).get(matrix);
            shader.setMatrix("InvWorldProjection", matrix, true);
            handProjection.invert(INVERSE).get(matrix);
            shader.setMatrix("InvHandProjection", matrix, true);
            eye(WORLD_PROJECTION, EYE);
            shader.set3f("WorldEye", EYE.x, EYE.y, EYE.z);
            eye(handProjection, EYE);
            shader.set3f("HandEye", EYE.x, EYE.y, EYE.z);
            FloatBuffer matrix3 = stack.mallocFloat(9);
            VIEW_ROTATION.get3x3(VIEW_TO_WORLD).transpose();
            VIEW_TO_WORLD.get(matrix3);
            shader.setMatrix("ViewToWorld", matrix3, false);
        }
        shader.set2f("ScreenSize", width, height);
        shader.set1f("Time", time());
        shader.set1i("Occupancy", 7);
    }

    /** Whether this light is stopped by blocks (lights swept over a group of objects have no single point to trace to). */
    private static boolean castsShadows(RenderLight light) {
        return !light.seeThrough && light.extentX == 0.0f && light.extentY == 0.0f && light.extentZ == 0.0f;
    }

    /** Lights that do not pass through blocks get the grid of solid blocks around them (unit 7). */
    private static void occlusionUniforms(LightShader shader, RenderLight light) {
        OcclusionGrids.Grid grid = null;
        net.minecraft.world.phys.Vec3 origin = camera.getPosition();
        if (castsShadows(light)) {
            grid = OcclusionGrids.gridFor(origin.x + light.x, origin.y + light.y, origin.z + light.z, light.boundRadius);
        }
        Gl.activeTexture(7);
        GL11.glBindTexture(org.lwjgl.opengl.GL12.GL_TEXTURE_3D, grid == null ? OcclusionGrids.emptyTexture() : grid.texture);
        Gl.activeTexture(0);
        shader.set1i("Occluded", grid == null ? 0 : 1);
        if (grid != null) {
            shader.set3f("OccupancyOrigin", (float) (grid.minX - origin.x), (float) (grid.minY - origin.y), (float) (grid.minZ - origin.z));
            shader.set3f("OccupancySize", grid.size, grid.size, grid.size);
        }
    }

    private static void lightUniforms(LightShader shader, RenderLight light) {
        shader.set4f("LightPos", light.x, light.y, light.z, Math.max(0.0f, light.radius));
        shader.set4f("LightColor", light.red, light.green, light.blue, light.intensity);
        shader.set4f("LightParams", Math.max(0.0f, light.distance), Math.max(0.0f, Math.min(1.0f, light.atmosphere / 10.0f)), light.shape.shaderId,
                light.shape.spread(light.angle));
        shader.setMatrix3("LightBasis", light.basis);
        shader.set3f("LightStretch", light.stretchX, light.stretchY, light.stretchZ);
        shader.set3f("LightPivot", light.pivotX, light.pivotY, light.pivotZ);
        shader.set1f("LightPhase", light.phase);
        shader.set3f("LightExtent", light.extentX, light.extentY, light.extentZ);
        shader.set4f("LightBound", light.boundCenterX, light.boundCenterY, light.boundCenterZ, light.boundRadius);
        shader.set1f("Luminosity", Math.max(0.0f, light.luminosity));
    }

    /**
     * Drops lights that do nothing, are outside the view or too small to see, keeps the closest the quality allows and gives
     * the nearest hazy ones their atmosphere detail.
     */
    private static int prepareLights(int width, int height) {
        // Sizes in pixels of the light target
        float pixelScale = WORLD_PROJECTION.m11() * height * 0.5f / quality.lightDivisor;
        for (int i = FRAME.size() - 1; i >= 0; i--) {
            RenderLight light = FRAME.get(i);
            if (!light.isVisible()) {
                FRAME.removeAt(i);
                continue;
            }
            light.computeBounds();
            if (!FRUSTUM.testSphere(light.boundCenterX, light.boundCenterY, light.boundCenterZ, light.boundRadius)) {
                FRAME.removeAt(i);
                continue;
            }
            double centerDistance = Math.sqrt(light.boundCenterX * light.boundCenterX + light.boundCenterY * light.boundCenterY + light.boundCenterZ * light.boundCenterZ);
            light.sortDistance = Math.max(0.0d, centerDistance - light.boundRadius);
            light.importance = centerDistance <= light.boundRadius ? Float.MAX_VALUE
                    : (float) (light.boundRadius * pixelScale / centerDistance);
            if (light.importance < MIN_PIXELS || !inVisibleSections(light)) {
                FRAME.removeAt(i);
            }
        }
        FRAME.sortByDistance();
        vanillaLights();
        int limit = Math.min(MAX_LIGHTS, quality.lights);
        // Every visible light is drawn; when together they cover the screen many times over, their atmosphere gets
        // fewer samples instead
        double screenArea = (double) divide(width, quality.lightDivisor) * divide(height, quality.lightDivisor);
        double covered = 0.0d;
        for (int i = 0; i < FRAME.size() && i < limit; i++) {
            RenderLight light = FRAME.get(i);
            covered += Math.min(1.0d, Math.PI * light.importance * light.importance / screenArea);
        }
        while (FRAME.size() > limit) {
            FRAME.removeAt(FRAME.size() - 1);
        }
        int hazy = 0;
        for (int i = 0; i < FRAME.size(); i++) {
            RenderLight light = FRAME.get(i);
            light.hazeSteps = 0;
            boolean glows = light.atmosphere > 0.0f || light.luminosity > 0.0f;
            if (glows && light.importance >= MIN_HAZE_PIXELS && hazy < quality.hazyLights) {
                hazy++;
                light.hazeSteps = -1;
            }
        }
        if (hazy == 0) {
            return 0;
        }
        // Many hazy lights share the budget; small ones on screen need fewer samples
        float share = hazy > HAZE_SHARE ? (float) Math.sqrt(HAZE_SHARE / (double) hazy) : 1.0f;
        if (covered > quality.screens) {
            share *= (float) Math.sqrt(quality.screens / covered);
        }
        for (int i = 0; i < FRAME.size(); i++) {
            RenderLight light = FRAME.get(i);
            if (light.hazeSteps == 0) {
                continue;
            }
            float size = Math.min(1.0f, 0.35f + light.importance / 100.0f);
            light.hazeSteps = Math.max(6, Math.round(quality.steps * size * share * (light.shape.animated() ? 2.0f : 1.0f)));
        }
        return hazy;
    }

    private static final it.unimi.dsi.fastutil.longs.LongOpenHashSet VISIBLE_SECTIONS = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
    private static boolean sectionsKnown;

    /** Remembers which 16-block sections the game draws this frame (after its frustum and cave culling). */
    private static final java.lang.invoke.MethodHandle SODIUM_RENDERER;
    private static final java.lang.invoke.MethodHandle SODIUM_BOX_VISIBLE;
    private static Object sodiumRenderer;

    static {
        java.lang.invoke.MethodHandle renderer = null;
        java.lang.invoke.MethodHandle boxVisible = null;
        // Sodium 0.6 and later, Sodium 0.5 (1.20.x) and Embeddium (its Forge port) keep the renderer in different places
        String[] rendererClasses = {"net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer",
                "me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer", "org.embeddedt.embeddium.impl.render.EmbeddiumWorldRenderer"};
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium")) {
            for (String className : rendererClasses) {
                try {
                    Class<?> type = Class.forName(className);
                    var lookup = java.lang.invoke.MethodHandles.publicLookup();
                    renderer = lookup.findStatic(type, "instanceNullable", java.lang.invoke.MethodType.methodType(type)).asType(java.lang.invoke.MethodType.methodType(Object.class));
                    boxVisible = lookup.findVirtual(type, "isBoxVisible", java.lang.invoke.MethodType.methodType(boolean.class, double.class, double.class,
                            double.class, double.class, double.class, double.class)).asType(java.lang.invoke.MethodType.methodType(boolean.class, Object.class,
                            double.class, double.class, double.class, double.class, double.class, double.class));
                    com.cotii.customlights.Customlights.LOGGER.info("CustomLights: light culling follows {}", className);
                    break;
                } catch (ReflectiveOperationException | RuntimeException failed) {
                    renderer = null;
                    boxVisible = null;
                }
            }
        }
        SODIUM_RENDERER = boxVisible == null ? null : renderer;
        SODIUM_BOX_VISIBLE = boxVisible;
    }

    private static void captureSections() {
        VISIBLE_SECTIONS.clear();
        if (SODIUM_RENDERER != null) {
            // Sodium draws the chunks itself and answers visibility questions directly
            try {
                sodiumRenderer = SODIUM_RENDERER.invoke();
            } catch (Throwable failed) {
                sodiumRenderer = null;
            }
            sectionsKnown = sodiumRenderer != null;
            return;
        }
        sectionsKnown = com.cotii.customlights.client.compat.VisibleSections.fill(VISIBLE_SECTIONS);
    }

    /**
     * Whether any section the light reaches is drawn this frame: lights shut away behind walls or underground (in sections
     * the game culls) are skipped.
     */
    private static boolean inVisibleSections(RenderLight light) {
        if (!sectionsKnown || camera == null || light.importance == Float.MAX_VALUE || light.boundRadius > 40.0f) {
            return true;
        }
        net.minecraft.world.phys.Vec3 eye = camera.getPosition();
        double x = eye.x + light.boundCenterX;
        double y = eye.y + light.boundCenterY;
        double z = eye.z + light.boundCenterZ;
        double r = light.boundRadius;
        if (sodiumRenderer != null) {
            try {
                return (boolean) SODIUM_BOX_VISIBLE.invoke(sodiumRenderer, x - r, y - r, z - r, x + r, y + r, z + r);
            } catch (Throwable failed) {
                return true;
            }
        }
        int minX = (int) Math.floor(x - r) >> 4, maxX = (int) Math.floor(x + r) >> 4;
        int minY = (int) Math.floor(y - r) >> 4, maxY = (int) Math.floor(y + r) >> 4;
        int minZ = (int) Math.floor(z - r) >> 4, maxZ = (int) Math.floor(z + r) >> 4;
        for (int sx = minX; sx <= maxX; sx++) {
            for (int sy = minY; sy <= maxY; sy++) {
                for (int sz = minZ; sz <= maxZ; sz++) {
                    if (VISIBLE_SECTIONS.contains(net.minecraft.core.SectionPos.asLong(sx, sy, sz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Box (x, y, width, height) in a target of the given size covering the light's bounds, or false for none. */
    private static boolean scissor(RenderLight light, int width, int height, int margin) {
        float minX = 1.0f, minY = 1.0f, maxX = -1.0f, maxY = -1.0f;
        // The light's own box (tighter than its bounding sphere for beams and cones)
        for (int corner = 0; corner < 8; corner++) {
            light.boxCorner(corner, POINT);
            CORNER.set(POINT[0], POINT[1], POINT[2], 1.0f);
            VIEW_PROJECTION.transform(CORNER);
            if (CORNER.w < 0.05f) {
                // A corner is behind the camera: the projection is unbounded
                SCISSOR[0] = 0;
                SCISSOR[1] = 0;
                SCISSOR[2] = width;
                SCISSOR[3] = height;
                return true;
            }
            float x = CORNER.x / CORNER.w;
            float y = CORNER.y / CORNER.w;
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        int x1 = Math.max(0, (int) Math.floor((Math.max(-1.0f, minX) * 0.5f + 0.5f) * width) - margin);
        int y1 = Math.max(0, (int) Math.floor((Math.max(-1.0f, minY) * 0.5f + 0.5f) * height) - margin);
        int x2 = Math.min(width, (int) Math.ceil((Math.min(1.0f, maxX) * 0.5f + 0.5f) * width) + margin);
        int y2 = Math.min(height, (int) Math.ceil((Math.min(1.0f, maxY) * 0.5f + 0.5f) * height) + margin);
        if (x2 <= x1 || y2 <= y1) {
            return false;
        }
        SCISSOR[0] = x1;
        SCISSOR[1] = y1;
        SCISSOR[2] = x2 - x1;
        SCISSOR[3] = y2 - y1;
        return true;
    }

    private static final boolean CHECK_ERRORS = Boolean.getBoolean("customlights.devtest");

    /** Development: reports OpenGL errors raised since the last check. */
    private static void checkErrors(String where) {
        if (!CHECK_ERRORS) {
            return;
        }
        int error;
        while ((error = GL11.glGetError()) != GL11.GL_NO_ERROR) {
            com.cotii.customlights.Customlights.LOGGER.warn("[devtest] GL error 0x{} {}", Integer.toHexString(error), where);
        }
    }

    /** The point all view rays of a projection start from (moved a little by view bobbing), in view space. */
    private static void eye(Matrix4f projection, Vector4f out) {
        projection.invert(EYE_INVERSE);
        out.set(0.0f, 0.0f, 1.0f, 0.0f);
        EYE_INVERSE.transform(out);
        if (Math.abs(out.w) < 1.0e-6f) {
            out.set(0.0f, 0.0f, 0.0f, 1.0f);
        }
        out.div(out.w);
    }

    private static final Long2IntOpenHashMap VANILLA_LIGHT = new Long2IntOpenHashMap();
    private static long vanillaLightTime = Long.MIN_VALUE;
    private static float cameraLight = 1.0f;

    /** The scene is already lit by the game, so a surface's own color is the pixel divided by the game's light there. */
    private static void vanillaLights() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || !com.cotii.customlights.client.compat.Compat.lightmapReady() || camera == null || ShaderCompat.shaderPackInUse()) {
            // Shader packs light the scene their own way: the light is added as it is
            cameraLight = 1.0f;
            for (int i = 0; i < FRAME.size(); i++) {
                FRAME.get(i).surfaceGain = 1.0f;
            }
            return;
        }
        if (level.getGameTime() != vanillaLightTime) {
            vanillaLightTime = level.getGameTime();
            VANILLA_LIGHT.clear();
        }
        Vec3 origin = camera.getPosition();
        cameraLight = vanillaLight(level, origin.x, origin.y, origin.z);
        for (int i = 0; i < FRAME.size(); i++) {
            RenderLight light = FRAME.get(i);
            float brightness = vanillaLight(level, origin.x + light.x, origin.y + light.y, origin.z + light.z);
            light.surfaceGain = Math.min(1.0f / Math.max(brightness, 1.0e-3f), MAX_SURFACE_GAIN);
        }
    }

    /** Brightness (0..1) of the game's light around a point: the brightest of the block and its neighbours. */
    private static float vanillaLight(ClientLevel level, double x, double y, double z) {
        long key = BlockPos.asLong(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        int packed = VANILLA_LIGHT.getOrDefault(key, -1);
        if (packed < 0) {
            int bx = BlockPos.getX(key);
            int by = BlockPos.getY(key);
            int bz = BlockPos.getZ(key);
            int block = 0;
            int sky = 0;
            for (Direction direction : NEIGHBOURS) {
                BlockPos.MutableBlockPos at = SCRATCH_POS.set(bx, by, bz);
                if (direction != null) {
                    at.move(direction);
                }
                block = Math.max(block, level.getBrightness(LightLayer.BLOCK, at));
                sky = Math.max(sky, level.getBrightness(LightLayer.SKY, at));
            }
            packed = block << 4 | sky;
            VANILLA_LIGHT.put(key, packed);
        }
        return com.cotii.customlights.client.compat.Compat.lightmapBrightness(level, (packed >> 4) & 15, packed & 15);
    }

    private static final Direction[] NEIGHBOURS = {null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
    private static final BlockPos.MutableBlockPos SCRATCH_POS = new BlockPos.MutableBlockPos();
    private static final float MAX_SURFACE_GAIN = 6.0f;

    private static int divide(int size, int divisor) {
        return Math.max(1, (size + divisor - 1) / divisor);
    }

    private static void bindTexture(int unit, int texture) {
        Gl.bindTexture(unit, texture);
    }


    /** A color-only float target. */
    private static final class FloatTarget {
        final int format;
        final int filter;
        int framebuffer;
        int texture;
        int width, height;

        FloatTarget(int format, int filter) {
            this.format = format;
            this.filter = filter;
        }

        void ensure(int newWidth, int newHeight) {
            if (framebuffer != 0 && width == newWidth && height == newHeight) {
                return;
            }
            if (framebuffer == 0) {
                framebuffer = Gl.genFramebuffer();
                texture = Gl.genTexture();
            }
            width = newWidth;
            height = newHeight;
            Gl.activeTexture(0);
            Gl.bindTexture(0, texture);
            Gl.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
            Gl.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
            Gl.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_EDGE);
            Gl.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, format, width, height, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (FloatBuffer) null);
            Gl.bindTexture(0, 0);
            Gl.bindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            Gl.attachTexture(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, texture);
        }

        void bindAndClear(int viewWidth, int viewHeight) {
            Gl.bindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            Gl.viewport(0, 0, viewWidth, viewHeight);
            Gl.clearColor(0.0f, 0.0f, 0.0f, 0.0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        }
    }
}
