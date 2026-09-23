package com.cotii.customlights.api;

import com.cotii.customlights.client.light.Light;
import com.cotii.customlights.client.light.LightManager;
import com.cotii.customlights.client.light.LightShape;
import com.cotii.customlights.client.render.LightRenderer;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Public API for other mods, client side.
 *
 * <pre>
 * CustomLights.light("mymod_lamp")
 *         .at(100.5, 70, 8.5)
 *         .shape("sphere")
 *         .color(0xFF8800)
 *         .radius(3).blur(1.5f).intensity(2).atmosphere(3)
 *         .add();
 *
 * CustomLights.remove("mymod_lamp");
 * </pre>
 *
 * A torch on the player, the same light the editor's flashlight makes:
 *
 * <pre>
 * CustomLights.light("mymod_torch")
 *         .follow("&#64;self", 0, 0, 0)
 *         .style("flashlight")
 *         .cameraDelay(2)
 *         .add();
 * </pre>
 *
 * Lights added here behave like the player's own: saved with the world, shown in the editor and able to go in groups.
 * Put your mod id in the light id so it does not clash with anyone else's.
 */
public final class CustomLights {
    private CustomLights() {
    }

    /** False before the player is in a world, or while the mod's own shaders failed to build. */
    public static boolean ready() {
        return Minecraft.getInstance().level != null && !LightRenderer.shaderFailed();
    }

    /** A new light, or the settings of the one with this id if it exists. */
    public static Builder light(String id) {
        return new Builder(id);
    }

    /** Every shape name {@link Builder#shape(String)} takes. */
    public static List<String> shapes() {
        return LightShape.NAMES;
    }

    /**
     * The shapes that move by themselves: their silhouette changes every frame, and most of them also bring their own
     * animations (fire, portal, lightning, aurora, ripple, plasma, tornado, singularity, rune, flare, mist, comet,
     * laser_door and siren).
     */
    public static List<String> animatedShapes() {
        List<String> names = new ArrayList<>();
        for (LightShape shape : LightShape.values()) {
            if (shape.animated()) {
                names.add(shape.id);
            }
        }
        return names;
    }

    /** The ids of every light in the dimension the player is in. */
    public static List<String> ids() {
        return new ArrayList<>(LightManager.ids());
    }

    public static boolean exists(String id) {
        return LightManager.get(id).isPresent();
    }

    /** Takes a light out with its own fade, or right away with {@code fadeOut} 0. */
    public static boolean remove(String id) {
        return LightManager.remove(id, null);
    }

    public static boolean remove(String id, int fadeOut) {
        return LightManager.remove(id, fadeOut);
    }

    /** Turns every light off and on (the same as {@code /customlight toggle}). */
    public static void setEnabled(boolean enabled) {
        LightRenderer.enabled = enabled;
    }

    public static boolean enabled() {
        return LightRenderer.enabled;
    }

    /** How many lights reached the screen in the last frame. */
    public static int drawnLastFrame() {
        return LightRenderer.lastDrawn;
    }

    /** Builds a light. */
    public static final class Builder {
        private final Light light;

        private Builder(String id) {
            Optional<Light> existing = LightManager.get(id);
            light = existing.map(found -> found.copy(found.id)).orElseGet(() -> {
                Light fresh = new Light(id);
                LightShape.SPHERE.applyLook(fresh);
                return fresh;
            });
        }

        /** Where the light is, in world coordinates (static lights). */
        public Builder at(double x, double y, double z) {
            light.kind = Light.Kind.STATIC;
            light.x = x;
            light.y = y;
            light.z = z;
            return this;
        }

        /** Puts the light on a player name, an entity UUID, {@code tag:<tag>} or {@code @self}, with an offset. */
        public Builder follow(String target, double offsetX, double offsetY, double offsetZ) {
            light.kind = Light.Kind.FOLLOW;
            light.target = target;
            light.offsetX = offsetX;
            light.offsetY = offsetY;
            light.offsetZ = offsetZ;
            return this;
        }

        /**
         * One of {@link CustomLights#shapes()}: sphere, pad, cone, inverted_cone, spotlight, flashlight, siren, beam, ring,
         * box, star, cross, triangle, fire, portal, lightning, aurora, ripple, plasma, tornado, singularity, rune, flare,
         * mist, comet or laser_door. Only the shape changes; everything else stays as it is, so for one of the shapes that
         * move by themselves you probably want {@link #style(String)} instead.
         */
        public Builder shape(String name) {
            LightShape shape = LightShape.byName(name);
            if (shape != null) {
                light.shape = shape;
            }
            return this;
        }

