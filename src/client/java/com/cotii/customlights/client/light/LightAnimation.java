package com.cotii.customlights.client.light;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Looping animations of a light. */
public final class LightAnimation {
    /** What a preset's amount means, for the editor and the defaults. */
    public enum AmountKind {
        RADIUS("Radius", 0.0f, 32.0f, 0.1f, 2.0f),
        DEPTH("Depth", 0.0f, 1.0f, 0.01f, 0.9f),
        STRENGTH("Strength", 0.0f, 1.0f, 0.01f, 1.0f),
        DUTY("On time", 0.05f, 0.95f, 0.01f, 0.5f),
        SATURATION("Saturation", 0.0f, 1.0f, 0.01f, 0.85f),
        STEPS("Colors/loop", 1.0f, 16.0f, 1.0f, 4.0f),
        TURN("Degrees", 0.0f, 720.0f, 1.0f, 360.0f),
        ANGLE("Degrees", 0.0f, 180.0f, 1.0f, 20.0f);

        public final String label;
        public final float min, max, step, defaultAmount;

        AmountKind(String label, float min, float max, float step, float defaultAmount) {
            this.label = label;
            this.min = min;
            this.max = max;
            this.step = step;
            this.defaultAmount = defaultAmount;
        }
    }

    public enum Preset {
        /** Horizontal circle. */
        CIRCLE(AmountKind.RADIUS),
        /** Smooth random wander. */
        RANDOM(AmountKind.RADIUS),
        /** Vertical circle facing the light's yaw. */
        ORBIT(AmountKind.RADIUS),
        /** Horizontal figure eight. */
        FIGURE8(AmountKind.RADIUS),
        /** Circle that also rises and falls. */
        SPIRAL(AmountKind.RADIUS),
        /** Bounces up. */
        BOUNCE(AmountKind.RADIUS),
        /** Swings side to side. */
        SWAY(AmountKind.RADIUS),
        /** Moves up and down smoothly. */
        FLOAT(AmountKind.RADIUS),
        /** Intensity goes up and down. */
        PULSE(AmountKind.DEPTH),
        /** Fire or broken bulb flicker. */
        FLICKER(AmountKind.STRENGTH),
        /** Hard on/off. */
        STROBE(AmountKind.DUTY),
        /** Slow fade out and in. */
        BREATHE(AmountKind.DEPTH),
        /** Cycles the hue. */
        RAINBOW(AmountKind.SATURATION),
        /** Jumps between random bright colors. */
        DISCO(AmountKind.STEPS),
        /** Turns the light's direction around. */
        SWEEP(AmountKind.TURN),
        /** Turns the light's direction back and forth. */
        SCAN(AmountKind.ANGLE),
        /** Tilts the light's direction in a circle, like a searchlight. */
        WOBBLE(AmountKind.ANGLE),
        /** Two quick beats, then a rest. */
        HEARTBEAT(AmountKind.DEPTH),
        /** Soft, calm flame. */
        CANDLE(AmountKind.STRENGTH),
        /** Dark with sudden bright flashes. */
        LIGHTNING(AmountKind.STRENGTH),
        /** Zigzags side to side. */
        ZIGZAG(AmountKind.RADIUS),
        /** Tilts its direction up and down like a pendulum. */
        PENDULUM(AmountKind.ANGLE),
        /** Switches between red and blue. */
        POLICE(AmountKind.SATURATION),
        /** Jumps and cuts out at random. */
        GLITCH(AmountKind.STRENGTH),
        /** Drifts slowly and blinks, like a firefly. */
        FIREFLY(AmountKind.RADIUS),
        /** Stage light: the beam draws a figure eight from where it starts. */
        BALLYHOO(AmountKind.ANGLE),
        /** Stage light: the beam looks around smoothly at random. */
        SEARCH(AmountKind.ANGLE),
        /** Stage light: the beam glides between four aim points, resting on each. */
        CHASE(AmountKind.ANGLE),
        /** The beam grows out of its start and pulls back in. */
        GROW(AmountKind.DEPTH),
        /** The beam shoots out of its start, stays, then fades. */
        SHOOT(AmountKind.DUTY),
        /** The beam opens and closes. */
        IRIS(AmountKind.ANGLE),
        /** Turns around its own axis, clockwise (sirens). */
        ROTATION(AmountKind.TURN);

