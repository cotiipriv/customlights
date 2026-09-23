package com.cotii.customlights.client.light;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** How a light is shaped. */
public enum LightShape {
    /** Ball of {@code radius}. */
    SPHERE("sphere", 0, -90.0f, 0.0f, new Look(0.6f, 5.0f, 1.6f, 3.0f, 1.0f, 0.0f, 0)),
    /** Thin disc of {@code radius}. */
    PAD("pad", 1, -90.0f, 0.0f, new Look(2.5f, 0.6f, 1.4f, 1.5f, 1.0f, 0.0f, 0)),
    /** Wide lamp: tip at the light, twice {@code radius} long. */
    CONE("cone", 2, 90.0f, 2.0f, new Look(3.0f, 1.5f, 1.6f, 3.5f, 1.0f, 53.0f, 0)),
    /** Wide at the light, narrowing to a tip after twice {@code radius}. */
    INVERTED_CONE("inverted_cone", 3, -90.0f, 2.0f, new Look(1.5f, 1.0f, 1.4f, 4.0f, 1.0f, 53.0f, 0)),
    /** Narrow projector, six times {@code radius} long, hot centre. */
    SPOTLIGHT("spotlight", 4, 90.0f, 6.0f, new Look(1.5f, 1.5f, 2.5f, 3.0f, 1.0f, 20.0f, 0)),
    /** Column of radius {@code radius}, sixteen times as long. */
    BEAM("beam", 5, -90.0f, 16.0f, new Look(0.5f, 1.5f, 2.0f, 7.0f, 3.0f, 0.0f, 0)),
    /** Thin ring of {@code radius}. */
    RING("ring", 6, -90.0f, 0.0f, new Look(1.5f, 1.0f, 2.0f, 4.0f, 1.0f, 0.0f, 0)),
    /** Box reaching {@code radius} from its centre to each face. */
    BOX("box", 7, -90.0f, 0.0f, new Look(0.5f, 2.0f, 1.5f, 2.0f, 1.0f, 0.0f, 0)),
    /** Thin five-point star of {@code radius}. */
    STAR("star", 8, -90.0f, 0.0f, new Look(1.5f, 0.4f, 1.8f, 1.0f, 1.0f, 0.0f, 0)),
    /** Thin plus sign of {@code radius}. */
    CROSS("cross", 10, -90.0f, 0.0f, new Look(1.5f, 0.4f, 1.8f, 1.0f, 1.0f, 0.0f, 0)),
    /** Thin triangle of {@code radius}. */
    TRIANGLE("triangle", 11, -90.0f, 0.0f, new Look(1.5f, 0.4f, 1.8f, 1.0f, 1.0f, 0.0f, 0)),
    /**
     * A real torch: reaches twelve times {@code radius}, a bright centre with a soft rim and a faint spill around it, dimmer
     * the further it goes.
     */
    FLASHLIGHT("flashlight", 12, 90.0f, 12.0f, new Look(2.0f, 0.5f, 2.8f, 0.35f, 1.0f, 40.0f, 0)),
    /**
     * Warning light: a glowing core and two opposite beams, sixteen times {@code radius} long, that sweep around its axis
     * with the rotation animation.
     */
    SIREN("siren", 13, -90.0f, 16.0f, new Look(0.5f, 0.3f, 2.6f, 2.5f, 1.0f, 30.0f, 0)),
    /** Flame that licks and leans on its own, three times {@code radius} tall. */
    FIRE("fire", 14, -90.0f, 4.0f, new Look(0.7f, 0.35f, 3.0f, 2.0f, 1.0f, 0.0f, 0xFF7A1E)),
    /** Rippling ring with a sheet inside it, turning around its axis. */
    PORTAL("portal", 15, 0.0f, 0.0f, new Look(1.8f, 0.7f, 2.4f, 3.5f, 1.0f, 0.0f, 0xA24BFF)),
    /** Bolt that redraws itself, ten times {@code radius} long. */
    LIGHTNING("lightning", 16, -90.0f, 10.0f, new Look(0.35f, 0.7f, 3.0f, 3.5f, 1.0f, 0.0f, 0xBBD7FF)),
    /** Waving curtain, four times {@code radius} tall. */
    AURORA("aurora", 17, -90.0f, 4.0f, new Look(2.5f, 1.2f, 1.8f, 5.0f, 1.0f, 0.0f, 0x46FFB4)),
    /** Pool on the ground whose rim rises and falls in a wave running outwards. */
    RIPPLE("ripple", 18, -90.0f, 0.0f, new Look(3.0f, 0.8f, 2.0f, 2.5f, 1.0f, 0.0f, 0x3FD8FF)),
    /** Ball whose surface boils. */
    PLASMA("plasma", 20, -90.0f, 0.0f, new Look(1.2f, 1.0f, 2.4f, 4.0f, 1.0f, 0.0f, 0x7FD0FF)),
    /** Funnel {@code 8r} tall, narrow at the ground and turning as it widens. */
    TORNADO("tornado", 21, -90.0f, 8.0f, new Look(0.9f, 1.0f, 2.0f, 4.5f, 1.0f, 0.0f, 0xBFC8E0)),
    /** Accretion disc wound into a spiral around a core nothing comes out of. */
    SINGULARITY("singularity", 22, -90.0f, 0.0f, new Look(2.6f, 0.6f, 2.6f, 3.0f, 1.0f, 0.0f, 0x7A3CFF)),
    /** Summoning circle: rings and marks turning against each other, beating slowly. */
    RUNE("rune", 23, -90.0f, 0.0f, new Look(2.4f, 0.35f, 2.8f, 1.5f, 1.0f, 0.0f, 0x8CFFE8)),
    /** Star whose spikes grow and pull back in. */
    FLARE("flare", 24, -90.0f, 0.0f, new Look(1.6f, 0.8f, 3.0f, 3.0f, 1.0f, 0.0f, 0xFFD26A)),
    /** Low bank of fog whose top rolls slowly. */
    MIST("mist", 25, -90.0f, 0.0f, new Look(4.0f, 1.4f, 1.2f, 6.0f, 1.0f, 0.0f, 0xC8E0FF)),
    /** Burning head with a tail `7r` long that wavers and fades out behind it. */
    COMET("comet", 26, -90.0f, 7.0f, new Look(0.7f, 0.6f, 3.2f, 4.0f, 1.0f, 0.0f, 0xBFE8FF)),
    /** Flat doorway of beams, `2r` wide and `2.4r` tall, whose rungs creep upwards. */
    LASER_DOOR("laser_door", 27, -90.0f, 2.4f, new Look(1.4f, 0.05f, 3.6f, 3.0f, 1.0f, 0.0f, 0x39FF6A));




