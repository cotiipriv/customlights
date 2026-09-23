package com.cotii.customlights.client.light;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** A light placed with a command, the editor or the server plugin. */
public final class Light {
    public enum Kind {
        STATIC,
        FOLLOW
    }

    public static final float MAX_ATMOSPHERE = 10.0f;

    public String id;
    public Kind kind = Kind.STATIC;
    /** Sent by the server plugin: never saved on the client. */
    public boolean server;
    /** Dimension the light belongs to, or null for every dimension. */
    public String dimension;
    /** Group in the saved lights list ("" for none). */
    public String group = "";

    public double x, y, z;
    /** Follow target: player name, entity UUID or {@code tag:<tag>}. */
    public String target = "";
    public double offsetX, offsetY, offsetZ;
    /** Entities the server resolved for a tag target. */
    public List<UUID> targetIds = List.of();

    public LightShape shape = LightShape.SPHERE;
    public int color = 0xFFFFFF;
    public float radius = 1.0f;
    public float distance = 8.0f;
    public float intensity = 1.0f;
    public float atmosphere = 0.0f;
    /** Extra glow the light leaves on what it lights, whatever colour that surface is (0 = off). */
    public float luminosity = 0.0f;
    /** Full opening of cone shapes in degrees (0 = the shape's own). */
    public float angle;
    public int fadeIn;
    public int fadeOut;
    public float yaw;
    public float pitch;
    /** Stretch of the shape on its own axes (Y = where it points); negative flips it. */
    public float stretchX = 1.0f, stretchY = 1.0f, stretchZ = 1.0f;
    /** The point the stretch grows from, on the light's own axes and in its unstretched units (0 = its middle). */
    public float pivotX, pivotY, pivotZ;
    /** Follow lights: false hides the light from the followed player while they are in first person. */
    public boolean visibleFirstPerson = true;
    /** False: solid blocks stop the light (it only reaches what it can see). */
    public boolean seeThrough = true;
    /** Flashlight: ticks its direction takes to catch up with the camera (0 = none). */
    public float cameraDelay;

    public final LightAnimation animation = new LightAnimation();

    // Runtime state, never saved
    public int age;
    /**
     * Saved lights: how on its group is (0..1, fades with the light's own fades) and how much it animates, now and last
     * tick.
     */
    public float groupShown = 1.0f, groupShownBefore = 1.0f, groupMotion = 1.0f, groupMotionBefore = 1.0f;
    public boolean removing;
    public int removeAge;
    public int removeDuration;
    private Move move;
    /** Seed of random animations, stable per id. */
    public int seed;

    public Light(String id) {
        setId(id);
    }

    public void setId(String id) {
        this.id = id;
        this.seed = id.toLowerCase(Locale.ROOT).hashCode();
    }

    public boolean isStretched() {
        return stretchX != 1.0f || stretchY != 1.0f || stretchZ != 1.0f;
    }

    public boolean hasPivot() {
        return pivotX != 0.0f || pivotY != 0.0f || pivotZ != 0.0f;
    }

    public boolean isFollow() {
        return kind == Kind.FOLLOW;
    }

    /** The cone opening in use: its own angle, or the shape's. */
    public float effectiveAngle() {
        return angle > 0.0f ? angle : shape.look.angle();
    }

    public void setShape(LightShape shape, Float yaw, Float pitch) {
        if (this.shape != shape) {
            angle = 0.0f;
        }
        this.shape = shape;
        this.yaw = yaw == null ? 0.0f : yaw;
        this.pitch = pitch == null ? shape.defaultPitch : pitch;
    }

    public void tick() {
        age++;
        if (removing) {
            removeAge++;
        }
        if (move != null && age - move.startAge >= move.duration) {
            move.finish(this);
            move = null;
        }
    }

    public boolean finishedRemoving() {
        return removing && removeAge >= removeDuration;
    }

    public void startRemove(int ticks) {
        if (removing) {
            removeDuration = Math.min(removeDuration, ticks);
            return;
        }
        removing = true;
        removeAge = 0;
        removeDuration = Math.max(0, ticks);
    }