        public final String id = name().toLowerCase(Locale.ROOT);
        public final AmountKind amount;

        Preset(AmountKind amount) {
            this.amount = amount;
        }

        public float defaultAmount() {
            return switch (this) {
                case WOBBLE -> 16.0f;
                case PENDULUM -> 30.0f;
                case POLICE -> 1.0f;
                case GLITCH -> 0.3f;
                case FIREFLY -> 1.0f;
                case BALLYHOO -> 25.0f;
                case SEARCH, CHASE -> 30.0f;
                case GROW -> 1.0f;
                case SHOOT -> 0.6f;
                case IRIS -> 12.0f;
                default -> amount.defaultAmount;
            };
        }

        public boolean moves() {
            return amount == AmountKind.RADIUS;
        }

        /** Presets that only make sense on cones and beams. */
        public boolean forBeams() {
            return this == ROTATION || this == BALLYHOO || this == SEARCH || this == CHASE || this == GROW || this == SHOOT || this == IRIS;
        }

        public static Preset byName(String name) {
            String key = name.toLowerCase(Locale.ROOT);
            for (Preset preset : values()) {
                if (preset.id.equals(key)) {
                    return preset;
                }
            }
            return switch (key) {
                case "figure_eight", "eight", "infinity" -> FIGURE8;
                case "wander" -> RANDOM;
                case "fire", "candle" -> FLICKER;
                case "blink" -> STROBE;
                case "rotate", "spin" -> SWEEP;
                case "searchlight" -> WOBBLE;
                case "pan" -> SCAN;
                case "tilt" -> PENDULUM;
                case "reveal", "extend" -> GROW;
                case "rotate_axis", "roll", "siren" -> ROTATION;
                default -> null;
            };
        }

        public static List<String> names() {
            List<String> names = new ArrayList<>();
            for (Preset preset : values()) {
                names.add(preset.id);
            }
            return names;
        }
    }

    /** One running preset with its own settings. */
    public static final class Entry {
        public final Preset preset;
        /** Ticks of one loop. */
        public int time;
        public float amount;

        public Entry(Preset preset, int time, float amount) {
            this.preset = preset;
            this.time = Math.max(1, time);
            this.amount = amount;
        }
    }

    /** What the animations change this frame; {@link #color} is -1 when the light keeps its own. */
    public static final class Result {
        public double dx, dy, dz;
        public float yawOffset, pitchOffset;
        /** Degrees added to a cone's opening. */
        public float angleOffset;
        /** Degrees turned around the light's own axis. */
        public float rollOffset;
        /** Multiplies the length of cones and beams (and the size of other shapes). */
        public float reach = 1.0f;
        public float intensity = 1.0f;
        public int color = -1;
        public float radius, distance;
    }

    public static final int DEFAULT_TIME = 40;

    public final List<Entry> presets = new ArrayList<>();
    /** Movement radius every moving preset pulses to; used when {@link #radiusPulseTime} is above 0. */
    public float radiusPulseTarget = 2.0f;
    /** Ticks of a full movement radius pulse; 0 for none. */
    public int radiusPulseTime;
    /** Color transition: each color and the ticks it takes to blend into the next one (the last blends into the first). */
    public int[] colors = new int[0];
    public int[] colorTimes = new int[0];
    public float sizeRadius, sizeDistance;
    /** Ticks of a full size pulse; 0 for none. */
    public int sizeTime;

    public boolean isEmpty() {
        return presets.isEmpty() && colors.length < 2 && sizeTime <= 0;
    }

    public boolean hasPreset() {
        return !presets.isEmpty();
    }

    public Entry entry(Preset preset) {
        for (Entry entry : presets) {
            if (entry.preset == preset) {
                return entry;
            }
        }
        return null;
    }