    /**
     * Ready-made settings for a shape; {@code stretchY} other than 1 turns the stretch on, {@code angle} is the full opening
     * of cones in degrees.
     */
    public record Look(float radius, float distance, float intensity, float atmosphere, float stretchY, float angle, int color) {
    }

    /** One of the animations a shape brings with it. */
    public record Built(LightAnimation.Preset preset, int time, float amount) {
    }

    public static final List<String> NAMES;
    public static final float MAX_ANGLE = 170.0f;

    static {
        List<String> names = new ArrayList<>();
        for (LightShape shape : values()) {
            names.add(shape.id);
        }
        NAMES = List.copyOf(names);
    }

    public final String id;
    public final int shaderId;
    public final float defaultPitch;
    /** Length of cones and beams in radii (0 for the other shapes). */
    public final float length;
    public final Look look;

    LightShape(String id, int shaderId, float defaultPitch, float length, Look look) {
        this.id = id;
        this.shaderId = shaderId;
        this.defaultPitch = defaultPitch;
        this.length = length;
        this.look = look;
    }

    /** The animations a shape brings with it (the editor marks these shapes and turns their animations on). */
    public List<Built> animations() {
        return switch (this) {
            case SIREN -> List.of(new Built(LightAnimation.Preset.ROTATION, 30, 360.0f));
            case FIRE -> List.of();
            case PORTAL -> List.of(new Built(LightAnimation.Preset.ROTATION, 90, 360.0f), new Built(LightAnimation.Preset.BREATHE, 60, 0.3f));
            case LIGHTNING -> List.of(new Built(LightAnimation.Preset.LIGHTNING, 26, 0.9f));
            case RIPPLE -> List.of(new Built(LightAnimation.Preset.PULSE, 50, 0.35f));
            case PLASMA -> List.of();
            case TORNADO -> List.of(new Built(LightAnimation.Preset.SWAY, 120, 0.5f));
            case SINGULARITY -> List.of(new Built(LightAnimation.Preset.BREATHE, 120, 0.25f));
            case RUNE -> List.of(new Built(LightAnimation.Preset.PULSE, 70, 0.3f));
            case MIST -> List.of(new Built(LightAnimation.Preset.FLOAT, 160, 0.3f));

            case AURORA -> List.of(new Built(LightAnimation.Preset.BREATHE, 110, 0.45f), new Built(LightAnimation.Preset.SWAY, 140, 0.6f));
            default -> List.of();
        };
    }

    /** Shapes that move on their own: their silhouette changes in the shader every frame. */
    public boolean animated() {
        return switch (this) {
            case SIREN, FIRE, PORTAL, LIGHTNING, AURORA, RIPPLE, PLASMA, TORNADO, SINGULARITY, RUNE, FLARE, MIST, COMET, LASER_DOOR -> true;
            default -> false;
        };
    }

    /** Adds this shape's own animations to {@code animation}, leaving any the light already has. */
    public void applyAnimations(LightAnimation animation) {
        for (Built built : animations()) {
            if (animation.entry(built.preset()) == null) {
                animation.put(built.preset(), built.time(), built.amount());
            }
        }
    }