    /** Fade in/out multiplier. */
    public float alpha(float partialTick) {
        float alpha = 1.0f;
        if (fadeIn > 0) {
            alpha = Math.min(1.0f, (age + partialTick) / fadeIn);
        }
        if (removing) {
            alpha *= removeDuration <= 0 ? 0.0f : Math.max(0.0f, 1.0f - (removeAge + partialTick) / removeDuration);
        }
        return alpha * alpha * (3.0f - 2.0f * alpha);
    }

    /** Moves the light over {@code ticks}; null values keep the current setting. */
    public void startMove(Double toX, Double toY, Double toZ, Integer toColor, Float toRadius, Float toDistance, Float toIntensity, Float toAtmosphere, int ticks) {
        startMove(toX, toY, toZ, toColor, toRadius, toDistance, toIntensity, toAtmosphere, ticks, Easing.SMOOTH);
    }

    public void startMove(Double toX, Double toY, Double toZ, Integer toColor, Float toRadius, Float toDistance, Float toIntensity, Float toAtmosphere,
                          int ticks, Easing easing) {
        if (move != null) {
            // Continue from where the running move is now
            move.apply(this, move.progress(age, 0.0f));
            move = null;
        }
        Move next = new Move();
        next.easing = easing == null ? Easing.SMOOTH : easing;
        next.startAge = age;
        next.duration = Math.max(0, ticks);
        next.fromX = x;
        next.fromY = y;
        next.fromZ = z;
        next.fromOffsetX = offsetX;
        next.fromOffsetY = offsetY;
        next.fromOffsetZ = offsetZ;
        next.fromColor = color;
        next.fromRadius = radius;
        next.fromDistance = distance;
        next.fromIntensity = intensity;
        next.fromAtmosphere = atmosphere;
        boolean follow = isFollow();
        next.toX = toX == null ? (follow ? offsetX : x) : toX;
        next.toY = toY == null ? (follow ? offsetY : y) : toY;
        next.toZ = toZ == null ? (follow ? offsetZ : z) : toZ;
        next.toColor = toColor == null ? color : toColor;
        next.toRadius = toRadius == null ? radius : toRadius;
        next.toDistance = toDistance == null ? distance : toDistance;
        next.toIntensity = toIntensity == null ? intensity : toIntensity;
        next.toAtmosphere = toAtmosphere == null ? atmosphere : toAtmosphere;
        if (next.duration == 0) {
            next.finish(this);
        } else {
            move = next;
        }
    }

    /** The settings as they are this frame (moves and animations applied). */
    public Snapshot snapshot(float partialTick, LightAnimation.Result animationResult) {
        return snapshot(partialTick, animationResult, 1.0f);
    }