    /** Adds the preset or updates its settings; {@code amount} null keeps the current (or default) amount. */
    public Entry put(Preset preset, int time, Float amount) {
        Entry entry = entry(preset);
        if (entry == null) {
            entry = new Entry(preset, time, amount == null ? preset.defaultAmount() : amount);
            presets.add(entry);
        } else {
            entry.time = Math.max(1, time);
            if (amount != null) {
                entry.amount = amount;
            }
        }
        return entry;
    }

    public boolean remove(Preset preset) {
        return presets.removeIf(entry -> entry.preset == preset);
    }

    /** Sets the radius of every moving preset. */
    public void setMovementRadius(float radius) {
        for (Entry entry : presets) {
            if (entry.preset.moves()) {
                entry.amount = radius;
            }
        }
    }

    public void clear() {
        presets.clear();
        colors = new int[0];
        colorTimes = new int[0];
        sizeTime = 0;
        radiusPulseTime = 0;
    }

    /** Presets from "circle+pulse", or null when a name is unknown. */
    public static List<Preset> parsePresets(String text) {
        List<Preset> presets = new ArrayList<>();
        for (String part : text.split("[+,]")) {
            if (part.isBlank()) {
                continue;
            }
            Preset preset = Preset.byName(part.trim());
            if (preset == null) {
                return null;
            }
            if (!presets.contains(preset)) {
                presets.add(preset);
            }
        }
        return presets.isEmpty() ? null : presets;
    }