        /**
         * The shape with the look it has in the editor: size, blur, brightness, haze, angle and, for the shapes that move
         * by themselves, their colour and their own animations. Set your own values after this to override any of them.
         */
        public Builder style(String name) {
            LightShape shape = LightShape.byName(name);
            if (shape != null) {
                shape.applyLook(light);
                shape.applyAnimations(light.animation);
            }
            return this;
        }

        /**
         * Adds one animation: the preset's name (flicker, pulse, rotation, breathe, strobe, rainbow, lightning...), how long
         * one round takes in ticks and how strong it is. Unknown names are ignored.
         */
        public Builder animation(String preset, int ticks, float amount) {
            try {
                light.animation.put(com.cotii.customlights.client.light.LightAnimation.Preset.valueOf(preset.toUpperCase(java.util.Locale.ROOT)),
                        Math.max(1, ticks), amount);
            } catch (IllegalArgumentException ignored) {
                // Not a preset: nothing to animate
            }
            return this;
        }

        public Builder clearAnimations() {
            light.animation.clear();
            return this;
        }

        /** Flashlight only: ticks its direction takes to catch up with the camera (0 = none). */
        public Builder cameraDelay(float ticks) {
            light.cameraDelay = ticks;
            return this;
        }

        /** Follow lights: false hides the light from the player carrying it while they are in first person. */
        public Builder visibleFirstPerson(boolean visible) {
            light.visibleFirstPerson = visible;
            return this;
        }

        /** 0xRRGGBB. */
        public Builder color(int rgb) {
            light.color = rgb & 0xFFFFFF;
            return this;
        }

        /** Size of the fully lit shape, in blocks. */
        public Builder radius(float radius) {
            light.radius = radius;
            return this;
        }

        /** How far the light blurs out past the shape (0 = hard edge). */
        public Builder blur(float distance) {
            light.distance = distance;
            return this;
        }

        /** Brightness; a dark colour darkens instead. */
        public Builder intensity(float intensity) {
            light.intensity = intensity;
            return this;
        }

        /** Light scattered in the air, 0 to 10. */
        public Builder atmosphere(float atmosphere) {
            light.atmosphere = atmosphere;
            return this;
        }

        /** Extra glow on whatever the light reaches, 0 to 10. */
        public Builder luminosity(float luminosity) {
            light.luminosity = luminosity;
            return this;
        }

        /** Where the light points; yaw 0 is south, pitch 90 is down. */
        public Builder direction(float yaw, float pitch) {
            light.yaw = yaw;
            light.pitch = pitch;
            return this;
        }

        /** How wide cones, spotlights, flashlights and sirens open, in degrees. */
        public Builder angle(float degrees) {
            light.angle = degrees;
            return this;
        }

        /** Where the stretch grows from, on the light's own axes and in unstretched blocks (0 0 0 = its middle). */
        public Builder pivot(float x, float y, float z) {
            light.pivotX = x;
            light.pivotY = y;
            light.pivotZ = z;
            return this;
        }

        public Builder stretch(float x, float y, float z) {
            light.stretchX = x;
            light.stretchY = y;
            light.stretchZ = z;
            return this;
        }

        /** Ticks the light takes to appear and to disappear. */
        public Builder fade(int in, int out) {
            light.fadeIn = in;
            light.fadeOut = out;
            return this;
        }

        /** False makes solid blocks stop the light. */
        public Builder throughBlocks(boolean through) {
            light.seeThrough = through;
            return this;
        }

        /** The group it shows in, in the editor. */
        public Builder group(String group) {
            light.group = group;
            return this;
        }

        /** Adds it, or replaces the light with the same id. */
        public void add() {
            LightManager.put(light);
        }

        /** Moves it to a point over {@code ticks}, easing with smooth, linear, step or bounce. */
        public void moveTo(double x, double y, double z, String easing, int ticks) {
            LightManager.get(light.id).ifPresent(found -> {
                found.startMove(x, y, z, null, null, null, null, null, ticks, Light.Easing.of(easing));
                LightManager.markDirty();
            });
        }
    }
}
