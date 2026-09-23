package com.cotii.customlights.client.light;

import com.cotii.customlights.client.render.LightFrame;
import com.cotii.customlights.client.render.RenderLight;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * Lights attached to model parts named {@code customlights_<model_id>} (GeckoLib bones, Blockbench groups in item models,
 * ModelEngine bone items).
 */
public final class MarkerLights {
    public static final String PREFIX = "customlights_";

    public enum Phase {
        /** GUI, maps, anything that is not the world: markers are ignored. */
        NONE,
        /** Pose matrices are camera-relative world space. */
        WORLD,
        /** First person hand: pose matrices are camera-relative world space too, drawn with the hand projection. */
        HAND
    }

    private static final class Instance {
        String template;
        /** Held in first person: its haze would fill the whole view, so it barely has any. */
        boolean hand;
        double x, y, z;
        float dirX, dirY, dirZ;
        long firstSeenTick;
        long lastSeenFrame;
        long lastSeenTick;
    }

    private static final float HAND_ATMOSPHERE = 0.12f;

    private static Phase phase = Phase.NONE;
    private static int owner;
    private static long frame;
    private static float partialTick;
    private static Vec3 cameraPos = Vec3.ZERO;
    private static final Quaternionf CAMERA_ROTATION = new Quaternionf();
    private static final Map<String, Instance> INSTANCES = new HashMap<>();
    private static final Map<String, Integer> OCCURRENCES = new HashMap<>();
    private static final LightAnimation.Result ANIMATION = new LightAnimation.Result();
    private static final Vector3f SCRATCH = new Vector3f();
    private static final Matrix4f VIEW_TO_WORLD = new Matrix4f();

    private MarkerLights() {
    }

    /** Template id of a part name, or null when the part is not a light marker. */
    public static String templateOf(String partName) {
        if (partName == null || partName.length() <= PREFIX.length() || !partName.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            return null;
        }
        return partName.substring(PREFIX.length()).toLowerCase(Locale.ROOT);
    }

    public static void beginFrame(Vec3 camera, Quaternionf cameraRotation, float partial) {
        frame++;
        cameraPos = camera;
        CAMERA_ROTATION.set(cameraRotation);
        // View space to camera-relative world axes (1.20.x sets its own, see setViewRotation)
        VIEW_TO_WORLD.rotation(CAMERA_ROTATION);
        partialTick = partial;
        OCCURRENCES.clear();
    }

    /** 1.20.x: the view rotation this frame is drawn with (its poses are in view space, see PoseSpace). */
    /** Iris / Oculus draw the first person hand themselves, in view space, while the world is being drawn. */
    private static boolean irisHand;
    private static Phase phaseBeforeIrisHand = Phase.NONE;

    public static void setIrisHand(boolean drawing) {
        if (drawing && !irisHand) {
            phaseBeforeIrisHand = phase;
            phase = Phase.HAND;
        } else if (!drawing && irisHand) {
            phase = phaseBeforeIrisHand;
        }
        irisHand = drawing;
    }

    public static void setViewRotation(Matrix4f viewRotation) {
        VIEW_TO_WORLD.set(viewRotation).invert();
    }

    public static void setPhase(Phase newPhase) {
        phase = newPhase;
    }

    public static Phase phase() {
        // A shader pack's shadow map is drawn from the sun: nothing there is a light
        if (phase != Phase.NONE && com.cotii.customlights.client.render.ShaderCompat.shadowPass()) {
            return Phase.NONE;
        }
        return phase;
    }

    /** Entity (or other object) whose model is being drawn, to tell apart the same bone on different entities. */
    public static int setOwner(int newOwner) {
        int previous = owner;
        owner = newOwner;
        return previous;
    }