    /** Fills {@code out} for the given time in ticks. */
    public void apply(double time, int seed, float baseRadius, float baseDistance, float yaw, Result out) {
        out.dx = out.dy = out.dz = 0.0d;
        out.yawOffset = out.pitchOffset = 0.0f;
        out.angleOffset = 0.0f;
        out.rollOffset = 0.0f;
        out.reach = 1.0f;
        out.intensity = 1.0f;
        out.color = -1;
        out.radius = baseRadius;
        out.distance = baseDistance;

        if (sizeTime > 0) {
            float wave = wave(time, sizeTime);
            out.radius = baseRadius + (sizeRadius - baseRadius) * wave;
            out.distance = baseDistance + (sizeDistance - baseDistance) * wave;
        }
        if (colors.length >= 2) {
            out.color = transitionColor(time);
        }
        if (presets.isEmpty()) {
            return;
        }
        float radiusWave = radiusPulseTime > 0 ? wave(time, radiusPulseTime) : 0.0f;
        double seedPhase = (seed & 0xFFFF) / 65535.0d * 100.0d;
        for (Entry entry : presets) {
            int period = entry.time;
            double phase = time / period * Math.PI * 2.0d;
            double loop = ((time % period) + period) % period / period;
            float amount = entry.amount;
            if (entry.preset.moves() && radiusPulseTime > 0) {
                amount += (radiusPulseTarget - amount) * radiusWave;
            }
            switch (entry.preset) {
                case CIRCLE -> {
                    out.dx += Math.cos(phase) * amount;
                    out.dz += Math.sin(phase) * amount;
                }
                case RANDOM -> {
                    double t = time / period * 2.0d + seedPhase;
                    out.dx += noise(t, 11) * amount;
                    out.dy += noise(t, 23) * amount * 0.5d;
                    out.dz += noise(t, 37) * amount;
                }
                case ORBIT -> {
                    double yawRad = Math.toRadians(yaw);
                    double side = Math.cos(phase) * amount;
                    out.dx += Math.cos(yawRad) * side;
                    out.dz += Math.sin(yawRad) * side;
                    out.dy += Math.sin(phase) * amount;
                }
                case FIGURE8 -> {
                    out.dx += Math.sin(phase) * amount;
                    out.dz += Math.sin(phase * 2.0d) * amount * 0.5d;
                }
                case SPIRAL -> {
                    out.dx += Math.cos(phase) * amount;
                    out.dz += Math.sin(phase) * amount;
                    // Rises and falls over four turns
                    out.dy += Math.sin(phase * 0.25d) * amount * 0.5d;
                }
                case BOUNCE -> {
                    // Lifts off and lands softly
                    double hop = Math.sin(loop * Math.PI);
                    out.dy += hop * hop * (3.0d - 2.0d * hop) * amount;
                }
                case SWAY -> out.dx += Math.sin(phase) * amount;
                case FLOAT -> out.dy += Math.sin(phase) * amount * 0.5d;
                case PULSE -> out.intensity *= 1.0f - clamp01(amount) * (0.5f - 0.5f * (float) Math.sin(phase));
                case FLICKER -> {
                    double t = time * 0.9d * (DEFAULT_TIME / (double) period) + seedPhase;
                    float flicker = 0.8f + 0.2f * (float) noise(t, 5) + 0.1f * (float) noise(t * 2.3d, 9);
                    // Dips blend in and out instead of cutting
                    float dip = smooth((float) clamp((noise(t * 0.35d, 71) - 0.45d) / 0.3d));
                    flicker *= 1.0f - 0.6f * dip;
                    out.intensity *= 1.0f + (Math.max(0.0f, flicker) - 1.0f) * clamp01(amount);
                }
                case STROBE -> {
                    // On for the duty part of the loop, with short soft edges
                    double duty = Math.max(0.05d, Math.min(0.95d, amount));
                    double edge = Math.min(0.05d, Math.min(duty, 1.0d - duty) * 0.5d);
                    out.intensity *= (float) (smoothRange(loop, 0.0d, edge) * (1.0d - smoothRange(loop, duty, duty + edge)));
                }
                case BREATHE -> {
                    float wave = 0.5f - 0.5f * (float) Math.cos(phase);
                    out.intensity *= 1.0f - clamp01(amount) * (1.0f - wave * wave);
                }
                case RAINBOW -> out.color = LightColors.fromHsv((float) (loop * 360.0d), clamp01(amount), 1.0f);
                case DISCO -> {
                    // Holds each color, then blends quickly into the next
                    double steps = Math.max(1.0d, Math.round(amount));
                    double position = time / Math.max(1.0d, period / steps);
                    long step = (long) Math.floor(position);
                    float blend = smooth((float) clamp((position - step - 0.75d) / 0.25d));
                    int from = LightColors.fromHsv((hash((int) step * 31 + seed) & 0xFFFF) / 65535.0f * 360.0f, 0.9f, 1.0f);
                    int to = LightColors.fromHsv((hash((int) (step + 1) * 31 + seed) & 0xFFFF) / 65535.0f * 360.0f, 0.9f, 1.0f);
                    out.color = LightColors.lerp(from, to, blend);
                }
                // Keeps turning: no jump back at the end of a loop
                case SWEEP -> out.yawOffset += (float) ((time / period) * amount % 360.0d);
                case SCAN -> out.yawOffset += (float) Math.sin(phase) * amount;
                case WOBBLE -> {
                    out.yawOffset += (float) Math.cos(phase) * amount;
                    out.pitchOffset += (float) Math.sin(phase) * amount;
                }
                case HEARTBEAT -> {
                    double beat = bump(loop, 0.1d) + 0.7d * bump(loop, 0.3d) + bump(loop, 1.1d) + 0.7d * bump(loop, -0.7d);
                    out.intensity *= 1.0f - clamp01(amount) + clamp01(amount) * (float) Math.min(1.0d, beat);
                }
                case CANDLE -> {
                    double t = time * 0.25d * (DEFAULT_TIME / (double) period) + seedPhase;
                    float flame = 0.88f + 0.08f * (float) noise(t, 3) + 0.04f * (float) noise(t * 2.7d, 13);
                    out.intensity *= 1.0f + (flame - 1.0f) * clamp01(amount) * 2.0f;
                    out.dy += noise(t * 0.8d, 17) * 0.03d * clamp01(amount);
                }
                case LIGHTNING -> {
                    // Flashes strike at random and die away quickly
                    double slotLength = Math.max(1.0d, period / 8.0d);
                    double slot = time / slotLength;
                    long index = (long) Math.floor(slot);
                    double flash = 0.0d;
                    for (long k = index - 2; k <= index; k++) {
                        int h = hash((int) k * 977 + seed);
                        if ((h & 0xFF) < 60) {
                            double since = (slot - k) * slotLength - ((h >> 8) & 0x7) * 0.5d;
                            if (since >= 0.0d) {
                                flash = Math.max(flash, Math.exp(-since * 0.45d) * (0.6d + 0.4d * Math.abs(Math.cos(since * 2.5d))));
                            }
                        }
                    }
                    float dark = 1.0f - clamp01(amount) * 0.85f;
                    out.intensity *= dark + (1.0f - dark) * (float) Math.min(1.0d, flash);
                }
                case ZIGZAG -> {
                    // A triangle wave with rounded corners
                    double triangle = (2.0d / Math.PI) * Math.asin(0.985d * Math.sin(phase));
                    out.dx += triangle * amount;
                    out.dz += Math.sin(phase * 2.0d) * amount * 0.15d;
                }
                case PENDULUM -> out.pitchOffset += (float) Math.sin(phase) * amount;
                case POLICE -> {
                    float blend = smooth((float) clamp(Math.sin(phase) * 1.5d + 0.5d));
                    int red = LightColors.fromHsv(0.0f, clamp01(amount), 1.0f);
                    int blue = LightColors.fromHsv(225.0f, clamp01(amount), 1.0f);
                    out.color = LightColors.lerp(blue, red, blend);
                }
                case GLITCH -> {
                    long step = (long) Math.floor(time / Math.max(1.0d, period / 10.0d));
                    int hash = hash((int) step * 131 + seed);
                    if ((hash & 0xFF) < 70) {
                        out.dx += ((hash >> 8 & 0xFF) / 255.0d - 0.5d) * amount;
                        out.dy += ((hash >> 16 & 0xFF) / 255.0d - 0.5d) * amount;
                        out.intensity *= (hash & 1) == 0 ? 0.15f : 1.0f;
                    }
                }
                case FIREFLY -> {
                    double t = time / period + seedPhase;
                    out.dx += noise(t, 41) * amount;
                    out.dy += noise(t, 43) * amount * 0.6d;
                    out.dz += noise(t, 47) * amount;
                    float blink = (float) (0.5d - 0.5d * Math.cos(phase));
                    out.intensity *= 0.15f + 0.85f * blink * blink;
                }
                case BALLYHOO -> {
                    out.yawOffset += (float) Math.sin(phase) * amount;
                    out.pitchOffset += (float) Math.sin(phase * 2.0d) * amount * 0.4f;
                }
                case SEARCH -> {
                    double t = time / period * 1.5d + seedPhase;
                    out.yawOffset += (float) noise(t, 53) * amount;
                    out.pitchOffset += (float) noise(t, 59) * amount * 0.6f;
                }
                case CHASE -> {
                    // Four aim points around the start, gliding between them and resting on each
                    double position = loop * 4.0d;
                    int from = Math.min(3, (int) Math.floor(position));
                    int to = (from + 1) % 4;
                    float glide = smootherstep((float) clamp((position - from) / 0.6d));
                    out.yawOffset += (float) lerp(CHASE_YAW[from], CHASE_YAW[to], glide) * amount;
                    out.pitchOffset += (float) lerp(CHASE_PITCH[from], CHASE_PITCH[to], glide) * amount;
                }
                case GROW -> {
                    // Grows out of its start, holds, and pulls back in
                    double grow = smootherstep((float) clamp(loop / 0.4d)) * (1.0d - smootherstep((float) clamp((loop - 0.7d) / 0.3d)));
                    out.reach *= (float) Math.max(0.02d, 1.0d - clamp01(amount) + clamp01(amount) * grow);
                }
                case SHOOT -> {
                    // Shoots out fast, stays for the on time, then fades where it is
                    double hold = Math.max(0.15d, Math.min(0.8d, amount));
                    double extend = 1.0d - Math.pow(1.0d - clamp(loop / 0.12d), 3.0d);
                    double fade = 1.0d - smootherstep((float) clamp((loop - hold) / 0.2d));
                    out.reach *= (float) Math.max(0.02d, extend);
                    out.intensity *= (float) fade;
                }
                case IRIS -> out.angleOffset += (float) Math.sin(phase) * amount;
                case ROTATION -> out.rollOffset += (float) ((time / period) * amount % 360.0d);
            }
        }
    }

