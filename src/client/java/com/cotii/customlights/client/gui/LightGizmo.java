package com.cotii.customlights.client.gui;

import com.cotii.customlights.client.render.LightRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

/**
 * Axiom-style handles drawn over the world for the light being edited, spread out so each one is easy to grab: <ul>
 * <li>red/green/blue arrows move it along an axis, the white square moves it freely;</li> <li>the green ring turns it (yaw),
 * the red ring tilts it (pitch), the orange knob outside the rings aims it;</li> <li>the cyan squares, on the other side of
 * the arrows, stretch it on its own axes;</li> <li>the yellow knob on the radius circle changes its radius.</li> </ul> The
 * handle closest to the mouse wins.
 */
final class LightGizmo {
    /** What the gizmo edits. */
    interface Target {
        Vec3 position();

        void setPosition(Vec3 position);

        boolean directional();

        float yaw();

        float pitch();

        void setDirection(float yaw, float pitch);

        float radius();

        void setRadius(float radius);

        /** Whether the stretch handles show (the light's stretch is on). */
        boolean stretchEnabled();

        float stretch(int axis);

        void setStretch(int axis, float value);

        /** Where the stretch grows from, on the light's own axes. */
        float pivot(int axis);

        void setPivot(int axis, float value);
    }

    private enum Handle {
        NONE, CENTER, MOVE_X, MOVE_Y, MOVE_Z, YAW, PITCH, AIM, RADIUS, STRETCH_X, STRETCH_Y, STRETCH_Z, PIVOT
    }

    private static final int COLOR_X = 0xFFFF4A4A;
    private static final int COLOR_Y = 0xFF52E052;
    private static final int COLOR_Z = 0xFF4A7CFF;
    private static final int COLOR_AIM = 0xFFFFA030;
    private static final int COLOR_RADIUS = 0xFFFFE060;
    private static final int COLOR_STRETCH = 0xFF40E8F0;
    private static final int COLOR_PIVOT = 0xFFC060FF;
    private static final int COLOR_HOT = 0xFFFFFFFF;
    private static final int OUTLINE = 0xC0000000;
    /** Pixels around a handle that still grab it. */
    private static final double PICK = 9.0d;
    private static final int RING_SEGMENTS = 64;
    private static final float MAX_STRETCH = 64.0f;
    /** Sizes relative to the arrow length. */
    private static final double RING_SIZE = 1.3d;
    private static final double AIM_SIZE = 1.75d;
    private static final double STRETCH_SIZE = 0.7d;

    private final Vector4f clip = new Vector4f();
    private Target target;
    private Handle hovered = Handle.NONE;
    private Handle dragging = Handle.NONE;
    private int width, height;

    // Drag state
    private double startMouseX, startMouseY;
    private Vec3 startPosition;
    private float startYaw, startPitch, startRadius, startStretch;
    private final float[] startPivot = new float[3];
    private double startAngle;
    private double startMouseDistance;
    private double startWorldPerPixel;

    void setTarget(Target newTarget) {
        if (newTarget == null) {
            dragging = Handle.NONE;
        }
        target = newTarget;
    }

    boolean dragging() {
        return dragging != Handle.NONE;
    }

    // ── Projection
    // ─────────────────────────────────────────────────

    private static Vec3 camera() {
        return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
    }

    /** Screen position (GUI pixels) of a world point, or null when behind the camera. */
    private double[] screen(Vec3 world) {
        Vec3 camera = camera();
        if (!LightRenderer.project((float) (world.x - camera.x), (float) (world.y - camera.y), (float) (world.z - camera.z), clip) || clip.w < 0.05f) {
            return null;
        }
        return new double[]{(clip.x / clip.w * 0.5d + 0.5d) * width, (0.5d - clip.y / clip.w * 0.5d) * height};
    }