    /**
     * A marker part is being drawn with this transform ({@code pose} maps part space, in blocks, to the current phase space;
     * {@code origin} is the part pivot in part space).
     */
    public static void submit(String templateId, Matrix4f pose, Matrix3f normal, float originX, float originY, float originZ, int partKey) {
        if (phase() == Phase.NONE) {
            return;
        }
        Light template = ObjectLights.markerTemplate(templateId);
        if (template == null) {
            return;
        }
        String key = templateId + '|' + owner + '|' + partKey + '|' + phase.ordinal();
        int occurrence = OCCURRENCES.merge(key, 1, Integer::sum);
        if (occurrence > 1) {
            key = key + '#' + occurrence;
        }

        Light.Snapshot snapshot = template.snapshot(partialTick, ANIMATION);
        // Poses end in camera-relative world axes, except on 1.20.x where they are in view space and are turned here
        boolean viewSpace = com.cotii.customlights.client.compat.PoseSpace.viewSpace() || irisHand;
        Vector3f position = pose.transformPosition(originX + (float) snapshot.x, originY + (float) snapshot.y, originZ + (float) snapshot.z, SCRATCH);
        if (viewSpace) {
            VIEW_TO_WORLD.transformPosition(position);
        }
        double absX = cameraPos.x + position.x;
        double absY = cameraPos.y + position.y;
        double absZ = cameraPos.z + position.z;

        double yaw = Math.toRadians(snapshot.yaw);
        double pitch = Math.toRadians(snapshot.pitch);
        Vector3f dir = normal.transform((float) (-Math.sin(yaw) * Math.cos(pitch)), (float) -Math.sin(pitch),
                (float) (Math.cos(yaw) * Math.cos(pitch)), new Vector3f());
        if (viewSpace) {
            VIEW_TO_WORLD.transformDirection(dir);
        }

        long tick = LightManager.ticks();
        Instance instance = INSTANCES.get(key);
        if (instance == null || !instance.template.equals(templateId)) {
            instance = new Instance();
            instance.template = templateId;
            instance.firstSeenTick = tick;
            INSTANCES.put(key, instance);
        } else if (tick - instance.lastSeenTick > Math.max(2, template.fadeOut) + 2) {
            // Gone long enough to have faded out: fade in again
            instance.firstSeenTick = tick;
        }
        instance.x = absX;
        instance.y = absY;
        instance.z = absZ;
        instance.dirX = dir.x;
        instance.dirY = dir.y;
        instance.dirZ = dir.z;
        instance.hand = phase == Phase.HAND;
        instance.lastSeenFrame = frame;
        instance.lastSeenTick = tick;
    }

    /** Marker lights (item, model and bone lights) added in the last frame, for the development tests. */
    public static int lastCollected;

    /** Adds this frame's marker lights, fading out the ones that stopped being drawn. */
    public static void collect(LightFrame lights, Vec3 camera, float partial) {
        lastCollected = 0;
        if (INSTANCES.isEmpty()) {
            return;
        }
        long tick = LightManager.ticks();
        Iterator<Instance> iterator = INSTANCES.values().iterator();
        while (iterator.hasNext()) {
            Instance instance = iterator.next();
            Light template = ObjectLights.markerTemplate(instance.template);
            if (template == null) {
                iterator.remove();
                continue;
            }
            float alpha = 1.0f;
            if (template.fadeIn > 0) {
                alpha = Math.min(1.0f, (tick - instance.firstSeenTick + partial) / template.fadeIn);
            }
            if (instance.lastSeenFrame != frame) {
                // The part was not drawn this frame (removed, culled or out of view): fade out where it was last
                float missing = tick - instance.lastSeenTick + partial;
                if (template.fadeOut <= 0 || missing >= template.fadeOut) {
                    if (missing > 2.0f) {
                        iterator.remove();
                    }
                    continue;
                }
                alpha *= 1.0f - missing / template.fadeOut;
            }
            Light.Snapshot snapshot = template.snapshot(partial, ANIMATION);
            lastCollected++;
            RenderLight light = lights.add();
            light.x = (float) (instance.x - camera.x);
            light.y = (float) (instance.y - camera.y);
            light.z = (float) (instance.z - camera.z);
            light.setDirectionVector(instance.dirX, instance.dirY, instance.dirZ);
            light.setStretch(template.stretchX, template.stretchY, template.stretchZ);
            light.setPivot(template.pivotX, template.pivotY, template.pivotZ);
            light.setSeed(template.seed);
            light.shape = template.shape;
            light.setColor(snapshot.color, snapshot.intensity * alpha);
            light.radius = snapshot.radius;
            light.distance = snapshot.distance;
            light.angle = snapshot.angle;
            light.roll = (float) Math.toRadians(snapshot.roll);
            light.atmosphere = snapshot.atmosphere * snapshot.alpha * alpha * (instance.hand ? HAND_ATMOSPHERE : 1.0f);
            light.luminosity = template.luminosity * alpha;
            light.seeThrough = template.seeThrough;
        }
    }

    /** Whether a first person hand light sits in front of the eye, within reach (for the development tests). */
    public static boolean handLightInFront(Vec3 eye, Vec3 look) {
        for (Instance instance : INSTANCES.values()) {
            Vec3 offset = new Vec3(instance.x, instance.y, instance.z).subtract(eye);
            if (instance.hand && offset.length() < 1.5d && offset.dot(look) > 0.1d) {
                return true;
            }
        }
        return false;
    }

    /** Where each marker light is (world position), for the development tests. */
    public static java.util.List<String> describe() {
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (Instance instance : INSTANCES.values()) {
            lines.add(String.format(Locale.ROOT, "%s at %.2f %.2f %.2f%s", instance.template, instance.x, instance.y, instance.z, instance.hand ? " (hand)" : ""));
        }
        return lines;
    }

    public static void clear() {
        INSTANCES.clear();
    }
}