    private static final double[] CHASE_YAW = {-1.0d, 1.0d, 1.0d, -1.0d};
    private static final double[] CHASE_PITCH = {-0.5d, -0.5d, 0.5d, 0.5d};

    public int colorTime(int index) {
        return index < colorTimes.length ? Math.max(1, colorTimes[index]) : DEFAULT_TIME;
    }

    /** Sets the colors of the transition, each with the ticks it takes to reach the next one. */
    public void setTransition(int[] newColors, int[] newTimes) {
        colors = newColors.clone();
        colorTimes = new int[newColors.length];
        for (int i = 0; i < newColors.length; i++) {
            colorTimes[i] = i < newTimes.length ? Math.max(1, newTimes[i]) : DEFAULT_TIME;
        }
    }

    private int transitionColor(double time) {
        int total = 0;
        for (int i = 0; i < colors.length; i++) {
            total += colorTime(i);
        }
        double position = ((time % total) + total) % total;
        for (int i = 0; i < colors.length; i++) {
            int length = colorTime(i);
            if (position < length || i == colors.length - 1) {
                // Half eased, half linear: soft at each color without stopping on it
                float linear = (float) Math.min(1.0d, position / length);
                float fraction = (linear + smooth(linear)) * 0.5f;
                int from = LightColors.resolve(colors[i], time);
                int to = LightColors.resolve(colors[(i + 1) % colors.length], time);
                return LightColors.lerp(from, to, fraction);
            }
            position -= length;
        }
        return LightColors.resolve(colors[0], time);
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    /** 0 → 1 → 0 over the period as a cosine, so it never stops sharply. */
    private static float wave(double time, int period) {
        return (float) (0.5d - 0.5d * Math.cos(time / period * Math.PI * 2.0d));
    }

    private static float smooth(float t) {
        return t * t * (3.0f - 2.0f * t);
    }

    private static float smootherstep(float t) {
        return t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f);
    }