    public String displayName() {

        String name = id.replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** Shapes whose look depends on where they point. */
    public boolean directional() {
        return this != SPHERE;
    }

    public boolean planar() {
        return this == PAD || this == STAR || this == CROSS || this == TRIANGLE || this == RING || this == PORTAL || this == RIPPLE
                || this == SINGULARITY || this == RUNE || this == LASER_DOOR;
    }

    /** Cones: their opening is set by an angle. */
    public boolean hasAngle() {
        return look.angle > 0.0f;
    }

    /** Half width per unit of length for an angle (0 = this shape's own). */
    public float spread(float angle) {
        float degrees = angle > 0.0f ? Math.min(angle, MAX_ANGLE) : look.angle;
        return (float) Math.tan(Math.toRadians(degrees) * 0.5d);
    }

    /** Applies the shape and its ready-made look to a light. */
    public void applyLook(Light light) {
        applyLook(light, 1.0f);
    }

    /** Applies the look scaled down (or up) for small objects: sizes and haze follow {@code scale}. */
    public void applyLook(Light light, float scale) {
        light.shape = this;
        light.radius = look.radius * scale;
        light.distance = look.distance * scale;
        light.intensity = look.intensity;
        light.atmosphere = look.atmosphere * Math.min(1.0f, scale * 1.5f);
        light.angle = look.angle;
        light.yaw = 0.0f;
        light.pitch = defaultPitch;
        light.stretchX = 1.0f;
        light.stretchY = look.stretchY;
        light.stretchZ = 1.0f;
        if (look.color != 0) {
            light.color = look.color;
        }
    }

    /** Local extent of the shape: {half width, bottom, top} along its own axes (Y = pointing direction). */
    public float[] extent(float radius, float angle) {
        float r = Math.max(0.0f, radius);
        float thin = Math.max(0.05f, r * 0.08f);
        float reach = r * length;
        return switch (this) {
            case SPHERE, BOX -> new float[]{r, -r, r};
            case PAD, STAR, CROSS, TRIANGLE -> new float[]{r, -thin, thin};
            case RING -> new float[]{r + thin, -thin, thin};
            case BEAM -> new float[]{r, 0.0f, reach};
            // The flashlight's spill reaches a little wider than its angle
            case FLASHLIGHT -> new float[]{reach * Math.min(spread(angle) * 1.6f, 12.0f), 0.0f, reach};
            case CONE, INVERTED_CONE, SPOTLIGHT -> new float[]{reach * Math.min(spread(angle), 12.0f), 0.0f, reach};
            case SIREN -> {
                float side = reach * Math.min(spread(angle) * 1.2f, 12.0f);
                yield new float[]{reach, -side, side};
            }
            // The flame leans and its tip wanders, so it is given room
            case FIRE -> new float[]{r * 2.2f, -r * 0.5f, reach * 1.2f};
            case PORTAL -> new float[]{r * 1.35f, -r * 0.35f, r * 0.35f};
            case LIGHTNING -> new float[]{r * 2.2f, 0.0f, reach};
            case AURORA -> new float[]{r * 1.7f, 0.0f, reach};
            case RIPPLE -> new float[]{r * 1.1f, -r * 0.2f, r * 0.95f};
            case PLASMA -> new float[]{r * 1.45f, -r * 1.45f, r * 1.45f};
            case TORNADO -> new float[]{r * 1.6f, 0.0f, reach};
            case SINGULARITY -> new float[]{r * 1.25f, -r * 0.3f, r * 0.3f};
            case RUNE -> new float[]{r * 1.15f, -r * 0.2f, r * 0.2f};
            case FLARE -> new float[]{r * 1.8f, -r * 1.8f, r * 1.8f};
            case MIST -> new float[]{r * 1.25f, -r * 0.25f, r * 0.6f};
            case COMET -> new float[]{r * 1.8f, -r * 1.2f, reach};
            case LASER_DOOR -> new float[]{r * 1.15f, -r * 0.1f, reach};

        };
    }

    public static LightShape byName(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        for (LightShape shape : values()) {
            if (shape.id.equals(key)) {
                return shape;
            }
        }
        return switch (key) {
            case "point" -> SPHERE;
            case "invertedcone", "inverted-cone", "cone_inverted" -> INVERTED_CONE;
            case "spot" -> SPOTLIGHT;
            case "column", "pillar" -> BEAM;
            case "halo" -> RING;
            case "cube" -> BOX;
            case "plus" -> CROSS;
            case "torch", "linterna" -> FLASHLIGHT;
            case "sirena", "alarm", "warning" -> SIREN;
            case "flame", "campfire", "fuego" -> FIRE;
            case "vortex", "rift" -> PORTAL;
            case "bolt", "thunder", "rayo" -> LIGHTNING;
            case "curtain", "northern_lights" -> AURORA;
            case "wave", "pool", "ola" -> RIPPLE;
            case "energy", "orb" -> PLASMA;
            case "twister", "whirlwind", "tornado_funnel" -> TORNADO;
            case "blackhole", "black_hole", "accretion" -> SINGULARITY;
            case "circle", "magic", "summon" -> RUNE;
            case "starburst", "sun", "burst" -> FLARE;
            case "fog", "niebla", "haze" -> MIST;
            case "meteor", "shooting_star" -> COMET;
            case "laser", "laserwall", "laser_wall", "security" -> LASER_DOOR;

            default -> null;
        };
    }
}