    /** The mouse ray: {origin, direction} in world space. */
    private Vec3[] ray(double mouseX, double mouseY) {
        Matrix4f viewProjection = LightRenderer.lastViewProjection();
        if (viewProjection == null) {
            return null;
        }
        Matrix4f inverse = new Matrix4f(viewProjection).invert();
        float ndcX = (float) (mouseX / width * 2.0d - 1.0d);
        float ndcY = (float) (1.0d - mouseY / height * 2.0d);
        Vector4f near = inverse.transform(new Vector4f(ndcX, ndcY, -1.0f, 1.0f));
        Vector4f far = inverse.transform(new Vector4f(ndcX, ndcY, 1.0f, 1.0f));
        Vec3 camera = camera();
        Vec3 from = new Vec3(near.x / near.w, near.y / near.w, near.z / near.w);
        Vec3 to = new Vec3(far.x / far.w, far.y / far.w, far.z / far.w);
        return new Vec3[]{camera.add(from), to.subtract(from).normalize()};
    }

    /** Where the mouse ray meets the plane through {@code point} with {@code normal}, or null. */
    private Vec3 onPlane(double mouseX, double mouseY, Vec3 point, Vec3 normal) {
        Vec3[] ray = ray(mouseX, mouseY);
        if (ray == null) {
            return null;
        }
        double facing = ray[1].dot(normal);
        if (Math.abs(facing) < 1.0e-3) {
            return null;
        }
        double t = point.subtract(ray[0]).dot(normal) / facing;
        return t <= 0.0d ? null : ray[0].add(ray[1].scale(t));
    }

    private static Vec3 viewForward() {
        Matrix4f viewProjection = LightRenderer.lastViewProjection();
        if (viewProjection == null) {
            return new Vec3(0.0d, 0.0d, 1.0d);
        }
        Matrix4f inverse = new Matrix4f(viewProjection).invert();
        Vector4f near = inverse.transform(new Vector4f(0.0f, 0.0f, -1.0f, 1.0f));
        Vector4f far = inverse.transform(new Vector4f(0.0f, 0.0f, 1.0f, 1.0f));
        return new Vec3(far.x / far.w - near.x / near.w, far.y / far.w - near.y / near.w, far.z / far.w - near.z / near.w).normalize();
    }

    /** World length of the arrows: about the same size on screen at any distance. */
    private double scale(Vec3 position) {
        return Math.max(0.35d, position.distanceTo(camera()) * 0.2d);
    }

    // ── Geometry
    // ───────────────────────────────────────────────────

    private static Vec3 axis(Handle handle) {
        return switch (handle) {
            case MOVE_X -> new Vec3(1.0d, 0.0d, 0.0d);
            case MOVE_Y -> new Vec3(0.0d, 1.0d, 0.0d);
            default -> new Vec3(0.0d, 0.0d, 1.0d);
        };
    }

    /** The light's own axes (X, Y = where it points, Z), as the light shader builds them. */
    private static Vec3[] frame(float yawDegrees, float pitchDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double tilt = Math.toRadians(pitchDegrees) + Math.PI / 2.0d;
        double cy = Math.cos(yaw), sy = Math.sin(yaw), ct = Math.cos(tilt), st = Math.sin(tilt);
        return new Vec3[]{new Vec3(cy, 0.0d, sy), new Vec3(-st * sy, ct, st * cy), new Vec3(-ct * sy, -st, ct * cy)};
    }

    private static Vec3 direction(float yawDegrees, float pitchDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        return new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
    }

    /** Stretch handles sit on the far side of the light's own axes, away from the arrows. */
    private Vec3 stretchAxis(int axis) {
        return frame(target.yaw(), target.pitch())[axis].scale(-1.0d);
    }

    private Vec3 stretchPoint(int axis) {
        return target.position().add(stretchAxis(axis).scale(scale(target.position()) * STRETCH_SIZE));
    }

    /** The pivot sits where the light stretches from: its own axes, in unstretched blocks. */
    private Vec3 pivotPoint() {
        Vec3[] frame = frame(target.yaw(), target.pitch());
        return target.position().add(frame[0].scale(target.pivot(0))).add(frame[1].scale(target.pivot(1))).add(frame[2].scale(target.pivot(2)));
    }