    private static double clamp(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    /** 0 before {@code from}, 1 after {@code to}, eased in between. */
    private static double smoothRange(double value, double from, double to) {
        if (to <= from) {
            return value >= from ? 1.0d : 0.0d;
        }
        return smooth((float) clamp((value - from) / (to - from)));
    }

    private static double bump(double value, double center) {
        double x = (value - center) * 14.0d;
        return Math.exp(-x * x);
    }

    private static double lerp(double from, double to, double t) {
        return from + (to - from) * t;
    }

    /** Smooth value noise in -1..1 (Catmull-Rom through random points, so it never pauses at them). */
    private static double noise(double t, int channel) {
        long cell = (long) Math.floor(t);
        double f = t - cell;
        double p0 = point(cell - 1, channel);
        double p1 = point(cell, channel);
        double p2 = point(cell + 1, channel);
        double p3 = point(cell + 2, channel);
        double value = 0.5d * ((2.0d * p1) + (-p0 + p2) * f + (2.0d * p0 - 5.0d * p1 + 4.0d * p2 - p3) * f * f
                + (-p0 + 3.0d * p1 - 3.0d * p2 + p3) * f * f * f);
        return Math.max(-1.0d, Math.min(1.0d, value));
    }

    private static double point(long cell, int channel) {
        return hash((int) cell * 7919 + channel * 104729) / (double) Integer.MAX_VALUE * 2.0d - 1.0d;
    }

    private static int hash(int value) {
        int h = value * 0x9E3779B1;
        h ^= h >>> 15;
        h *= 0x85EBCA77;
        h ^= h >>> 13;
        return h & Integer.MAX_VALUE;
    }

    public LightAnimation copy() {
        LightAnimation copy = new LightAnimation();
        copy.read(write());
        return copy;
    }

    public JsonObject write() {
        JsonObject object = new JsonObject();
        if (!presets.isEmpty()) {
            JsonArray array = new JsonArray();
            for (Entry entry : presets) {
                JsonObject preset = new JsonObject();
                preset.addProperty("preset", entry.preset.id);
                preset.addProperty("time", entry.time);
                preset.addProperty("amount", entry.amount);
                array.add(preset);
            }
            object.add("presets", array);
        }
        if (radiusPulseTime > 0) {
            object.addProperty("radiusPulseTarget", radiusPulseTarget);
            object.addProperty("radiusPulseTime", radiusPulseTime);
        }
        if (colors.length >= 2) {
            JsonArray array = new JsonArray();
            for (int i = 0; i < colors.length; i++) {
                array.add(LightColors.format(colors[i]) + ":" + colorTime(i));
            }
            object.add("colors", array);
        }
        if (sizeTime > 0) {
            object.addProperty("sizeRadius", sizeRadius);
            object.addProperty("sizeDistance", sizeDistance);
            object.addProperty("sizeTime", sizeTime);
        }
        return object;
    }

    public void read(JsonObject object) {
        clear();
        if (object.has("presets")) {
            for (JsonElement element : object.getAsJsonArray("presets")) {
                JsonObject preset = element.getAsJsonObject();
                Preset type = Preset.byName(preset.has("preset") ? preset.get("preset").getAsString() : "");
                if (type != null) {
                    put(type, preset.has("time") ? preset.get("time").getAsInt() : DEFAULT_TIME,
                            preset.has("amount") ? preset.get("amount").getAsFloat() : null);
                }
            }
        } else if (object.has("preset")) {
            // Older format: one time and one movement radius for every preset
            int time = object.has("presetTime") ? object.get("presetTime").getAsInt() : DEFAULT_TIME;
            List<Preset> parsed = parsePresets(object.get("preset").getAsString());
            if (parsed != null) {
                for (Preset preset : parsed) {
                    put(preset, time, null);
                }
            }
            if (object.has("animationRadius")) {
                setMovementRadius(object.get("animationRadius").getAsFloat());
            }
        }
        if (object.has("radiusPulseTime")) {
            radiusPulseTime = object.get("radiusPulseTime").getAsInt();
            radiusPulseTarget = object.has("radiusPulseTarget") ? object.get("radiusPulseTarget").getAsFloat() : 2.0f;
        } else if (object.has("animationRadiusTime")) {
            radiusPulseTime = object.get("animationRadiusTime").getAsInt();
            radiusPulseTarget = object.has("animationRadiusTarget") ? object.get("animationRadiusTarget").getAsFloat() : 2.0f;
        }
        if (object.has("colors")) {
            List<Integer> parsedColors = new ArrayList<>();
            List<Integer> parsedTimes = new ArrayList<>();
            JsonArray array = object.getAsJsonArray("colors");
            // Older format: one loop time shared by every color
            int shared = object.has("colorTime") ? Math.max(1, object.get("colorTime").getAsInt() / Math.max(1, array.size())) : DEFAULT_TIME;
            for (JsonElement element : array) {
                String text = element.getAsString();
                int colon = text.lastIndexOf(':');
                Integer color = LightColors.parse(colon > 0 ? text.substring(0, colon) : text);
                if (color == null) {
                    continue;
                }
                int ticks = shared;
                if (colon > 0) {
                    try {
                        ticks = Integer.parseInt(text.substring(colon + 1));
                    } catch (NumberFormatException ignored) {
                    }
                }
                parsedColors.add(color);
                parsedTimes.add(ticks);
            }
            setTransition(parsedColors.stream().mapToInt(Integer::intValue).toArray(), parsedTimes.stream().mapToInt(Integer::intValue).toArray());
        }
        if (object.has("sizeTime")) {
            sizeTime = object.get("sizeTime").getAsInt();
            sizeRadius = object.has("sizeRadius") ? object.get("sizeRadius").getAsFloat() : 1.0f;
            sizeDistance = object.has("sizeDistance") ? object.get("sizeDistance").getAsFloat() : 1.0f;
        }
    }
}
