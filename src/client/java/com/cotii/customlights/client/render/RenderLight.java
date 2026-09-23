package com.cotii.customlights.client.render;

import com.cotii.customlights.client.light.LightShape;

/** One light instance ready for the shader, relative to the camera. */
public final class RenderLight {
    public float x, y, z;
    /** Radians, Minecraft convention: yaw 0 = +Z, pitch 90 = down. */
    public float yaw, pitch;
    /** Radians turned around the light's own axis (clockwise seen from where it points to). */
    public float roll;
    public float stretchX = 1.0f, stretchY = 1.0f, stretchZ = 1.0f;
    /** The point the stretch grows from, on the light's own axes (0 = its middle). */
    public float pivotX, pivotY, pivotZ;
    /** Where in its own time a shape that moves by itself is, so two of them never run in step. */
    public float phase;
    public LightShape shape = LightShape.SPHERE;
    public float red = 1.0f, green = 1.0f, blue = 1.0f;
    /** Brightness with fade applied; negative is darkness. */
    public float intensity;
    public float radius, distance;
    /** 0..10. */
    public float atmosphere;
    /** Glow added on the lit surfaces, whatever their colour. */
    public float luminosity;
    /** Full opening of cones in degrees (0 = the shape's own). */
    public float angle;
    /** Half sizes (world axes) of the box the light is swept over: a group of objects lit as one. */
    public float extentX, extentY, extentZ;
    /** Frame axes in world space (columns: X, Y = direction, Z). */
    final float[] basis = new float[9];
    float boundCenterX, boundCenterY, boundCenterZ, boundRadius;
    /** Box around the light in its own (unstretched) frame: half sizes and the middle along Y. */
    float boxHalfX, boxHalfY, boxHalfZ, boxMiddle;
    double sortDistance;
    /** How much of the screen the light covers (for the budget), and its atmosphere detail this frame. */
    float importance;
    int hazeSteps;
    /** False: solid blocks stop the light. */
    public boolean seeThrough = true;
    /** How much the surface light is brightened: the game already darkened the scene there. */
    float surfaceGain = 1.0f;

    void reset() {
        radius = 0.0f;
        distance = 0.0f;
        atmosphere = 0.0f;
        luminosity = 0.0f;
        angle = 0.0f;
        extentX = extentY = extentZ = 0.0f;
        intensity = 0.0f;
        yaw = 0.0f;
        pitch = 0.0f;
        roll = 0.0f;
        stretchX = stretchY = stretchZ = 1.0f;
        pivotX = pivotY = pivotZ = 0.0f;
        phase = 0.0f;
        red = green = blue = 1.0f;
        seeThrough = true;
    }

    public RenderLight setColor(int rgb, float intensity) {
        red = ((rgb >> 16) & 0xFF) / 255.0f;
        green = ((rgb >> 8) & 0xFF) / 255.0f;
        blue = (rgb & 0xFF) / 255.0f;
        this.intensity = intensity;
        return this;
    }

    public RenderLight setStretch(float x, float y, float z) {
        stretchX = nonZero(x);
        stretchY = nonZero(y);
        stretchZ = nonZero(z);
        return this;
    }

    /** Spreads the shapes that move on their own over their own time. */
    public RenderLight setSeed(int seed) {
        phase = (seed & 1023) * (6.2831855f / 1024.0f);
        return this;
    }

    public RenderLight setPivot(float x, float y, float z) {
        pivotX = x;
        pivotY = y;
        pivotZ = z;
        return this;
    }

    private static float nonZero(float value) {
        if (Math.abs(value) < 0.02f) {
            return value < 0.0f ? -0.02f : 0.02f;
        }
        return value;
    }

    public RenderLight setDirection(float yawDegrees, float pitchDegrees) {
        yaw = (float) Math.toRadians(yawDegrees);
        pitch = (float) Math.toRadians(pitchDegrees);
        return this;
    }

    /** World direction the light points to. */
    public void setDirectionVector(float x, float y, float z) {
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        if (length < 1.0e-5f) {
            yaw = 0.0f;
            pitch = (float) (Math.PI / 2.0d);
            return;
        }
        x /= length;
        y /= length;
        z /= length;
        pitch = (float) Math.asin(Math.max(-1.0f, Math.min(1.0f, -y)));
        yaw = (float) Math.atan2(-x, z);
    }

    boolean isVisible() {
        return Math.abs(intensity) > 0.001f && (radius > 0.001f || distance > 0.001f);
    }