    private Vec3 aimPoint() {
        return target.position().add(direction(target.yaw(), target.pitch()).scale(scale(target.position()) * AIM_SIZE));
    }

    private Vec3 ringPoint(boolean yawRing, double angle, double size) {
        Vec3 center = target.position();
        if (yawRing) {
            return center.add(Math.cos(angle) * size, 0.0d, Math.sin(angle) * size);
        }
        double yaw = Math.toRadians(target.yaw());
        Vec3 forward = new Vec3(-Math.sin(yaw), 0.0d, Math.cos(yaw));
        return center.add(forward.scale(Math.cos(angle) * size)).add(0.0d, Math.sin(angle) * size, 0.0d);
    }

    /** Screen pixels per world block at the light, measured sideways. */
    private double pixelsPerBlock(double[] center) {
        Vec3 side = viewForward().cross(new Vec3(0.0d, 1.0d, 0.0d));
        side = side.lengthSqr() < 1.0e-6d ? new Vec3(1.0d, 0.0d, 0.0d) : side.normalize();
        double[] edge = screen(target.position().add(side));
        return edge == null ? 1.0d : Math.max(1.0e-3d, Math.hypot(edge[0] - center[0], edge[1] - center[1]));
    }

    /** The radius knob: on the radius circle, down and to the right, never inside the centre square. */
    private double[] radiusKnob(double[] center) {
        double radius = Math.max(target.radius() * pixelsPerBlock(center), 16.0d);
        return new double[]{center[0] + radius * 0.7071d, center[1] + radius * 0.7071d};
    }

    // ── Rendering
    // ──────────────────────────────────────────────────