    /** The settings this frame; {@code motion} 0..1 is how much of the animations to use (groups fading them in or out). */
    public Snapshot snapshot(float partialTick, LightAnimation.Result animationResult, float motion) {
        Snapshot snapshot = new Snapshot();
        double px = x, py = y, pz = z, ox = offsetX, oy = offsetY, oz = offsetZ;
        int currentColor = color;
        float currentRadius = radius, currentDistance = distance, currentIntensity = intensity, currentAtmosphere = atmosphere;
        if (move != null) {
            float t = move.progress(age, partialTick);
            px = lerp(move.fromX, move.toX, t);
            py = lerp(move.fromY, move.toY, t);
            pz = lerp(move.fromZ, move.toZ, t);
            if (isFollow()) {
                px = x;
                py = y;
                pz = z;
                ox = lerp(move.fromOffsetX, move.toX, t);
                oy = lerp(move.fromOffsetY, move.toY, t);
                oz = lerp(move.fromOffsetZ, move.toZ, t);
            }
            double now = age + partialTick;
            currentColor = LightColors.lerp(LightColors.resolve(move.fromColor, now), LightColors.resolve(move.toColor, now), t);
            currentRadius = (float) lerp(move.fromRadius, move.toRadius, t);
            currentDistance = (float) lerp(move.fromDistance, move.toDistance, t);
            currentIntensity = (float) lerp(move.fromIntensity, move.toIntensity, t);
            currentAtmosphere = (float) lerp(move.fromAtmosphere, move.toAtmosphere, t);
        }
        float currentYaw = yaw, currentPitch = pitch, currentAngle = effectiveAngle();
        float w = Math.max(0.0f, Math.min(1.0f, motion));
        w = w * w * (3.0f - 2.0f * w);
        boolean animated = w > 0.0f && !animation.isEmpty();
        if (animated) {
            animation.apply(age + partialTick, seed, currentRadius, currentDistance, yaw, animationResult);
            px += animationResult.dx * w;
            py += animationResult.dy * w;
            pz += animationResult.dz * w;
            currentYaw += animationResult.yawOffset * w;
            currentPitch = Math.max(-90.0f, Math.min(90.0f, currentPitch + animationResult.pitchOffset * w));
            currentIntensity *= 1.0f + (animationResult.intensity - 1.0f) * w;
            if (animationResult.color >= 0) {
                double now = age + partialTick;
                currentColor = LightColors.lerp(LightColors.resolve(currentColor, now), animationResult.color, w);
            }
            currentRadius += (animationResult.radius * animationResult.reach - currentRadius) * w;
            currentDistance += (animationResult.distance - currentDistance) * w;
            if (currentAngle > 0.0f) {
                currentAngle = Math.max(1.0f, Math.min(LightShape.MAX_ANGLE, currentAngle + animationResult.angleOffset * w));
            }
        }
        snapshot.x = px;
        snapshot.y = py;
        snapshot.z = pz;
        snapshot.offsetX = ox;
        snapshot.offsetY = oy;
        snapshot.offsetZ = oz;
        snapshot.color = LightColors.resolve(currentColor, age + partialTick);
        snapshot.radius = currentRadius;
        snapshot.distance = currentDistance;
        snapshot.alpha = alpha(partialTick);
        snapshot.intensity = currentIntensity * snapshot.alpha;
        snapshot.atmosphere = currentAtmosphere;
        snapshot.yaw = currentYaw;
        snapshot.pitch = currentPitch;
        snapshot.angle = currentAngle;
        snapshot.roll = animated ? animationResult.rollOffset * w : 0.0f;
        return snapshot;
    }

    /** Animated values of one frame. */
    public static final class Snapshot {
        public double x, y, z, offsetX, offsetY, offsetZ;
        public int color;
        public float radius, distance, intensity, atmosphere, yaw, pitch, angle, roll;
        /** Fade in/out, already applied to {@link #intensity} but not to {@link #atmosphere}. */
        public float alpha;
    }

    private static double lerp(double from, double to, float t) {
        return from + (to - from) * t;
    }

    /** How a move gets from one value to the next. */
    public enum Easing {
        /** Slow start, slow stop, nothing abrupt anywhere (the nicest for lights). */
        SMOOTH("smooth"),
        /** The same speed all the way. */
        LINEAR("linear"),
        /** Eight little hops: it waits, jumps to the next step, waits again. */
        STEP("step"),
        /** Arrives fast and bounces a few times, shorter each time, before settling. */
        BOUNCE("bounce");

        public final String id;

        Easing(String id) {
            this.id = id;
        }

        public static Easing of(String name) {
            for (Easing easing : values()) {
                if (easing.id.equalsIgnoreCase(name)) {
                    return easing;
                }
            }
            return SMOOTH;
        }

        /** 0 at the start, 1 at the end (bounce goes past 1 in between and comes back). */
        public float ease(float t) {
            float time = Math.max(0.0f, Math.min(1.0f, t));
            switch (this) {
                case LINEAR:
                    return time;
                case STEP: {
                    // Each step waits, then hops over in the last part of its slot
                    float steps = 8.0f;
                    float scaled = time * steps;
                    float index = (float) Math.floor(scaled);
                    float local = Math.min(1.0f, (scaled - index) / 0.45f);
                    float hop = local * local * (3.0f - 2.0f * local);
                    return Math.min(1.0f, (index + hop) / steps);
                }
                case BOUNCE: {
                    // Lands, then three smaller and smaller bounces
                    float n = 7.5625f;
                    float d = 2.75f;
                    if (time < 1.0f / d) {
                        return n * time * time;
                    }
                    if (time < 2.0f / d) {
                        float shifted = time - 1.5f / d;
                        return n * shifted * shifted + 0.75f;
                    }
                    if (time < 2.5f / d) {
                        float shifted = time - 2.25f / d;
                        return n * shifted * shifted + 0.9375f;
                    }
                    float shifted = time - 2.625f / d;
                    return n * shifted * shifted + 0.984375f;
                }
                default:
                    // Smootherstep: it leaves and arrives with no jolt at all
                    return time * time * time * (time * (time * 6.0f - 15.0f) + 10.0f);
            }
        }
    }