    /** Frame axes (yaw around Y after tilting the up axis by pitch + 90°) and the world bounds. */
    void computeBounds() {
        double tilt = pitch + Math.PI / 2.0d;
        float ct = (float) Math.cos(tilt), st = (float) Math.sin(tilt);
        float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
        // Rx(tilt): X stays, Y -> (0, ct, st), Z -> (0, -st, ct); then Ry(yaw): (x, y, z) -> (x*cy - z*sy, y, x*sy +
        basis[0] = cy;
        basis[1] = 0.0f;
        basis[2] = sy;
        basis[3] = -st * sy;
        basis[4] = ct;
        basis[5] = st * cy;
        basis[6] = -ct * sy;
        basis[7] = -st;
        basis[8] = ct * cy;
        if (roll != 0.0f) {
            // Turn the frame around its own Y axis
            float cr = (float) Math.cos(roll), sr = (float) Math.sin(roll);
            for (int i = 0; i < 3; i++) {
                float axisX = basis[i];
                float axisZ = basis[6 + i];
                basis[i] = cr * axisX + sr * axisZ;
                basis[6 + i] = -sr * axisX + cr * axisZ;
            }
        }

        float[] extent = shape.extent(radius, angle);
        float half = extent[0];
        float bottom = extent[1];
        float top = extent[2];
        if (shape == LightShape.SPOTLIGHT) {
            // Its haze carries on a little past its length
            half *= 1.4f;
            top *= 1.4f;
        }
        float blur = Math.max(0.0f, distance);
        if (shape == LightShape.FLASHLIGHT || shape == LightShape.SIREN) {
            // Their rim softness and spill (see common.glsl) reach past the angle
            float reach = Math.max(0.0f, radius) * shape.length;
            float spread = shape.spread(angle);
            float soft = shape == LightShape.FLASHLIGHT ? Math.min(0.08f + blur * 0.2f, 1.5f) : Math.min(0.15f + blur * 0.2f, 1.5f);
            float side = reach * Math.min(spread * Math.max(shape == LightShape.FLASHLIGHT ? 1.9f : 1.0f, 1.0f + soft), 12.0f);
            if (shape == LightShape.FLASHLIGHT) {
                half = side;
            } else {
                bottom = -side;
                top = side;
            }
        }
        // How far past the shape light (or haze) can reach, in the shape's own units
        float glow = switch (shape) {
            case SPHERE -> Math.max(blur, half) + 0.5f;
            case FLASHLIGHT, SIREN -> 0.3f;
            default -> Math.max(blur * 1.25f, Math.max(0.0f, radius) * 0.35f + 0.1f) + 0.3f;
        };
        boxHalfX = half + glow;
        boxHalfY = (top - bottom) * 0.5f + glow;
        boxHalfZ = half + glow;
        boxMiddle = (bottom + top) * 0.5f;
        // Centre in world: the middle of the shape, stretched around the pivot and turned onto the light's axes
        float localX = -pivotX * stretchX + pivotX;
        float localY = (boxMiddle - pivotY) * stretchY + pivotY;
        float localZ = -pivotZ * stretchZ + pivotZ;
        boundCenterX = x + basis[0] * localX + basis[3] * localY + basis[6] * localZ;
        boundCenterY = y + basis[1] * localX + basis[4] * localY + basis[7] * localZ;
        boundCenterZ = z + basis[2] * localX + basis[5] * localY + basis[8] * localZ;
        float sx = boxHalfX * stretchX, sy2 = boxHalfY * stretchY, sz = boxHalfZ * stretchZ;
        boundRadius = (float) Math.sqrt(sx * sx + sy2 * sy2 + sz * sz);
        if (hasExtent()) {
            boundRadius += (float) Math.sqrt(extentX * extentX + extentY * extentY + extentZ * extentZ);
        }
    }

    /** Corner {@code index} (0..7) of the box around the light, relative to the camera, into {@code out}. */
    boolean hasExtent() {
        return extentX > 0.0f || extentY > 0.0f || extentZ > 0.0f;
    }

    void boxCorner(int index, float[] out) {
        if (hasExtent()) {
            // Swept lights: the box around the light's own box, grown by the sweep
            float sx = boxHalfX * Math.abs(stretchX), sy = boxHalfY * Math.abs(stretchY), sz = boxHalfZ * Math.abs(stretchZ);
            float hx = Math.abs(basis[0]) * sx + Math.abs(basis[3]) * sy + Math.abs(basis[6]) * sz + extentX;
            float hy = Math.abs(basis[1]) * sx + Math.abs(basis[4]) * sy + Math.abs(basis[7]) * sz + extentY;
            float hz = Math.abs(basis[2]) * sx + Math.abs(basis[5]) * sy + Math.abs(basis[8]) * sz + extentZ;
            out[0] = boundCenterX + ((index & 1) == 0 ? -hx : hx);
            out[1] = boundCenterY + ((index & 2) == 0 ? -hy : hy);
            out[2] = boundCenterZ + ((index & 4) == 0 ? -hz : hz);
            return;
        }
        float lx = (((index & 1) == 0 ? -boxHalfX : boxHalfX) - pivotX) * stretchX + pivotX;
        float ly = (boxMiddle + ((index & 2) == 0 ? -boxHalfY : boxHalfY) - pivotY) * stretchY + pivotY;
        float lz = (((index & 4) == 0 ? -boxHalfZ : boxHalfZ) - pivotZ) * stretchZ + pivotZ;
        out[0] = x + basis[0] * lx + basis[3] * ly + basis[6] * lz;
        out[1] = y + basis[1] * lx + basis[4] * ly + basis[7] * lz;
        out[2] = z + basis[2] * lx + basis[5] * ly + basis[8] * lz;
    }
}