    void render(GuiGraphics graphics, int screenWidth, int screenHeight, double mouseX, double mouseY, boolean mouseFree) {
        width = screenWidth;
        height = screenHeight;
        if (target == null || LightRenderer.lastViewProjection() == null) {
            return;
        }
        double[] center = screen(target.position());
        if (center == null) {
            return;
        }
        if (dragging == Handle.NONE) {
            hovered = mouseFree ? pick(mouseX, mouseY) : Handle.NONE;
        }
        Handle active = dragging != Handle.NONE ? dragging : hovered;
        com.cotii.customlights.client.compat.GuiCanvas canvas = com.cotii.customlights.client.compat.GuiCanvas.begin(graphics);
        double size = scale(target.position());

        // Radius circle (thin) and its knob
        double radius = target.radius() * pixelsPerBlock(center);
        if (radius > 3.0d) {
            circle(canvas, center, radius, (active == Handle.RADIUS ? COLOR_HOT : COLOR_RADIUS) & 0x90FFFFFF, 1.0f);
        }
        // Rings
        ring(canvas, true, size * RING_SIZE, active == Handle.YAW ? COLOR_HOT : 0xD052E052, active == Handle.YAW ? 3.0f : 2.0f);
        if (target.directional()) {
            ring(canvas, false, size * RING_SIZE, active == Handle.PITCH ? COLOR_HOT : 0xD0FF4A4A, active == Handle.PITCH ? 3.0f : 2.0f);
        }
        // Stretch handles
        for (int axis = 0; axis < (target.stretchEnabled() ? 3 : 0); axis++) {
            double[] point = screen(stretchPoint(axis));
            if (point != null) {
                Handle handle = Handle.values()[Handle.STRETCH_X.ordinal() + axis];
                line(canvas, center, point, 0x7040E8F0, 1.0f);
                square(canvas, point, 4.5f, OUTLINE);
                square(canvas, point, 3.5f, active == handle ? COLOR_HOT : COLOR_STRETCH);
            }
        }
        // Pivot
        if (target.stretchEnabled()) {
            double[] point = screen(pivotPoint());
            if (point != null) {
                line(canvas, center, point, COLOR_PIVOT & 0x60FFFFFF, 1.5f);
                diamond(canvas, point, 10.0f, OUTLINE);
                diamond(canvas, point, 8.0f, active == Handle.PIVOT ? COLOR_HOT : COLOR_PIVOT);
            }
        }
        // Arrows
        for (Handle handle : new Handle[]{Handle.MOVE_X, Handle.MOVE_Y, Handle.MOVE_Z}) {
            double[] tip = screen(target.position().add(axis(handle).scale(size)));
            if (tip == null) {
                continue;
            }
            int color = active == handle ? COLOR_HOT : handle == Handle.MOVE_X ? COLOR_X : handle == Handle.MOVE_Y ? COLOR_Y : COLOR_Z;
            double[] from = along(center, tip, 0.18d);
            line(canvas, from, tip, OUTLINE, 5.0f);
            line(canvas, from, tip, color, active == handle ? 3.0f : 2.5f);
            arrowHead(canvas, center, tip, color);
        }
        // Aim knob
        if (target.directional()) {
            double[] aim = screen(aimPoint());
            if (aim != null) {
                line(canvas, center, aim, (active == Handle.AIM ? COLOR_HOT : COLOR_AIM) & 0x80FFFFFF, 1.0f);
                disc(canvas, aim, 6.0f, OUTLINE);
                disc(canvas, aim, 4.5f, active == Handle.AIM ? COLOR_HOT : COLOR_AIM);
            }
        }
        // Radius knob
        double[] knob = radiusKnob(center);
        diamond(canvas, knob, 6.0f, OUTLINE);
        diamond(canvas, knob, 4.5f, active == Handle.RADIUS ? COLOR_HOT : COLOR_RADIUS);
        // Centre
        square(canvas, center, 6.0f, OUTLINE);
        square(canvas, center, 4.5f, active == Handle.CENTER ? COLOR_HOT : 0xFFE8E8E8);
        canvas.end(graphics);

        String hint = switch (active) {
            case CENTER -> "Move";
            case MOVE_X -> "Move X";
            case MOVE_Y -> "Move Y";
            case MOVE_Z -> "Move Z";
            case YAW -> "Yaw " + Math.round(target.yaw()) + "°";
            case PITCH -> "Pitch " + Math.round(target.pitch()) + "°";
            case AIM -> "Aim " + Math.round(target.yaw()) + "° " + Math.round(target.pitch()) + "°";
            case RADIUS -> "Radius " + Math.round(target.radius() * 100.0f) / 100.0f;
            case STRETCH_X, STRETCH_Y, STRETCH_Z -> "Stretch " + "XYZ".charAt(active.ordinal() - Handle.STRETCH_X.ordinal()) + " "
                    + Math.round(target.stretch(active.ordinal() - Handle.STRETCH_X.ordinal()) * 100.0f) / 100.0f;
            case PIVOT -> "Pivot " + round2(target.pivot(0)) + " " + round2(target.pivot(1)) + " " + round2(target.pivot(2));
            case NONE -> null;
        };
        if (hint != null) {
            String text = hint + (dragging != Handle.NONE ? "" : "   Ctrl: snap");
            int textX = (int) Math.min(mouseX + 12, width - Minecraft.getInstance().font.width(text) - 4);
            int textY = (int) Math.max(4, mouseY - 14);
            graphics.fill(textX - 3, textY - 2, textX + Minecraft.getInstance().font.width(text) + 3, textY + 10, 0xB0000000);
            graphics.drawString(Minecraft.getInstance().font, text, textX, textY, 0xFFFFFFFF, true);
        }
    }

    private static double[] along(double[] from, double[] to, double t) {
        return new double[]{from[0] + (to[0] - from[0]) * t, from[1] + (to[1] - from[1]) * t};
    }

    private void ring(com.cotii.customlights.client.compat.GuiCanvas canvas, boolean yawRing, double size, int color, float thickness) {
        double[] previous = null;
        for (int i = 0; i <= RING_SEGMENTS; i++) {
            double[] point = screen(ringPoint(yawRing, i * Math.PI * 2.0d / RING_SEGMENTS, size));
            if (previous != null && point != null) {
                line(canvas, previous, point, color, thickness);
            }
            previous = point;
        }
    }

