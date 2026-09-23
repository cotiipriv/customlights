package com.cotii.customlights.client.light;

import com.cotii.customlights.client.render.LightFrame;
import com.cotii.customlights.client.render.RenderLight;
import com.cotii.customlights.client.storage.LightStorage;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every light the client knows: its own (saved per world), the server plugin's (never saved), model light templates, the
 * flashlight and the editor preview.
 */
public final class LightManager {
    private static final int SAVE_DELAY_TICKS = 1;
    private static final int TAG_REFRESH_TICKS = 20;
    public static final String SELF_TARGET = "@self";

    private static final Map<String, Light> LOCAL = new LinkedHashMap<>();
    private static final Map<String, Light> SERVER = new LinkedHashMap<>();
    private static final Map<String, Light> SERVER_TEMPLATES = new LinkedHashMap<>();
    /** Entities with a tag, looked up on the integrated server (tags are not sent to clients). */
    private static final Map<String, Set<Integer>> TAGGED = new ConcurrentHashMap<>();
    private static final LightAnimation.Result ANIMATION = new LightAnimation.Result();

    private static Light preview;
    private static String previewReplaces;
    private static long ticks;
    private static String worldKey;
    private static ClientLevel lastLevel;
    private static int saveCountdown = -1;
    /** Changes not written to the world file yet. */
    private static boolean dirty;
    private static Map<UUID, Entity> entitiesByUuid = Map.of();
    private static long entitiesByUuidTick = -1;

    private LightManager() {
    }

    public static long ticks() {
        return ticks;
    }

    // ── Lookup
    // ──────────────────────────────────────────────────────

    private static String key(String id) {
        return id.toLowerCase(Locale.ROOT);
    }

    /** A light in the current dimension: the client's own first, then the server's. */
    public static Optional<Light> get(String id) {
        Light light = LOCAL.get(key(id));
        if (light == null || light.removing || !inCurrentDimension(light)) {
            light = SERVER.get(key(id));
        }
        return Optional.ofNullable(light).filter(found -> !found.removing && inCurrentDimension(found));
    }

    public static boolean isTemplate(Light light) {
        return SERVER_TEMPLATES.containsValue(light) || ObjectLights.isRuleLight(light);
    }

    public static Light template(String id) {
        Light template = SERVER_TEMPLATES.get(id);
        return template != null ? template : ObjectLights.bone(id);
    }

    public static Optional<Light> getTemplate(String id) {
        return Optional.ofNullable(template(key(id)));
    }

    /** Ids of the lights in the current dimension, sorted. */
    public static List<String> ids() {
        List<String> ids = new ArrayList<>();
        for (Light light : all()) {
            if (!light.removing && inCurrentDimension(light) && !ids.contains(light.id)) {
                ids.add(light.id);
            }
        }
        ids.sort(String.CASE_INSENSITIVE_ORDER);
        return ids;
    }

    public static List<String> templateIds() {
        List<String> ids = new ArrayList<>();
        ids.addAll(ObjectLights.boneIds());
        for (Light template : SERVER_TEMPLATES.values()) {
            if (!ids.contains(template.id)) {
                ids.add(template.id);
            }
        }
        ids.sort(String.CASE_INSENSITIVE_ORDER);
        return ids;
    }

    /** The client's own lights (every dimension). */
    public static List<Light> localLights() {
        return new ArrayList<>(LOCAL.values());
    }