    private static final class Move {
        int startAge, duration;
        Easing easing = Easing.SMOOTH;
        double fromX, fromY, fromZ, toX, toY, toZ, fromOffsetX, fromOffsetY, fromOffsetZ;
        int fromColor, toColor;
        float fromRadius, toRadius, fromDistance, toDistance, fromIntensity, toIntensity, fromAtmosphere, toAtmosphere;

        float progress(int age, float partialTick) {
            if (duration <= 0) {
                return 1.0f;
            }
            float t = Math.max(0.0f, Math.min(1.0f, (age - startAge + partialTick) / duration));
            return easing.ease(t);
        }

        void finish(Light light) {
            apply(light, 1.0f);
        }

        void apply(Light light, float t) {
            if (light.isFollow()) {
                light.offsetX = lerp(fromOffsetX, toX, t);
                light.offsetY = lerp(fromOffsetY, toY, t);
                light.offsetZ = lerp(fromOffsetZ, toZ, t);
            } else {
                light.x = lerp(fromX, toX, t);
                light.y = lerp(fromY, toY, t);
                light.z = lerp(fromZ, toZ, t);
            }
            light.color = t >= 1.0f ? toColor : t <= 0.0f ? fromColor
                    : LightColors.lerp(LightColors.resolve(fromColor, light.age), LightColors.resolve(toColor, light.age), t);
            light.radius = (float) lerp(fromRadius, toRadius, t);
            light.distance = (float) lerp(fromDistance, toDistance, t);
            light.intensity = (float) lerp(fromIntensity, toIntensity, t);
            light.atmosphere = (float) lerp(fromAtmosphere, toAtmosphere, t);
        }
    }

    public Light copy(String newId) {
        Light copy = fromJson(toJson());
        copy.setId(newId);
        copy.server = server;
        return copy;
    }

    /** The light as it will be once its running move ends (what a world file keeps, so leaving mid-move loses nothing). */
    public JsonObject toSavedJson() {
        if (move == null) {
            return toJson();
        }
        Light settled = copy(id);
        move.apply(settled, 1.0f);
        settled.move = null;
        return settled.toJson();
    }