    private static void circle(com.cotii.customlights.client.compat.GuiCanvas canvas, double[] center, double radius, int color, float thickness) {
        for (int i = 0; i < RING_SEGMENTS; i++) {
            double a = i * Math.PI * 2.0d / RING_SEGMENTS;
            double b = (i + 1) * Math.PI * 2.0d / RING_SEGMENTS;
            line(canvas, new double[]{center[0] + Math.cos(a) * radius, center[1] + Math.sin(a) * radius},
                    new double[]{center[0] + Math.cos(b) * radius, center[1] + Math.sin(b) * radius}, color, thickness);
        }
    }

    private static void line(com.cotii.customlights.client.compat.GuiCanvas canvas, double[] from, double[] to, int color, float thickness) {
        double dx = to[0] - from[0];
        double dy = to[1] - from[1];
        double length = Math.hypot(dx, dy);
        if (length < 1.0e-3) {
            return;
        }
        float nx = (float) (-dy / length * thickness * 0.5d);
        float ny = (float) (dx / length * thickness * 0.5d);
        quad(canvas, (float) from[0] + nx, (float) from[1] + ny, (float) from[0] - nx, (float) from[1] - ny,
                (float) to[0] - nx, (float) to[1] - ny, (float) to[0] + nx, (float) to[1] + ny, color);
    }

    private static void arrowHead(com.cotii.customlights.client.compat.GuiCanvas canvas, double[] from, double[] tip, int color) {
        double dx = tip[0] - from[0];
        double dy = tip[1] - from[1];
        double length = Math.hypot(dx, dy);
        if (length < 1.0e-3) {
            return;
        }
        double ux = dx / length, uy = dy / length;
        float endX = (float) (tip[0] + ux * 11.0d), endY = (float) (tip[1] + uy * 11.0d);
        float leftX = (float) (tip[0] - uy * 5.5d), leftY = (float) (tip[1] + ux * 5.5d);
        float rightX = (float) (tip[0] + uy * 5.5d), rightY = (float) (tip[1] - ux * 5.5d);
        quad(canvas, leftX, leftY, rightX, rightY, endX, endY, endX, endY, color);
    }

    private static void square(com.cotii.customlights.client.compat.GuiCanvas canvas, double[] center, float half, int color) {
        float x = (float) center[0], y = (float) center[1];
        quad(canvas, x - half, y - half, x - half, y + half, x + half, y + half, x + half, y - half, color);
    }

    private static void diamond(com.cotii.customlights.client.compat.GuiCanvas canvas, double[] center, float half, int color) {
        float x = (float) center[0], y = (float) center[1];
        quad(canvas, x, y - half, x - half, y, x, y + half, x + half, y, color);
    }

    private static void disc(com.cotii.customlights.client.compat.GuiCanvas canvas, double[] center, float radius, int color) {
        float x = (float) center[0], y = (float) center[1];
        int segments = 14;
        for (int i = 0; i < segments; i++) {
            double a = i * Math.PI * 2.0d / segments;
            double b = (i + 1) * Math.PI * 2.0d / segments;
            quad(canvas, x, y, x, y, x + (float) (Math.cos(b) * radius), y + (float) (Math.sin(b) * radius),
                    x + (float) (Math.cos(a) * radius), y + (float) (Math.sin(a) * radius), color);
        }
    }

    private static void quad(com.cotii.customlights.client.compat.GuiCanvas canvas, float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4,
                             int color) {
        canvas.quad(x1, y1, x2, y2, x3, y3, x4, y4, color);
    }

    // ── Picking and dragging
    // ───────────────────────────────────────