    public static List<Light> visibleLights() {
        List<Light> lights = new ArrayList<>();
        for (Light light : all()) {
            if (!light.removing && inCurrentDimension(light)) {
                lights.add(light);
            }
        }
        lights.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.id, b.id));
        return lights;
    }

    private static Iterable<Light> all() {
        List<Light> all = new ArrayList<>(LOCAL.size() + SERVER.size());
        all.addAll(LOCAL.values());
        all.addAll(SERVER.values());
        return all;
    }

    public static String currentDimension() {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? null : level.dimension().location().toString();
    }

    private static boolean inCurrentDimension(Light light) {
        return light.dimension == null || light.dimension.equals(currentDimension());
    }

    // ── Local changes
    // ──────────────────────────────────────────────

    /** Adds or replaces a client light in the current dimension. */
    public static void put(Light light) {
        light.server = false;
        light.dimension = currentDimension();
        Light previous = LOCAL.put(key(light.id), light);
        // Keeps its group fade where it was (or starts where its group is)
        light.groupShown = light.groupShownBefore = previous != null ? previous.groupShown : LightGroups.shows(light) ? 1.0f : 0.0f;
        light.groupMotion = light.groupMotionBefore = previous != null ? previous.groupMotion : LightGroups.animates(light) ? 1.0f : 0.0f;
        if (previous != null && !previous.removing) {
            // Replacing keeps the light on without fading in again
            light.age = Math.max(light.age, light.fadeIn);
        }
        markDirty();
    }

    /** Removes a light with its fade out; {@code fadeOut} null uses the light's own. */
    public static boolean remove(String id, Integer fadeOut) {
        Optional<Light> light = get(id);
        light.ifPresent(found -> {
            found.startRemove(fadeOut == null ? found.fadeOut : fadeOut);
            markDirty();
        });
        return light.isPresent();
    }

    public static int removeAll(Integer fadeOut) {
        int count = 0;
        for (Light light : all()) {
            if (!light.removing && inCurrentDimension(light)) {
                light.startRemove(fadeOut == null ? light.fadeOut : fadeOut);
                count++;
            }
        }
        markDirty();
        return count;
    }

    public static void putTemplate(Light template) {
        template.server = false;
        template.dimension = null;
        ObjectLights.put(new ObjectLights.Rule(ObjectLights.Kind.BONE, key(template.id), -1, template));
    }

    public static boolean removeTemplate(String id, Integer fadeOut) {
        if (ObjectLights.remove(ObjectLights.keyOf(ObjectLights.Kind.BONE, id, -1), fadeOut)) {
            return true;
        }
        Light template = SERVER_TEMPLATES.get(key(id));
        if (template == null || template.removing) {
            return false;
        }
        template.startRemove(fadeOut == null ? template.fadeOut : fadeOut);
        return true;
    }

    /** Called after changing a light's settings in place. */
    public static void markDirty() {
        dirty = true;
        if (saveCountdown < 0) {
            saveCountdown = SAVE_DELAY_TICKS;
        }
        ObjectLights.markDirty();
    }

    // ── Flashlights and preview
    // ────────────────────────────────────

    /**
     * A flashlight is a saved light like any other: a follow light with the flashlight shape, with its own id, group and
     * file.
     */
    public static void setFlashlight(Light light) {
        light.kind = Light.Kind.FOLLOW;
        light.shape = LightShape.FLASHLIGHT;
        if (light.target == null || light.target.isBlank()) {
            light.target = SELF_TARGET;
        }
        put(light);
    }

    /** The light called "flashlight", or any other flashlight of this dimension. */
    public static Light flashlight() {
        Light other = null;
        for (Light light : all()) {
            if (light.shape != LightShape.FLASHLIGHT || light.removing || !inCurrentDimension(light)) {
                continue;
            }
            if (light.id.equalsIgnoreCase("flashlight")) {
                return light;
            }
            other = other == null ? light : other;
        }
        return other;
    }

    /** Every flashlight of this dimension (the client's own and the server's). */
    public static List<Light> flashlights() {
        List<Light> found = new ArrayList<>();
        for (Light light : all()) {
            if (light.shape == LightShape.FLASHLIGHT && !light.removing && inCurrentDimension(light)) {
                found.add(light);
            }
        }
        return found;
    }

    /** Turns every flashlight off. */
    public static void turnOffFlashlight() {
        for (Light light : flashlights()) {
            light.startRemove(light.fadeOut);
        }
        markDirty();
    }

    /** The editor's draft; it hides the saved light with {@code replaces} while shown. */
    public static void setPreview(Light light, String replaces) {
        preview = light;
        previewReplaces = replaces == null ? null : key(replaces);
    }

    // ── Server sync
    // ────────────────────────────────────────────────

    public static void serverFull(Collection<Light> lights, Collection<Light> templates) {
        SERVER.clear();
        for (Light light : lights) {
            putFromServer(light);
        }
        SERVER_TEMPLATES.clear();
        for (Light template : templates) {
            putTemplateFromServer(template);
        }
    }

    public static void putFromServer(Light light) {
        light.server = true;
        LightGroups.applyServerGroup(light);
        Light previous = SERVER.put(key(light.id), light);
        light.groupShown = light.groupShownBefore = previous != null ? previous.groupShown : LightGroups.shows(light) ? 1.0f : 0.0f;
        light.groupMotion = light.groupMotionBefore = previous != null ? previous.groupMotion : LightGroups.animates(light) ? 1.0f : 0.0f;
        if (previous != null && !previous.removing) {
            light.age = Math.max(light.age, light.fadeIn);
        }
    }

    public static Light serverLight(String id) {
        return SERVER.get(key(id));
    }

    public static void removeFromServer(String id, Integer fadeOut) {
        Light light = SERVER.get(key(id));
        if (light != null) {
            light.startRemove(fadeOut == null ? light.fadeOut : fadeOut);
        }
    }

    public static void removeAllFromServer(Integer fadeOut) {
        for (Light light : SERVER.values()) {
            light.startRemove(fadeOut == null ? light.fadeOut : fadeOut);
        }
    }

    public static void putTemplateFromServer(Light template) {
        template.server = true;
        template.dimension = null;
        SERVER_TEMPLATES.put(key(template.id), template);
    }

    public static void removeTemplateFromServer(String id, Integer fadeOut) {
        Light template = SERVER_TEMPLATES.get(key(id));
        if (template != null) {
            template.startRemove(fadeOut == null ? template.fadeOut : fadeOut);
        }
    }

    public static void removeAllTemplatesFromServer(Integer fadeOut) {
        for (Light template : SERVER_TEMPLATES.values()) {
            template.startRemove(fadeOut == null ? template.fadeOut : fadeOut);
        }
    }

    public static void onDisconnect() {
        saveNow();
        if (Minecraft.getInstance().level != null) {
            // Already in another world by the time this runs: that world's lights stay, loaded again from its file
            SERVER.clear();
            SERVER_TEMPLATES.clear();
            worldKey = null;
            lastLevel = null;
            return;
        }
        SERVER.clear();
        SERVER_TEMPLATES.clear();
        LOCAL.clear();
        LightGroups.clear();
        worldKey = null;
        lastLevel = null;
        preview = null;
        MarkerLights.clear();
        TAGGED.clear();
    }

    // ── Tick
    // ───────────────────────────────────────────────────────

    public static void tick(Minecraft minecraft) {
        ObjectLights.tick(minecraft);
        if (minecraft.level != lastLevel) {
            onLevelChanged(minecraft);
        }
        if (minecraft.level == null) {
            return;
        }
        ticks++;
        tickAll(LOCAL);
        tickAll(SERVER);
        tickAll(SERVER_TEMPLATES);
        if (preview != null) {
            preview.tick();
        }
        if (ticks % TAG_REFRESH_TICKS == 0) {
            refreshTags(minecraft);
        }
        if (saveCountdown >= 0 && --saveCountdown < 0) {
            saveNow();
        }
    }

    private static void tickAll(Map<String, Light> lights) {
        Iterator<Light> iterator = lights.values().iterator();
        while (iterator.hasNext()) {
            Light light = iterator.next();
            light.tick();
            tickGroup(light);
            if (light.finishedRemoving()) {
                iterator.remove();
            }
        }
    }

    /** Fades a saved light in or out with its group (with its own fades), and its animations in or out. */
    private static void tickGroup(Light light) {
        light.groupShownBefore = light.groupShown;
        light.groupMotionBefore = light.groupMotion;
        boolean shown = LightGroups.shows(light);
        if (shown) {
            light.groupShown = light.fadeIn <= 0 ? 1.0f : Math.min(1.0f, light.groupShown + 1.0f / light.fadeIn);
        } else {
            light.groupShown = light.fadeOut <= 0 ? 0.0f : Math.max(0.0f, light.groupShown - 1.0f / light.fadeOut);
        }
        boolean animates = LightGroups.animates(light);
        light.groupMotion = animates ? Math.min(1.0f, light.groupMotion + 0.1f) : Math.max(0.0f, light.groupMotion - 0.1f);
    }

    private static void onLevelChanged(Minecraft minecraft) {
        lastLevel = minecraft.level;
        String key = LightStorage.worldKey(minecraft);
        if (key == null) {
            // Left the world: whatever is pending is written now
            saveNow();
            return;
        }
        if (key.equals(worldKey)) {
            // Same world, other dimension: lights stay, each shows in its own dimension
            return;
        }
        saveNow();
        worldKey = key;
        LOCAL.clear();
        LightGroups.load(key);
        for (Light light : SERVER.values()) {
            LightGroups.applyServerGroup(light);
        }
        for (Light light : LightStorage.loadWorld(key)) {
            // Already on when the world loads
            light.age = light.fadeIn;
            LOCAL.put(key(light.id), light);
        }
        for (Light light : LOCAL.values()) {
            light.groupShown = light.groupShownBefore = LightGroups.shows(light) ? 1.0f : 0.0f;
            light.groupMotion = light.groupMotionBefore = LightGroups.animates(light) ? 1.0f : 0.0f;
        }
        saveCountdown = -1;
    }

    /** Lights that can be put in groups: this client's and the server's. */
    public static List<Light> groupableLights() {
        List<Light> lights = new ArrayList<>(LOCAL.values());
        lights.addAll(SERVER.values());
        return lights;
    }

    public static void saveNow() {
        LightGroups.saveIfDirty();
        if (worldKey != null && dirty) {
            LightStorage.saveWorld(worldKey, LOCAL.values());
            dirty = false;
        }
        saveCountdown = -1;
        ObjectLights.saveIfDirty();
    }

    private static void refreshTags(Minecraft minecraft) {
        Set<String> tags = new java.util.HashSet<>();
        for (Light light : all()) {
            String tag = tagOf(light.target);
            if (light.isFollow() && tag != null && light.targetIds.isEmpty()) {
                tags.add(tag);
            }
        }
        TAGGED.keySet().retainAll(tags);
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (tags.isEmpty() || server == null || minecraft.level == null) {
            return;
        }
        var dimension = minecraft.level.dimension();
        server.execute(() -> {
            ServerLevel level = server.getLevel(dimension);
            if (level == null) {
                return;
            }
            Map<String, Set<Integer>> found = new HashMap<>();
            for (Entity entity : level.getAllEntities()) {
                for (String tag : entity.getTags()) {
                    if (tags.contains(tag)) {
                        found.computeIfAbsent(tag, ignored -> new java.util.HashSet<>()).add(entity.getId());
                    }
                }
            }
            for (String tag : tags) {
                TAGGED.put(tag, found.getOrDefault(tag, Set.of()));
            }
        });
    }

    private static String tagOf(String target) {
        return target != null && target.regionMatches(true, 0, "tag:", 0, 4) ? target.substring(4) : null;
    }

    // ── Follow targets
    // ─────────────────────────────────────────────

    /** Entities a follow light is on right now (a tag can match several). */
    public static List<Entity> targets(Minecraft minecraft, Light light) {
        ClientLevel level = minecraft.level;
        if (level == null || light.target == null || light.target.isEmpty()) {
            return List.of();
        }
        String target = light.target;
        if (target.equalsIgnoreCase(SELF_TARGET) || target.equalsIgnoreCase("@s")) {
            return minecraft.player == null ? List.of() : List.of(minecraft.player);
        }
        String tag = tagOf(target);
        if (tag != null) {
            List<Entity> entities = new ArrayList<>();
            if (!light.targetIds.isEmpty()) {
                for (UUID uuid : light.targetIds) {
                    Entity entity = entityByUuid(level, uuid);
                    if (entity != null) {
                        entities.add(entity);
                    }
                }
            } else {
                for (Integer id : TAGGED.getOrDefault(tag, Set.of())) {
                    Entity entity = level.getEntity(id);
                    if (entity != null) {
                        entities.add(entity);
                    }
                }
            }
            return entities;
        }
        try {
            Entity entity = entityByUuid(level, UUID.fromString(target));
            return entity == null ? List.of() : List.of(entity);
        } catch (IllegalArgumentException notUuid) {
            for (Player player : level.players()) {
                if (player.getGameProfile().getName().equalsIgnoreCase(target)) {
                    return List.of(player);
                }
            }
            return List.of();
        }
    }

    private static Entity entityByUuid(ClientLevel level, UUID uuid) {
        if (entitiesByUuidTick != ticks) {
            entitiesByUuidTick = ticks;
            Map<UUID, Entity> map = new HashMap<>();
            for (Entity entity : level.entitiesForRendering()) {
                map.put(entity.getUUID(), entity);
            }
            entitiesByUuid = map;
        }
        return entitiesByUuid.get(uuid);
    }

    // ── Rendering
    // ──────────────────────────────────────────────────

    /** Adds every light to draw this frame. */
    public static void collect(LightFrame frame, Camera camera, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 cameraPos = camera.getPosition();
        String dimension = currentDimension();
        for (Light light : LOCAL.values()) {
            if (previewReplaces != null && preview != null && previewReplaces.equals(key(light.id))) {
                continue;
            }
            emit(frame, minecraft, light, cameraPos, dimension, partialTick);
        }
        for (Light light : SERVER.values()) {
            if (previewReplaces != null && preview != null && previewReplaces.equals(key(light.id)) && !LOCAL.containsKey(previewReplaces)) {
                continue;
            }
            emit(frame, minecraft, light, cameraPos, dimension, partialTick);
        }
        if (preview != null) {
            emit(frame, minecraft, preview, cameraPos, null, partialTick);
        }
        ObjectLights.collect(frame, cameraPos, partialTick);
        MarkerLights.collect(frame, cameraPos, partialTick);
    }

    private static void emit(LightFrame frame, Minecraft minecraft, Light light, Vec3 camera, String dimension, float partialTick) {
        if (dimension != null && light.dimension != null && !light.dimension.equals(dimension)) {
            return;
        }
        // Saved lights follow their group: on or off with their fades, animated or still (the editor preview is
        // always on and animated)
        float shown = light == preview ? 1.0f : light.groupShownBefore + (light.groupShown - light.groupShownBefore) * partialTick;
        if (shown <= 0.0f) {
            return;
        }
        float motion = light == preview ? 1.0f : light.groupMotionBefore + (light.groupMotion - light.groupMotionBefore) * partialTick;
        Light.Snapshot snapshot = light.snapshot(partialTick, ANIMATION, motion);
        if (shown < 1.0f) {
            float eased = shown * shown * (3.0f - 2.0f * shown);
            snapshot.intensity *= eased;
            snapshot.atmosphere *= eased;
        }
        if (snapshot.intensity <= 0.001f) {
            return;
        }
        if (!light.isFollow()) {
            add(frame, light, snapshot, snapshot.x - camera.x, snapshot.y - camera.y, snapshot.z - camera.z);
            return;
        }
        List<Entity> targets = targets(minecraft, light);
        boolean firstPerson = minecraft.options.getCameraType().isFirstPerson();
        for (Entity entity : targets) {
            if (!light.visibleFirstPerson && firstPerson && entity == minecraft.getCameraEntity()) {
                // Others (and this player in third person) still see it
                continue;
            }
            if (light.shape == LightShape.FLASHLIGHT) {
                // A saved flashlight: from the target's eyes, where it looks (as many as wanted, each with its own id)
                emitFlashlight(frame, minecraft, minecraft.gameRenderer.getMainCamera(), partialTick, light, snapshot, entity,
                        entity == minecraft.getCameraEntity());
                continue;
            }
            Vec3 position = entity.getPosition(partialTick);
            add(frame, light, snapshot,
                    position.x + snapshot.offsetX + (snapshot.x - light.x) - camera.x,
                    position.y + snapshot.offsetY + (snapshot.y - light.y) - camera.y,
                    position.z + snapshot.offsetZ + (snapshot.z - light.z) - camera.z);
        }
    }

    private static void add(LightFrame frame, Light light, Light.Snapshot snapshot, double relX, double relY, double relZ) {
        RenderLight render = frame.add();
        render.x = (float) relX;
        render.y = (float) relY;
        render.z = (float) relZ;
        render.shape = light.shape;
        render.setDirection(snapshot.yaw, snapshot.pitch);
        render.setStretch(light.stretchX, light.stretchY, light.stretchZ);
        render.setPivot(light.pivotX, light.pivotY, light.pivotZ);
        render.setSeed(light.seed);
        render.setColor(snapshot.color, snapshot.intensity);
        render.radius = snapshot.radius;
        render.distance = snapshot.distance;
        render.angle = snapshot.angle;
        render.roll = (float) Math.toRadians(snapshot.roll);
        render.atmosphere = snapshot.atmosphere * snapshot.alpha;
        render.luminosity = light.luminosity * snapshot.alpha;
        render.seeThrough = light.seeThrough;
    }

    /** Whether the flashlight is held by the player themselves (the default) rather than given to someone. */
    private static boolean heldBySelf(Light light) {
        return light.target == null || light.target.isEmpty() || light.target.equalsIgnoreCase(SELF_TARGET) || light.target.equalsIgnoreCase("@s");
    }

    private static void emitFlashlight(LightFrame frame, Minecraft minecraft, Camera camera, float partialTick, Light light) {
        Light.Snapshot snapshot = light.snapshot(partialTick, ANIMATION);
        if (snapshot.intensity <= 0.001f) {
            return;
        }
        if (heldBySelf(light)) {
            Entity entity = minecraft.getCameraEntity();
            if (entity != null) {
                emitFlashlight(frame, minecraft, camera, partialTick, light, snapshot, entity, true);
            }
            return;
        }
        // Given to players or entities: each one shines it where it looks
        for (Entity entity : targets(minecraft, light)) {
            emitFlashlight(frame, minecraft, camera, partialTick, light, snapshot, entity, entity == minecraft.getCameraEntity());
        }
    }

    private static void emitFlashlight(LightFrame frame, Minecraft minecraft, Camera camera, float partialTick, Light light,
                                       Light.Snapshot snapshot, Entity entity, boolean ownView) {
        // In first person the camera is the eye (it also smooths crouching), so the beam stays glued to the view
        Vec3 eye = minecraft.options.getCameraType().isFirstPerson() && entity == camera.getEntity() ? camera.getPosition() : entity.getEyePosition(partialTick);
        // The camera delay smooths the player's own view only, each flashlight on its own (several never fight)
        Vec3 look = ownView ? delayedLook(light.id + '|' + entity.getId(), entity.getViewVector(partialTick), light.cameraDelay)
                : entity.getViewVector(partialTick);
        Vec3 cameraPos = camera.getPosition();
        // Held slightly below and to the right of the eyes, like a torch in hand
        Vec3 up = new Vec3(0.0d, 1.0d, 0.0d);
        Vec3 right = look.cross(up);
        right = right.lengthSqr() < 1.0e-4d ? Vec3.ZERO : right.normalize();
        Vec3 origin = eye.add(right.scale(0.25d)).add(0.0d, -0.2d, 0.0d).add(look.scale(0.1d));
        RenderLight render = frame.add();
        render.x = (float) (origin.x - cameraPos.x);
        render.y = (float) (origin.y - cameraPos.y);
        render.z = (float) (origin.z - cameraPos.z);
        render.shape = light.shape;
        render.setDirectionVector((float) look.x, (float) look.y, (float) look.z);
        render.setStretch(light.stretchX, light.stretchY, light.stretchZ);
        render.setPivot(light.pivotX, light.pivotY, light.pivotZ);
        render.setSeed(light.seed);
        render.setColor(snapshot.color, snapshot.intensity);
        render.radius = snapshot.radius;
        render.distance = snapshot.distance;
        render.angle = snapshot.angle;
        render.roll = (float) Math.toRadians(snapshot.roll);
        render.atmosphere = snapshot.atmosphere * snapshot.alpha;
        render.luminosity = light.luminosity * snapshot.alpha;
        render.seeThrough = light.seeThrough;
    }

    /** Smoothed view direction per flashlight (and holder), with the frame it was last used in. */
    private record Smoothed(Vec3 look, long nanos) {
    }

    private static final Map<String, Smoothed> SMOOTHED_LOOKS = new HashMap<>();

    /** The view direction, lagging behind the camera by about {@code delayTicks}. */
    private static Vec3 delayedLook(String key, Vec3 look, float delayTicks) {
        long now = System.nanoTime();
        Smoothed previous = SMOOTHED_LOOKS.get(key);
        if (delayTicks <= 0.0f || previous == null) {
            SMOOTHED_LOOKS.put(key, new Smoothed(look, now));
            if (SMOOTHED_LOOKS.size() > 64) {
                SMOOTHED_LOOKS.entrySet().removeIf(entry -> now - entry.getValue().nanos() > 2_000_000_000L);
            }
            return look;
        }
        double elapsedTicks = Math.min(20.0d, (now - previous.nanos()) / 50_000_000.0d);
        double follow = 1.0d - Math.exp(-elapsedTicks / delayTicks);
        Vec3 next = previous.look().add(look.subtract(previous.look()).scale(follow));
        next = next.lengthSqr() < 1.0e-6d ? look : next.normalize();
        SMOOTHED_LOOKS.put(key, new Smoothed(next, now));
        return next;
    }
}