    public JsonObject toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("id", id);
        object.addProperty("kind", kind.name().toLowerCase(Locale.ROOT));
        if (dimension != null) {
            object.addProperty("dimension", dimension);
        }
        if (group != null && !group.isEmpty()) {
            object.addProperty("group", group);
        }
        // Follow lights and a flashlight given to someone
        if (isFollow() || (target != null && !target.isEmpty())) {
            object.addProperty("target", target);
        }
        if (isFollow()) {
            object.addProperty("offsetX", offsetX);
            object.addProperty("offsetY", offsetY);
            object.addProperty("offsetZ", offsetZ);
        } else {
            object.addProperty("x", x);
            object.addProperty("y", y);
            object.addProperty("z", z);
        }
        object.addProperty("shape", shape.id);
        object.addProperty("color", LightColors.format(color));
        object.addProperty("radius", radius);
        object.addProperty("distance", distance);
        object.addProperty("intensity", intensity);
        object.addProperty("atmosphere", atmosphere);
        if (luminosity > 0.0f) {
            object.addProperty("luminosity", luminosity);
        }
        object.addProperty("fadeIn", fadeIn);
        object.addProperty("fadeOut", fadeOut);
        object.addProperty("yaw", yaw);
        object.addProperty("pitch", pitch);
        if (shape.hasAngle()) {
            object.addProperty("angle", effectiveAngle());
        }
        if (isStretched()) {
            object.addProperty("stretchX", stretchX);
            object.addProperty("stretchY", stretchY);
            object.addProperty("stretchZ", stretchZ);
        }
        if (hasPivot()) {
            object.addProperty("pivotX", pivotX);
            object.addProperty("pivotY", pivotY);
            object.addProperty("pivotZ", pivotZ);
        }
        if (!visibleFirstPerson) {
            object.addProperty("visibleFirstPerson", false);
        }
        if (!seeThrough) {
            object.addProperty("seethrough", false);
        }
        if (cameraDelay > 0.0f) {
            object.addProperty("cameraDelay", cameraDelay);
        }
        if (!animation.isEmpty()) {
            object.add("animation", animation.write());
        }
        return object;
    }

    public static Light fromJson(JsonObject object) {
        Light light = new Light(getString(object, "id", "light"));
        light.kind = "follow".equalsIgnoreCase(getString(object, "kind", "static")) ? Kind.FOLLOW : Kind.STATIC;
        light.dimension = object.has("dimension") && !object.get("dimension").isJsonNull() ? object.get("dimension").getAsString() : null;
        light.x = getDouble(object, "x", 0.0d);
        light.y = getDouble(object, "y", 0.0d);
        light.z = getDouble(object, "z", 0.0d);
        light.target = getString(object, "target", "");
        light.group = getString(object, "group", "");
        light.offsetX = getDouble(object, "offsetX", 0.0d);
        light.offsetY = getDouble(object, "offsetY", 0.0d);
        light.offsetZ = getDouble(object, "offsetZ", 0.0d);
        if (object.has("targetIds")) {
            List<UUID> ids = new ArrayList<>();
            for (JsonElement element : object.getAsJsonArray("targetIds")) {
                try {
                    ids.add(UUID.fromString(element.getAsString()));
                } catch (IllegalArgumentException ignored) {
                }
            }
            light.targetIds = List.copyOf(ids);
        }
        LightShape shape = LightShape.byName(getString(object, "shape", "sphere"));
        light.shape = shape == null ? LightShape.SPHERE : shape;
        Integer color = LightColors.parse(getString(object, "color", "#FFFFFF"));
        light.color = color == null ? 0xFFFFFF : color;
        light.radius = (float) getDouble(object, "radius", 1.0d);
        light.distance = (float) getDouble(object, "distance", 8.0d);
        light.intensity = (float) getDouble(object, "intensity", 1.0d);
        light.atmosphere = (float) getDouble(object, "atmosphere", 0.0d);
        light.luminosity = (float) getDouble(object, "luminosity", 0.0d);
        light.fadeIn = (int) getDouble(object, "fadeIn", 0.0d);
        light.fadeOut = (int) getDouble(object, "fadeOut", 0.0d);
        light.yaw = (float) getDouble(object, "yaw", 0.0d);
        light.pitch = (float) getDouble(object, "pitch", light.shape.defaultPitch);
        light.angle = (float) getDouble(object, "angle", 0.0d);
        light.stretchX = (float) getDouble(object, "stretchX", 1.0d);
        light.stretchY = (float) getDouble(object, "stretchY", 1.0d);
        light.stretchZ = (float) getDouble(object, "stretchZ", 1.0d);
        light.pivotX = (float) getDouble(object, "pivotX", 0.0d);
        light.pivotY = (float) getDouble(object, "pivotY", 0.0d);
        light.pivotZ = (float) getDouble(object, "pivotZ", 0.0d);
        light.visibleFirstPerson = !object.has("visibleFirstPerson") || object.get("visibleFirstPerson").getAsBoolean();
        light.seeThrough = !object.has("seethrough") || object.get("seethrough").getAsBoolean();
        light.cameraDelay = (float) getDouble(object, "cameraDelay", 0.0d);
        if (object.has("animation")) {
            light.animation.read(object.getAsJsonObject("animation"));
        }
        return light;
    }

    public JsonArray targetIdsJson() {
        JsonArray array = new JsonArray();
        for (UUID uuid : targetIds) {
            array.add(uuid.toString());
        }
        return array;
    }

    static String getString(JsonObject object, String key, String fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback;
    }

    static double getDouble(JsonObject object, String key, double fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsDouble() : fallback;
    }
}