    /** The handle closest to the mouse (knobs and squares win ties over lines and rings). */
    private Handle pick(double mouseX, double mouseY) {
        double[] center = screen(target.position());
        if (center == null) {
            return Handle.NONE;
        }
        Handle best = Handle.NONE;
        double bestDistance = PICK;
        // Points count as a little closer than they are, so they are easy to grab where lines cross them
        double distance = Math.hypot(mouseX - center[0], mouseY - center[1]) - 3.0d;
        if (distance < bestDistance) {
            bestDistance = distance;
            best = Handle.CENTER;
        }
        double[] knob = radiusKnob(center);
        distance = Math.hypot(mouseX - knob[0], mouseY - knob[1]) - 3.0d;
        if (distance < bestDistance) {
            bestDistance = distance;
            best = Handle.RADIUS;
        }
        for (int axis = 0; axis < (target.stretchEnabled() ? 3 : 0); axis++) {
            double[] point = screen(stretchPoint(axis));
            if (point != null) {
                distance = Math.hypot(mouseX - point[0], mouseY - point[1]) - 3.0d;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = Handle.values()[Handle.STRETCH_X.ordinal() + axis];
                }
            }
        }
        if (target.stretchEnabled()) {
            double[] point = screen(pivotPoint());
            if (point != null) {
                // The biggest handle, so it is easy to grab where the others cross it
                distance = Math.hypot(mouseX - point[0], mouseY - point[1]) - 7.0d;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = Handle.PIVOT;
                }
            }
        }
        if (target.directional()) {
            double[] aim = screen(aimPoint());
            if (aim != null) {
                distance = Math.hypot(mouseX - aim[0], mouseY - aim[1]) - 3.0d;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = Handle.AIM;
                }
            }
        }
        double size = scale(target.position());
        for (Handle handle : new Handle[]{Handle.MOVE_X, Handle.MOVE_Y, Handle.MOVE_Z}) {
            double[] tip = screen(target.position().add(axis(handle).scale(size)));
            if (tip != null) {
                double[] from = along(center, tip, 0.18d);
                double[] end = along(center, tip, 1.12d);
                distance = segmentDistance(mouseX, mouseY, from, end);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = handle;
                }
            }
        }
        for (boolean yawRing : new boolean[]{true, false}) {
            if (!yawRing && !target.directional()) {
                continue;
            }
            double[] previous = null;
            for (int i = 0; i <= RING_SEGMENTS; i++) {
                double[] point = screen(ringPoint(yawRing, i * Math.PI * 2.0d / RING_SEGMENTS, size * RING_SIZE));
                if (previous != null && point != null) {
                    distance = segmentDistance(mouseX, mouseY, previous, point);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = yawRing ? Handle.YAW : Handle.PITCH;
                    }
                }
                previous = point;
            }
        }
        return best;
    }

    private static double segmentDistance(double x, double y, double[] a, double[] b) {
        double dx = b[0] - a[0];
        double dy = b[1] - a[1];
        double lengthSquared = dx * dx + dy * dy;
        double t = lengthSquared < 1.0e-6 ? 0.0d : Math.max(0.0d, Math.min(1.0d, ((x - a[0]) * dx + (y - a[1]) * dy) / lengthSquared));
        return Math.hypot(x - (a[0] + dx * t), y - (a[1] + dy * t));
    }

    /** Starts dragging the handle under the mouse; false when there is none. */
    boolean press(double mouseX, double mouseY) {
        if (target == null) {
            return false;
        }
        Handle handle = pick(mouseX, mouseY);
        if (handle == Handle.NONE) {
            return false;
        }
        dragging = handle;
        startMouseX = mouseX;
        startMouseY = mouseY;
        startPosition = target.position();
        startYaw = target.yaw();
        startPitch = target.pitch();
        startRadius = target.radius();
        if (handle.ordinal() >= Handle.STRETCH_X.ordinal() && handle != Handle.PIVOT) {
            startStretch = target.stretch(handle.ordinal() - Handle.STRETCH_X.ordinal());
        }
        for (int axis = 0; axis < 3; axis++) {
            startPivot[axis] = target.pivot(axis);
        }
        double[] center = screen(startPosition);
        startMouseDistance = center == null ? 1.0d : Math.hypot(mouseX - center[0], mouseY - center[1]);
        startWorldPerPixel = center == null ? 0.01d : 1.0d / pixelsPerBlock(center);
        startAngle = ringAngle(handle, mouseX, mouseY);
        return true;
    }

    void release() {
        dragging = Handle.NONE;
    }

    /** Angle (degrees) of the mouse around the ring being dragged. */
    private double ringAngle(Handle handle, double mouseX, double mouseY) {
        if (handle != Handle.YAW && handle != Handle.PITCH) {
            return 0.0d;
        }
        Vec3 center = startPosition;
        if (handle == Handle.YAW) {
            Vec3 hit = onPlane(mouseX, mouseY, center, new Vec3(0.0d, 1.0d, 0.0d));
            if (hit == null) {
                return screenAngle(mouseX, mouseY);
            }
            Vec3 v = hit.subtract(center);
            return Math.toDegrees(Math.atan2(-v.x, v.z));
        }
        double yaw = Math.toRadians(startYaw);
        Vec3 side = new Vec3(Math.cos(yaw), 0.0d, Math.sin(yaw));
        Vec3 forward = new Vec3(-Math.sin(yaw), 0.0d, Math.cos(yaw));
        Vec3 hit = onPlane(mouseX, mouseY, center, side);
        if (hit == null) {
            return screenAngle(mouseX, mouseY);
        }
        Vec3 v = hit.subtract(center);
        return Math.toDegrees(Math.atan2(-v.y, v.dot(forward)));
    }

    private double screenAngle(double mouseX, double mouseY) {
        double[] center = screen(startPosition);
        return center == null ? 0.0d : Math.toDegrees(Math.atan2(mouseY - center[1], mouseX - center[0]));
    }

    /** How far (in blocks along {@code axis}) the mouse moved, following the axis on screen. */
    private double alongAxis(Vec3 axis, double mouseX, double mouseY) {
        double size = scale(startPosition);
        double[] a = screen(startPosition);
        double[] b = screen(startPosition.add(axis.scale(size)));
        if (a == null || b == null) {
            return 0.0d;
        }
        double dx = b[0] - a[0];
        double dy = b[1] - a[1];
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared < 4.0d) {
            return 0.0d;
        }
        return ((mouseX - startMouseX) * dx + (mouseY - startMouseY) * dy) / lengthSquared * size;
    }

    void drag(double mouseX, double mouseY) {
        if (target == null || dragging == Handle.NONE) {
            return;
        }
        boolean snap = isControlDown();
        switch (dragging) {
            case CENTER -> {
                Vec3 normal = viewForward();
                Vec3 from = onPlane(startMouseX, startMouseY, startPosition, normal);
                Vec3 to = onPlane(mouseX, mouseY, startPosition, normal);
                if (from != null && to != null) {
                    target.setPosition(snap(startPosition.add(to.subtract(from)), snap));
                }
            }
            case MOVE_X, MOVE_Y, MOVE_Z -> {
                Vec3 moved = startPosition.add(axis(dragging).scale(alongAxis(axis(dragging), mouseX, mouseY)));
                target.setPosition(snap ? snapAxis(moved, dragging) : snap(moved, false));
            }
            case YAW -> {
                double angle = ringAngle(Handle.YAW, mouseX, mouseY);
                float yaw = wrap((float) (startYaw + (angle - startAngle)));
                target.setDirection(snap ? Math.round(yaw / 15.0f) * 15.0f : round(yaw), target.pitch());
            }
            case PITCH -> {
                double angle = ringAngle(Handle.PITCH, mouseX, mouseY);
                float pitch = (float) Math.max(-90.0d, Math.min(90.0d, startPitch + (angle - startAngle)));
                target.setDirection(target.yaw(), snap ? Math.round(pitch / 15.0f) * 15.0f : round(pitch));
            }
            case AIM -> {
                Vec3 hit = onPlane(mouseX, mouseY, startPosition, viewForward());
                if (hit != null) {
                    Vec3 v = hit.subtract(startPosition);
                    if (v.lengthSqr() > 1.0e-6d) {
                        v = v.normalize();
                        float yaw = (float) Math.toDegrees(Math.atan2(-v.x, v.z));
                        float pitch = (float) Math.toDegrees(Math.asin(Math.max(-1.0d, Math.min(1.0d, -v.y))));
                        if (snap) {
                            yaw = Math.round(yaw / 15.0f) * 15.0f;
                            pitch = Math.round(pitch / 15.0f) * 15.0f;
                        }
                        target.setDirection(round(yaw), round(pitch));
                    }
                }
            }
            case RADIUS -> {
                double[] center = screen(startPosition);
                if (center != null) {
                    // Blocks follow the mouse, so tiny radii are as easy as big ones
                    double distance = Math.hypot(mouseX - center[0], mouseY - center[1]);
                    float radius = (float) Math.max(0.0d, startRadius + (distance - startMouseDistance) * startWorldPerPixel);
                    target.setRadius(snap ? Math.round(radius * 4.0f) / 4.0f : Math.round(radius * 100.0f) / 100.0f);
                }
            }
            case STRETCH_X, STRETCH_Y, STRETCH_Z -> {
                int index = dragging.ordinal() - Handle.STRETCH_X.ordinal();
                double handle = scale(startPosition) * STRETCH_SIZE;
                // Dragging the handle as far again as it sits from the light adds 1 to the stretch
                double amount = alongAxis(stretchAxis(index), mouseX, mouseY) / handle;
                float value = (float) Math.max(-MAX_STRETCH, Math.min(MAX_STRETCH, startStretch + amount));
                value = snap ? Math.round(value * 2.0f) / 2.0f : Math.round(value * 100.0f) / 100.0f;
                target.setStretch(index, Math.abs(value) < 0.05f ? (value < 0.0f ? -0.05f : 0.05f) : value);
            }
            case PIVOT -> {
                // Follows the mouse on the view plane and is written back on the light's own axes
                Vec3 start = pivotStart();
                Vec3 from = onPlane(startMouseX, startMouseY, start, viewForward());
                Vec3 to = onPlane(mouseX, mouseY, start, viewForward());
                if (from != null && to != null) {
                    Vec3 moved = to.subtract(from);
                    Vec3[] frame = frame(target.yaw(), target.pitch());
                    for (int axis = 0; axis < 3; axis++) {
                        float value = (float) (startPivot[axis] + moved.dot(frame[axis]));
                        value = Math.max(-MAX_STRETCH, Math.min(MAX_STRETCH, value));
                        target.setPivot(axis, snap ? Math.round(value * 2.0f) / 2.0f : (float) round2(value));
                    }
                }
            }
            case NONE -> {
            }
        }
    }

    /** Where the pivot was when the drag started. */
    private Vec3 pivotStart() {
        Vec3[] frame = frame(startYaw, startPitch);
        return startPosition.add(frame[0].scale(startPivot[0])).add(frame[1].scale(startPivot[1])).add(frame[2].scale(startPivot[2]));
    }

    private static Vec3 snap(Vec3 position, boolean snap) {
        if (!snap) {
            return new Vec3(round2(position.x), round2(position.y), round2(position.z));
        }
        return new Vec3(Math.round(position.x * 2.0d) / 2.0d, Math.round(position.y * 2.0d) / 2.0d, Math.round(position.z * 2.0d) / 2.0d);
    }

    private static Vec3 snapAxis(Vec3 position, Handle handle) {
        return switch (handle) {
            case MOVE_X -> new Vec3(Math.round(position.x * 2.0d) / 2.0d, position.y, position.z);
            case MOVE_Y -> new Vec3(position.x, Math.round(position.y * 2.0d) / 2.0d, position.z);
            default -> new Vec3(position.x, position.y, Math.round(position.z * 2.0d) / 2.0d);
        };
    }

    private static double round2(double value) {
        return Math.round(value * 100.0d) / 100.0d;
    }

    private static float round(float value) {
        return Math.round(value * 10.0f) / 10.0f;
    }

    private static float wrap(float degrees) {
        return ((degrees + 180.0f) % 360.0f + 360.0f) % 360.0f - 180.0f;
    }

    private static boolean isControlDown() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }
}
