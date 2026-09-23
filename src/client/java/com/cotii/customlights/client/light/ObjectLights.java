package com.cotii.customlights.client.light;

import com.cotii.customlights.Customlights;
import com.cotii.customlights.client.gui.ObjectPreview;
import com.cotii.customlights.client.render.LightFrame;
import com.cotii.customlights.client.render.RenderLight;
import com.cotii.customlights.client.storage.LightStorage;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Lights attached to kinds of objects: items held in first person (optionally with a custom model data), blocks, particles,
 * entities, special effects (beacon beams, burning or glowing entities) and model bones named {@code customlights_<id>}.
 */
public final class ObjectLights {
    public enum Kind {
        ITEM("item"),
        BLOCK("block"),
        PARTICLE("particle"),
        ENTITY("entity"),
        SPECIAL("special"),
        BONE("bone");

        public final String id;

        Kind(String id) {
            this.id = id;
        }

        public String label() {
            return Character.toUpperCase(id.charAt(0)) + id.substring(1);
        }

        public static Kind byName(String name) {
            for (Kind kind : values()) {
                if (kind.id.equalsIgnoreCase(name)) {
                    return kind;
                }
            }
            return null;
        }
    }

    public static final String BEACON_BEAM = "beacon_beam";
    public static final String BURNING_ENTITY = "burning_entity";
    public static final String GLOWING_ENTITY = "glowing_entity";
    public static final String END_GATEWAY_BEAM = "end_gateway_beam";
    public static final String END_CRYSTAL_BEAM = "end_crystal_beam";
    public static final String CONDUIT = "conduit";
    public static final String LIT_BLOCKS = "lit_blocks";
    public static final String CHARGED_CREEPER = "charged_creeper";
    public static final String ENCHANTED_ITEM = "enchanted_item";
    public static final String ENCHANTED_GEAR = "enchanted_gear";
    public static final String HOSTILE_MOBS = "hostile_mobs";
    public static final String ANIMALS = "animals";
    public static final String PLAYERS = "players";
    public static final String PROJECTILES = "projectiles";
    public static final String BOSSES = "bosses";
    public static final String ITEM_ENTITIES = "item_entities";
    /** Targets of the special kind. */
    public static final List<String> SPECIAL_TARGETS = List.of(BEACON_BEAM, END_GATEWAY_BEAM, END_CRYSTAL_BEAM, CONDUIT, LIT_BLOCKS, BURNING_ENTITY,
            GLOWING_ENTITY, CHARGED_CREEPER, ENCHANTED_ITEM, ENCHANTED_GEAR, HOSTILE_MOBS, ANIMALS, PLAYERS, PROJECTILES, BOSSES, ITEM_ENTITIES);
    /** Stands for any lit block in the block index. */
    private static final Block LIT_BLOCK = net.minecraft.world.level.block.Blocks.VOID_AIR;

    /** Where an item light shows in one display context, like Blockbench display settings (offset in pixels). */
    public static final class Display {
        public boolean on;
        public double x, y, z;
        /** This context's own light, or null to use the rule's. */
        public Light light;

        public Display(boolean on, double x, double y, double z) {
            this.on = on;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public Display copy() {
            Display copy = new Display(on, x, y, z);
            if (light != null) {
                copy.light = light.copy(light.id);
                copy.light.age = light.age;
            }
            return copy;
        }
    }

    /** The display contexts an item light can show in (menus never draw lights). */
    public static final List<ItemDisplayContext> DISPLAYS = List.of(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
            ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, ItemDisplayContext.HEAD, ItemDisplayContext.GROUND,
            ItemDisplayContext.FIXED);

    /** By default an item lights only while held in first person. */
    public static Map<ItemDisplayContext, Display> defaultDisplays() {
        Map<ItemDisplayContext, Display> displays = new EnumMap<>(ItemDisplayContext.class);
        for (ItemDisplayContext context : DISPLAYS) {
            displays.put(context, new Display(context.firstPerson(), 0.0d, 0.0d, 0.0d));
        }
        return displays;
    }

    /** A light for one kind of object; {@code light.x/y/z} is the offset from the object. */
    public static final class Rule {
        public Kind kind;
        /** Registry id ({@code minecraft:torch}), a special target or, for bones, the model id. */
        public String target;
        /** Items only: the custom model data to match, or -1 for any. */
        public int customModelData = -1;
        public Light light;
        /** Items only: where the light shows for each display context. */
        public final Map<ItemDisplayContext, Display> displays = defaultDisplays();

        public Rule(Kind kind, String target, int customModelData, Light light) {
            this.kind = kind;
            this.target = target;
            this.customModelData = customModelData;
            this.light = light;
        }

        public Display display(ItemDisplayContext context) {
            return displays.get(context);
        }

        /** The light an item shows in a display context. */
        public Light displayLight(ItemDisplayContext context) {
            Display display = displays.get(context);
            return display != null && display.light != null ? display.light : light;
        }

        public String key() {
            return keyOf(kind, target, customModelData);
        }

        public String label() {
            return target + (kind == Kind.ITEM && customModelData >= 0 ? " #" + customModelData : "");
        }

        public Rule copy() {
            Light copy = light.copy(light.id);
            copy.age = light.age;
            Rule rule = new Rule(kind, target, customModelData, copy);
            displays.forEach((context, display) -> rule.displays.put(context, display.copy()));
            return rule;
        }

        public JsonObject toJson() {
            JsonObject object = new JsonObject();
            object.addProperty("kind", kind.id);
            object.addProperty("target", target);
            if (customModelData >= 0) {
                object.addProperty("customModelData", customModelData);
            }
            object.add("light", light.toJson());
            if (kind == Kind.ITEM) {
                JsonObject shown = new JsonObject();
                displays.forEach((context, display) -> {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("on", display.on);
                    if (display.x != 0.0d || display.y != 0.0d || display.z != 0.0d) {
                        JsonArray offset = new JsonArray();
                        offset.add(display.x);
                        offset.add(display.y);
                        offset.add(display.z);
                        entry.add("offset", offset);
                    }
                    if (display.light != null) {
                        entry.add("light", display.light.toJson());
                    }
                    shown.add(context.getSerializedName(), entry);
                });
                object.add("displays", shown);
            }
            return object;
        }

        public static Rule fromJson(JsonObject object) {
            Kind kind = Kind.byName(object.get("kind").getAsString());
            if (kind == null) {
                throw new IllegalArgumentException("unknown kind");
            }
            String target = object.get("target").getAsString();
            int cmd = object.has("customModelData") ? object.get("customModelData").getAsInt() : -1;
            Light light = Light.fromJson(object.getAsJsonObject("light"));
            light.age = light.fadeIn;
            Rule rule = new Rule(kind, target, cmd, light);
            if (object.has("displays")) {
                JsonObject shown = object.getAsJsonObject("displays");
                for (ItemDisplayContext context : DISPLAYS) {
                    if (shown.has(context.getSerializedName())) {
                        JsonObject entry = shown.getAsJsonObject(context.getSerializedName());
                        JsonArray offset = entry.has("offset") ? entry.getAsJsonArray("offset") : null;
                        Display display = new Display(entry.has("on") && entry.get("on").getAsBoolean(),
                                offset == null ? 0.0d : offset.get(0).getAsDouble(), offset == null ? 0.0d : offset.get(1).getAsDouble(),
                                offset == null ? 0.0d : offset.get(2).getAsDouble());
                        if (entry.has("light")) {
                            display.light = Light.fromJson(entry.getAsJsonObject("light"));
                            display.light.age = display.light.fadeIn;
                        }
                        rule.displays.put(context, display);
                    }
                }
            }
            return rule;
        }
    }

    public static String keyOf(Kind kind, String target, int customModelData) {
        return kind.id + ":" + target.toLowerCase(Locale.ROOT) + (kind == Kind.ITEM && customModelData >= 0 ? "#" + customModelData : "");
    }

    /** A small ready-made light for a new rule of this kind (objects are small, so their lights are too). */
    public static Light defaultLight(Kind kind, String target) {
        Light light = new Light("object");
        boolean beam = kind == Kind.SPECIAL && (BEACON_BEAM.equals(target) || END_GATEWAY_BEAM.equals(target) || END_CRYSTAL_BEAM.equals(target));
        if (beam) {
            LightShape.BEAM.applyLook(light);
            light.radius = END_CRYSTAL_BEAM.equals(target) ? 0.25f : 0.35f;
            light.distance = 1.2f;
            light.intensity = 1.4f;
            light.atmosphere = 3.0f;
            light.stretchY = 6.0f;
            light.color = switch (target) {
                case END_GATEWAY_BEAM -> 0xC080FF;
                case END_CRYSTAL_BEAM -> 0xFF90E0;
                default -> 0xFFFFFF;
            };
        } else {
            float scale = switch (kind) {
                case ITEM, BONE -> 0.25f;
                case PARTICLE -> 0.15f;
                case BLOCK -> 0.45f;
                case ENTITY, SPECIAL -> 0.6f;
            };
            LightShape.SPHERE.applyLook(light, scale);
            light.color = kind != Kind.SPECIAL ? 0xFFB050 : switch (target) {
                case GLOWING_ENTITY, PLAYERS, ITEM_ENTITIES -> 0xFFFFFF;
                case CONDUIT -> 0x60E0FF;
                case CHARGED_CREEPER -> 0x80C0FF;
                case ENCHANTED_ITEM, ENCHANTED_GEAR -> 0xB070FF;
                case HOSTILE_MOBS -> 0xFF4040;
                case ANIMALS -> 0xFFE0A0;
                case BOSSES -> 0xC040FF;
                default -> 0xFFB050;
            };
            if (kind == Kind.SPECIAL && LIT_BLOCKS.equals(target)) {
                LightShape.SPHERE.applyLook(light, 0.45f);
                light.intensity = 1.4f;
            }
            light.intensity = 1.4f;
        }
        light.fadeIn = 5;
        light.fadeOut = 5;
        return light;
    }

    /** Marker template keys of object rules start with this (bones use their plain id). */
    public static final String MARKER_PREFIX = "rule:";
    private static final int BLOCK_LIGHT_LIMIT = 64;
    private static final int PARTICLE_LIGHT_LIMIT = 32;
    private static final int ENTITY_LIGHT_LIMIT = 32;
    private static final int PARTICLE_TRACK_LIMIT = 4096;
    /** Nanoseconds a tick may spend indexing chunks. */
    private static final long SCAN_BUDGET_NANOS = 2_000_000L;
    private static final double BLOCK_RANGE = 112.0d;
    private static final double PARTICLE_RANGE = 64.0d;
    private static final double ENTITY_RANGE = 96.0d;
    private static final double PARTICLE_CELL = 1.5d;
    private static final double ENTITY_CELL = 2.0d;

    private static final Map<String, Rule> RULES = new LinkedHashMap<>();
    /**
     * Object lights the server plugin gives everyone (never saved here; this client's own rule for the same object wins).
     */
    private static final Map<String, Rule> SERVER_RULES = new LinkedHashMap<>();
    private static Rule preview;
    private static boolean loaded;
    private static boolean dirty;

    // Lookup caches, rebuilt when rules change
    private static final Map<Item, List<Rule>> ITEMS = new IdentityHashMap<>();
    private static final Map<Block, Rule> BLOCKS = new IdentityHashMap<>();
    private static final Map<ParticleType<?>, Rule> PARTICLES = new IdentityHashMap<>();
    private static final Map<EntityType<?>, Rule> ENTITIES = new IdentityHashMap<>();
    private static final Map<String, Rule> SPECIALS = new HashMap<>();
    private static final Map<String, Rule> BONES = new HashMap<>();

    // Blocks with a rule in the loaded chunks, already merged
    private static final Map<Long, ChunkBlocks> BLOCKS_BY_CHUNK = new HashMap<>();
    private static final List<ChunkBlocks> NEARBY = new ArrayList<>();
    private static final Deque<Long> CHUNK_QUEUE = new ArrayDeque<>();
    private static final Set<Long> CHANGED_CHUNKS = new LinkedHashSet<>();
    private static ClientLevel indexedLevel;

    // Particles with a rule and entity lights of this frame
    private static final Map<Particle, ParticleType<?>> TRACKED_PARTICLES = new IdentityHashMap<>();
    private static final List<Pending> PENDING = new ArrayList<>();
    private static int pendingCount;
    private static final LightAnimation.Result ANIMATION = new LightAnimation.Result();

    // Reused every frame
    private static final List<Cluster> CANDIDATES = new ArrayList<>();
    private static final Map<Long, Cluster> CELLS = new HashMap<>();
    private static final List<Cluster> CELL_POOL = new ArrayList<>();
    private static int cellPoolUsed;

    private ObjectLights() {
    }

    // ── Rules
    // ──────────────────────────────────────────────────────

    public static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        RULES.clear();
        JsonObject root = LightStorage.loadObjects();
        if (root != null && root.has("objects")) {
            for (JsonElement element : root.getAsJsonArray("objects")) {
                try {
                    Rule rule = Rule.fromJson(element.getAsJsonObject());
                    RULES.put(rule.key(), rule);
                } catch (RuntimeException exception) {
                    Customlights.LOGGER.warn("Skipping a broken object light", exception);
                }
            }
        }
        // Model lights saved before objects existed
        for (Light template : LightStorage.loadModels()) {
            Rule rule = new Rule(Kind.BONE, template.id, -1, template);
            template.age = template.fadeIn;
            RULES.putIfAbsent(rule.key(), rule);
            dirty = true;
        }
        rebuild();
    }

    public static void saveIfDirty() {
        if (!dirty) {
            return;
        }
        dirty = false;
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonArray array = new JsonArray();
        for (Rule rule : RULES.values()) {
            if (!rule.light.removing) {
                array.add(rule.toJson());
            }
        }
        root.add("objects", array);
        LightStorage.saveObjects(root);
        LightStorage.retireModels();
    }

    public static Collection<Rule> rules() {
        return RULES.values();
    }

    public static Rule rule(String key) {
        return RULES.get(key);
    }

    public static void put(Rule rule) {
        Rule previous = RULES.put(rule.key(), rule);
        if (previous != null && !previous.light.removing) {
            rule.light.age = Math.max(rule.light.age, rule.light.fadeIn);
        }
        dirty = true;
        rebuild();
    }

    public static boolean remove(String key, Integer fadeOut) {
        Rule rule = RULES.get(key);
        if (rule == null || rule.light.removing) {
            return false;
        }
        rule.light.startRemove(fadeOut == null ? rule.light.fadeOut : fadeOut);
        dirty = true;
        return true;
    }

    // Server object lights

    public static void serverFull(Collection<Rule> rules) {
        SERVER_RULES.clear();
        for (Rule rule : rules) {
            SERVER_RULES.put(rule.key(), rule);
        }
        rebuild();
    }

    public static void putFromServer(Rule rule) {
        Rule previous = SERVER_RULES.put(rule.key(), rule);
        if (previous != null && !previous.light.removing) {
            rule.light.age = Math.max(rule.light.age, rule.light.fadeIn);
        }
        rebuild();
    }

    public static void removeFromServer(String key) {
        Rule rule = SERVER_RULES.get(key);
        if (rule != null && !rule.light.removing) {
            rule.light.startRemove(rule.light.fadeOut);
        }
    }

    public static void clearServer() {
        if (!SERVER_RULES.isEmpty()) {
            SERVER_RULES.clear();
            rebuild();
        }
    }

    public static Collection<Rule> serverRules() {
        return SERVER_RULES.values();
    }

    public static Rule serverRule(String key) {
        return SERVER_RULES.get(key);
    }

    /** The editor's unsaved rule, shown instead of the saved one for the same object (null to stop). */
    public static void setPreview(Rule rule) {
        String before = preview == null ? null : preview.key();
        preview = rule;
        String after = rule == null ? null : rule.key();
        if (before == null ? after != null : !before.equals(after)) {
            rebuild();
        }
    }

    public static Rule preview() {
        return preview;
    }

    /** The rule to use for a looked up one: the editor's draft while it edits the same object. */
    private static Rule live(Rule rule) {
        Rule draft = preview;
        return draft != null && draft != rule && draft.kind == rule.kind && draft.key().equals(rule.key()) ? draft : rule;
    }

    public static void markDirty() {
        dirty = true;
    }

    // Bones (model_id)

    public static Light bone(String id) {
        Rule rule = BONES.get(id.toLowerCase(Locale.ROOT));
        return rule == null ? null : live(rule).light;
    }

    public static List<String> boneIds() {
        List<String> ids = new ArrayList<>();
        for (Rule rule : RULES.values()) {
            if (rule.kind == Kind.BONE && !rule.light.removing) {
                ids.add(rule.target);
            }
        }
        return ids;
    }

    public static boolean isRuleLight(Light light) {
        for (Rule rule : SERVER_RULES.values()) {
            if (rule.light == light) {
                return true;
            }
        }
        for (Rule rule : RULES.values()) {
            if (rule.light == light) {
                return true;
            }
        }
        return false;
    }

    private static void rebuild() {
        Set<Block> blocksBefore = new java.util.HashSet<>(BLOCKS.keySet());
        boolean litBefore = SPECIALS.containsKey(LIT_BLOCKS);
        ITEMS.clear();
        BLOCKS.clear();
        PARTICLES.clear();
        ENTITIES.clear();
        SPECIALS.clear();
        BONES.clear();
        List<Rule> all = new ArrayList<>();
        for (Rule rule : SERVER_RULES.values()) {
            Rule own = RULES.get(rule.key());
            if (own == null || own.light.removing) {
                all.add(rule);
            }
        }
        all.addAll(RULES.values());
        if (preview != null) {
            all.removeIf(rule -> rule.key().equals(preview.key()));
            all.add(preview);
        }
        for (Rule rule : all) {
            ResourceLocation id = ResourceLocation.tryParse(rule.target);
            switch (rule.kind) {
                case ITEM -> {
                    if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                        ITEMS.computeIfAbsent(com.cotii.customlights.client.compat.Compat.item(id), item -> new ArrayList<>()).add(rule);
                    }
                }
                case BLOCK -> {
                    if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
                        BLOCKS.put(com.cotii.customlights.client.compat.Compat.block(id), rule);
                    }
                }
                case PARTICLE -> {
                    if (id != null && BuiltInRegistries.PARTICLE_TYPE.containsKey(id)) {
                        PARTICLES.put(com.cotii.customlights.client.compat.Compat.particleType(id), rule);
                    }
                }
                case ENTITY -> {
                    if (id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
                        ENTITIES.put(com.cotii.customlights.client.compat.Compat.entityType(id), rule);
                    }
                }
                case SPECIAL -> SPECIALS.put(rule.target.toLowerCase(Locale.ROOT), rule);
                case BONE -> BONES.put(rule.target.toLowerCase(Locale.ROOT), rule);
            }
        }
        if (!blocksBefore.equals(BLOCKS.keySet()) || litBefore != SPECIALS.containsKey(LIT_BLOCKS)) {
            // Other blocks have lights now: index the loaded chunks again
            BLOCKS_BY_CHUNK.clear();
            CHANGED_CHUNKS.clear();
            indexedLevel = null;
        }
    }

    // ── Lookups used by the render hooks ──────────────────────────

    public static boolean hasItemRules() {
        return !ITEMS.isEmpty();
    }

    /** The rule for an item stack (a custom model data match wins over "any"). */
    public static Rule itemRule(ItemStack stack) {
        List<Rule> rules = ITEMS.get(stack.getItem());
        if (rules == null) {
            return null;
        }
        int customModelData = com.cotii.customlights.client.compat.Compat.customModelData(stack);
        Rule any = null;
        for (Rule rule : rules) {
            if (rule.customModelData < 0) {
                any = rule;
            } else if (customModelData == rule.customModelData) {
                return live(rule);
            }
        }
        return any == null ? null : live(any);
    }

    public static String markerKey(Rule rule) {
        return MARKER_PREFIX + rule.key();
    }

    /** Marker key of an item rule in one display context (each context has its own light). */
    public static String markerKey(Rule rule, ItemDisplayContext context) {
        return MARKER_PREFIX + rule.key() + "@" + context.getSerializedName();
    }

    /** Light of a marker key: object rules by key, bones by id (server model lights first). */
    public static Light markerTemplate(String key) {
        if (key.startsWith(MARKER_PREFIX)) {
            String ruleKey = key.substring(MARKER_PREFIX.length());
            ItemDisplayContext context = null;
            int at = ruleKey.lastIndexOf('@');
            if (at >= 0) {
                String name = ruleKey.substring(at + 1);
                ruleKey = ruleKey.substring(0, at);
                for (ItemDisplayContext each : DISPLAYS) {
                    if (each.getSerializedName().equals(name)) {
                        context = each;
                    }
                }
            }
            Rule rule = preview != null && preview.key().equals(ruleKey) ? preview : RULES.get(ruleKey);
            if (rule == null) {
                rule = SERVER_RULES.get(ruleKey);
            }
            if (rule == null) {
                return null;
            }
            return context == null ? rule.light : rule.displayLight(context);
        }
        return LightManager.template(key);
    }

    public static void onParticleCreated(Particle particle, ParticleType<?> type) {
        if (particle == null || PARTICLES.isEmpty() || !PARTICLES.containsKey(type) || TRACKED_PARTICLES.size() >= PARTICLE_TRACK_LIMIT) {
            return;
        }
        TRACKED_PARTICLES.put(particle, type);
    }

    /** An entity is being drawn this frame. */
    public static void onEntityRendered(Entity entity, float partialTick) {
        if ((ENTITIES.isEmpty() && SPECIALS.isEmpty()) || MarkerLights.phase() != MarkerLights.Phase.WORLD) {
            return;
        }
        Rule rule = ENTITIES.get(entity.getType());
        if (rule != null) {
            addEntityLight(rule, entity, partialTick, -1);
        }
        if (!SPECIALS.isEmpty()) {
            special(BURNING_ENTITY, entity, partialTick, entity.displayFireAnimation(), -1);
            if (SPECIALS.containsKey(GLOWING_ENTITY)) {
                special(GLOWING_ENTITY, entity, partialTick, Minecraft.getInstance().shouldEntityAppearGlowing(entity), entity.getTeamColor() & 0xFFFFFF);
            }
            special(CHARGED_CREEPER, entity, partialTick, entity instanceof net.minecraft.world.entity.monster.Creeper creeper && creeper.isPowered(), -1);
            special(ENCHANTED_ITEM, entity, partialTick, entity instanceof net.minecraft.world.entity.item.ItemEntity item && item.getItem().hasFoil(), -1);
            special(ITEM_ENTITIES, entity, partialTick, entity instanceof net.minecraft.world.entity.item.ItemEntity, -1);
            special(HOSTILE_MOBS, entity, partialTick, entity instanceof net.minecraft.world.entity.monster.Enemy, -1);
            special(ANIMALS, entity, partialTick, entity instanceof net.minecraft.world.entity.animal.Animal, -1);
            special(PLAYERS, entity, partialTick, entity instanceof net.minecraft.world.entity.player.Player, -1);
            special(PROJECTILES, entity, partialTick, entity instanceof net.minecraft.world.entity.projectile.Projectile, -1);
            special(BOSSES, entity, partialTick, entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon
                    || entity instanceof net.minecraft.world.entity.boss.wither.WitherBoss, -1);
            if (SPECIALS.containsKey(ENCHANTED_GEAR) && entity instanceof net.minecraft.world.entity.LivingEntity living) {
                boolean enchanted = false;
                for (ItemStack stack : com.cotii.customlights.client.compat.Compat.equipment(living)) {
                    if (stack.hasFoil()) {
                        enchanted = true;
                        break;
                    }
                }
                special(ENCHANTED_GEAR, entity, partialTick, enchanted, -1);
            }
            Rule crystalBeam = SPECIALS.get(END_CRYSTAL_BEAM);
            if (crystalBeam != null && entity instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal crystal && crystal.getBeamTarget() != null) {
                // From the crystal to the block it points at
                Vec3 from = crystal.getPosition(partialTick).add(0.0d, 1.0d, 0.0d);
                BlockPos to = crystal.getBeamTarget();
                Pending pending = blockPending(crystalBeam, BlockPos.containing(from), -1);
                pending.x = from.x;
                pending.y = from.y;
                pending.z = from.z;
                pending.owner = entity.getId();
                pending.aim = new Vec3(to.getX() + 0.5d - from.x, to.getY() + 0.5d - from.y, to.getZ() + 0.5d - from.z);
            }
        }
    }

    private static void special(String id, Entity entity, float partialTick, boolean matches, int tint) {
        if (matches) {
            Rule rule = SPECIALS.get(id);
            if (rule != null) {
                addEntityLight(rule, entity, partialTick, tint);
            }
        }
    }

    private static void addEntityLight(Rule rule, Entity entity, float partialTick, int tint) {
        Vec3 position = entity.getPosition(partialTick);
        Pending pending = pending();
        pending.rule = rule;
        pending.x = position.x;
        pending.y = position.y + entity.getBbHeight() * 0.5d;
        pending.z = position.z;
        pending.yaw = entity.getViewYRot(partialTick);
        pending.tint = tint;
        pending.owner = entity.getId();
        pending.aim = null;
    }

    /** A block entity is being drawn this frame (beacon and end gateway beams, active conduits). */
    public static void onBlockEntityRendered(BlockEntity blockEntity) {
        if (SPECIALS.isEmpty() || MarkerLights.phase() != MarkerLights.Phase.WORLD) {
            return;
        }
        if (blockEntity instanceof BeaconBlockEntity beacon) {
            Rule rule = SPECIALS.get(BEACON_BEAM);
            // The beam takes the color it has above the glass
            int beamColor = com.cotii.customlights.client.compat.Compat.beaconTopColor(beacon);
            if (rule != null && beamColor >= 0) {
                blockPending(rule, beacon.getBlockPos(), beamColor);
            }
        } else if (blockEntity instanceof net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity gateway) {
            Rule rule = SPECIALS.get(END_GATEWAY_BEAM);
            if (rule != null && (gateway.isSpawning() || gateway.isCoolingDown())) {
                blockPending(rule, gateway.getBlockPos(), -1);
            }
        } else if (blockEntity instanceof net.minecraft.world.level.block.entity.ConduitBlockEntity conduit) {
            Rule rule = SPECIALS.get(CONDUIT);
            if (rule != null && conduit.isActive()) {
                blockPending(rule, conduit.getBlockPos(), -1);
            }
        }
    }

    private static Pending blockPending(Rule rule, BlockPos pos, int tint) {
        Pending pending = pending();
        pending.rule = rule;
        pending.x = pos.getX() + 0.5d;
        pending.y = pos.getY() + 0.5d;
        pending.z = pos.getZ() + 0.5d;
        pending.yaw = 0.0f;
        pending.tint = tint;
        pending.owner = pos.asLong();
        pending.aim = null;
        return pending;
    }

    private static Pending pending() {
        if (pendingCount == PENDING.size()) {
            PENDING.add(new Pending());
        }
        return PENDING.get(pendingCount++);
    }

    public static void onBlockChanged(BlockPos pos, BlockState oldState, BlockState newState) {
        com.cotii.customlights.client.render.OcclusionGrids.blockChanged(pos.getX(), pos.getY(), pos.getZ());
        if (!hasBlockLights() || (blockKey(oldState) == null && blockKey(newState) == null && oldState.canOcclude() == newState.canOcclude())) {
            return;
        }
        // Index the chunk again soon (its neighbours too when the block is on an edge, for the open faces)
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        CHANGED_CHUNKS.add(ChunkPos.asLong(chunkX, chunkZ));
        int localX = pos.getX() & 15;
        int localZ = pos.getZ() & 15;
        if (localX == 0) CHANGED_CHUNKS.add(ChunkPos.asLong(chunkX - 1, chunkZ));
        if (localX == 15) CHANGED_CHUNKS.add(ChunkPos.asLong(chunkX + 1, chunkZ));
        if (localZ == 0) CHANGED_CHUNKS.add(ChunkPos.asLong(chunkX, chunkZ - 1));
        if (localZ == 15) CHANGED_CHUNKS.add(ChunkPos.asLong(chunkX, chunkZ + 1));
    }

    /** Whether some block (or lit blocks) has a light. */
    private static boolean hasBlockLights() {
        return !BLOCKS.isEmpty() || SPECIALS.containsKey(LIT_BLOCKS);
    }

    /** The index key of a block state: its block when it has a rule, the lit marker for lit blocks, or null. */
    private static Block blockKey(BlockState state) {
        Block block = state.getBlock();
        if (BLOCKS.containsKey(block)) {
            return block;
        }
        if (SPECIALS.containsKey(LIT_BLOCKS) && state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
                && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)) {
            return LIT_BLOCK;
        }
        return null;
    }

    private static Rule blockRule(Block key) {
        return key == LIT_BLOCK ? SPECIALS.get(LIT_BLOCKS) : BLOCKS.get(key);
    }

    public static void onChunkLoaded(LevelChunk chunk) {
        com.cotii.customlights.client.render.OcclusionGrids.chunkLoaded(chunk.getPos().x, chunk.getPos().z);
        if (hasBlockLights()) {
            CHUNK_QUEUE.add(chunk.getPos().toLong());
        }
    }

    public static void onChunkUnloaded(LevelChunk chunk) {
        BLOCKS_BY_CHUNK.remove(chunk.getPos().toLong());
    }

    // ── Tick and block index
    // ───────────────────────────────────────

    public static void tick(Minecraft minecraft) {
        load();
        Iterator<Rule> iterator = RULES.values().iterator();
        boolean removed = false;
        while (iterator.hasNext()) {
            Rule rule = iterator.next();
            rule.light.tick();
            tickDisplays(rule);
            if (rule.light.finishedRemoving()) {
                iterator.remove();
                removed = true;
            }
        }
        Iterator<Rule> serverIterator = SERVER_RULES.values().iterator();
        while (serverIterator.hasNext()) {
            Rule rule = serverIterator.next();
            rule.light.tick();
            tickDisplays(rule);
            if (rule.light.finishedRemoving()) {
                serverIterator.remove();
                removed = true;
            }
        }
        if (preview != null) {
            preview.light.tick();
            tickDisplays(preview);
        }
        if (removed) {
            rebuild();
        }
        ClientLevel level = minecraft.level;
        if (level == null) {
            BLOCKS_BY_CHUNK.clear();
            CHUNK_QUEUE.clear();
            CHANGED_CHUNKS.clear();
            TRACKED_PARTICLES.clear();
            indexedLevel = null;
            return;
        }
        if (indexedLevel != level) {
            indexedLevel = level;
            BLOCKS_BY_CHUNK.clear();
            CHUNK_QUEUE.clear();
            if (hasBlockLights() && minecraft.player != null) {
                // Nearest chunks first
                int radius = minecraft.options.getEffectiveRenderDistance() + 1;
                ChunkPos center = minecraft.player.chunkPosition();
                for (int ring = 0; ring <= radius; ring++) {
                    for (int dx = -ring; dx <= ring; dx++) {
                        for (int dz = -ring; dz <= ring; dz++) {
                            if (Math.max(Math.abs(dx), Math.abs(dz)) == ring) {
                                CHUNK_QUEUE.add(ChunkPos.asLong(center.x + dx, center.z + dz));
                            }
                        }
                    }
                }
            }
        }
        if (hasBlockLights()) {
            long deadline = System.nanoTime() + SCAN_BUDGET_NANOS;
            while (!CHANGED_CHUNKS.isEmpty() && System.nanoTime() < deadline) {
                Iterator<Long> changed = CHANGED_CHUNKS.iterator();
                long chunkPos = changed.next();
                changed.remove();
                scan(level, chunkPos);
            }
            while (!CHUNK_QUEUE.isEmpty() && System.nanoTime() < deadline) {
                scan(level, CHUNK_QUEUE.poll());
            }
        } else {
            CHUNK_QUEUE.clear();
            CHANGED_CHUNKS.clear();
        }
        TRACKED_PARTICLES.keySet().removeIf(particle -> !particle.isAlive());
    }

    private static void tickDisplays(Rule rule) {
        for (Display display : rule.displays.values()) {
            if (display.light != null) {
                display.light.tick();
            }
        }
    }

    private static void scan(ClientLevel level, long chunkPos) {
        ChunkAccess access = com.cotii.customlights.client.compat.Compat.fullChunk(level, ChunkPos.getX(chunkPos), ChunkPos.getZ(chunkPos));
        if (!(access instanceof LevelChunk chunk)) {
            BLOCKS_BY_CHUNK.remove(chunkPos);
            return;
        }
        List<Long> positions = new ArrayList<>();
        List<Block> blocks = new ArrayList<>();
        Map<Block, Map<Long, Cluster>> medium = new IdentityHashMap<>();
        Map<Block, Map<Long, Cluster>> coarse = new IdentityHashMap<>();
        LevelChunkSection[] sections = chunk.getSections();
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section == null || section.hasOnlyAir() || !section.maybeHas(state -> blockKey(state) != null)) {
                continue;
            }
            LevelChunkSection below = index > 0 ? sections[index - 1] : null;
            LevelChunkSection above = index + 1 < sections.length ? sections[index + 1] : null;
            int baseY = chunk.getSectionYFromSectionIndex(index) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        Block block = blockKey(section.getBlockState(x, y, z));
                        if (block == null || !exposed(section, below, above, x, y, z)) {
                            continue;
                        }
                        int worldX = baseX + x;
                        int worldY = baseY + y;
                        int worldZ = baseZ + z;
                        positions.add(BlockPos.asLong(worldX, worldY, worldZ));
                        blocks.add(block);
                        add(medium, block, BlockPos.asLong(worldX >> 2, worldY >> 2, worldZ >> 2), worldX, worldY, worldZ);
                        add(coarse, block, BlockPos.asLong(worldX >> 4, worldY >> 4, worldZ >> 4), worldX, worldY, worldZ);
                    }
                }
            }
        }
        if (positions.isEmpty()) {
            BLOCKS_BY_CHUNK.remove(chunkPos);
        } else {
            long[] packed = new long[positions.size()];
            for (int i = 0; i < packed.length; i++) {
                packed[i] = positions.get(i);
            }
            BLOCKS_BY_CHUNK.put(chunkPos, new ChunkBlocks(packed, blocks.toArray(Block[]::new), finish(medium), finish(coarse)));
        }
    }

    /** Whether a block has a neighbour that does not hide it (blocks on the chunk's sides count as open). */
    private static boolean exposed(LevelChunkSection section, LevelChunkSection below, LevelChunkSection above, int x, int y, int z) {
        if (x == 0 || x == 15 || z == 0 || z == 15) {
            return true;
        }
        if (!section.getBlockState(x - 1, y, z).canOcclude() || !section.getBlockState(x + 1, y, z).canOcclude()
                || !section.getBlockState(x, y, z - 1).canOcclude() || !section.getBlockState(x, y, z + 1).canOcclude()) {
            return true;
        }
        BlockState down = y > 0 ? section.getBlockState(x, y - 1, z) : below == null ? null : below.getBlockState(x, 15, z);
        BlockState up = y < 15 ? section.getBlockState(x, y + 1, z) : above == null ? null : above.getBlockState(x, 0, z);
        return down == null || up == null || !down.canOcclude() || !up.canOcclude();
    }

    private static void add(Map<Block, Map<Long, Cluster>> clusters, Block block, long cell, int x, int y, int z) {
        Cluster cluster = clusters.computeIfAbsent(block, ignored -> new HashMap<>()).computeIfAbsent(cell, ignored -> new Cluster());
        cluster.block = block;
        cluster.add(x + 0.5d, y + 0.5d, z + 0.5d);
    }

    private static Cluster[] finish(Map<Block, Map<Long, Cluster>> clusters) {
        List<Cluster> list = new ArrayList<>();
        for (Map<Long, Cluster> cells : clusters.values()) {
            for (Cluster cluster : cells.values()) {
                cluster.finish();
                list.add(cluster);
            }
        }
        return list.toArray(Cluster[]::new);
    }

    /** The blocks with a light in one chunk: each block, and the same merged in 4-block cells and 16-block sections. */
    private static final class ChunkBlocks {
        final long[] positions;
        final Block[] blocks;
        final Cluster[] medium;
        final Cluster[] coarse;
        double distance;
        /** Detail this frame: 0 = each block, 1 = cells, 2 = sections. */
        int level;

        ChunkBlocks(long[] positions, Block[] blocks, Cluster[] medium, Cluster[] coarse) {
            this.positions = positions;
            this.blocks = blocks;
            this.medium = medium;
            this.coarse = coarse;
        }

        int count(int detail) {
            return detail == 0 ? positions.length : detail == 1 ? medium.length : coarse.length;
        }
    }

    /** Several objects of the same kind merged into one light (or a single one). */
    private static final class Cluster {
        Block block;
        Rule rule;
        ParticleType<?> particle;
        double minX, minY, minZ, maxX, maxY, maxZ;
        /** Centre and half sizes of the box the members fill. */
        double x, y, z, halfX, halfY, halfZ;
        int count;
        float yaw;
        int tint = -1;
        double sortKey;
        Vec3 aim;

        void reset() {
            block = null;
            aim = null;
            rule = null;
            particle = null;
            count = 0;
            yaw = 0.0f;
            tint = -1;
        }

        void add(double px, double py, double pz) {
            if (count == 0) {
                minX = maxX = px;
                minY = maxY = py;
                minZ = maxZ = pz;
            } else {
                minX = Math.min(minX, px);
                minY = Math.min(minY, py);
                minZ = Math.min(minZ, pz);
                maxX = Math.max(maxX, px);
                maxY = Math.max(maxY, py);
                maxZ = Math.max(maxZ, pz);
            }
            count++;
        }

        void finish() {
            x = (minX + maxX) * 0.5d;
            y = (minY + maxY) * 0.5d;
            z = (minZ + maxZ) * 0.5d;
            halfX = (maxX - minX) * 0.5d;
            halfY = (maxY - minY) * 0.5d;
            halfZ = (maxZ - minZ) * 0.5d;
        }
    }

    /** A light found while drawing entities or block entities. */
    private static final class Pending {
        Rule rule;
        /** The entity or block it belongs to: something drawn twice in a frame only counts once. */
        long owner;
        /** Where a beam points (its length is how far it goes), or null to use the rule direction. */
        Vec3 aim;
        double x, y, z;
        float yaw;
        int tint;
    }

    // ── Rendering
    // ──────────────────────────────────────────────────

    /** Block, particle, entity and special lights of this frame. */
    public static void collect(LightFrame frame, Vec3 camera, float partialTick) {
        if (hasBlockLights() && !BLOCKS_BY_CHUNK.isEmpty()) {
            collectBlocks(frame, camera, partialTick);
        }
        if (!TRACKED_PARTICLES.isEmpty()) {
            collectParticles(frame, camera, partialTick);
        }
        if (pendingCount > 0) {
            collectPending(frame, camera, partialTick);
        }
        pendingCount = 0;
        ObjectPreview.collect(frame, camera, partialTick);
    }

    /**
     * Block lights: every chunk starts with one light per 16-block section, then the nearest chunks get one light per
     * 4-block cell and finally one per block, as long as the budget allows.
     */
    private static void collectBlocks(LightFrame frame, Vec3 camera, float partialTick) {
        NEARBY.clear();
        int total = 0;
        for (Map.Entry<Long, ChunkBlocks> entry : BLOCKS_BY_CHUNK.entrySet()) {
            long chunkPos = entry.getKey();
            double minX = ChunkPos.getX(chunkPos) << 4;
            double minZ = ChunkPos.getZ(chunkPos) << 4;
            double dx = Math.max(0.0d, Math.max(minX - camera.x, camera.x - (minX + 16.0d)));
            double dz = Math.max(0.0d, Math.max(minZ - camera.z, camera.z - (minZ + 16.0d)));
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance > BLOCK_RANGE) {
                continue;
            }
            ChunkBlocks chunk = entry.getValue();
            chunk.distance = distance;
            chunk.level = 2;
            total += chunk.count(2);
            NEARBY.add(chunk);
        }
        NEARBY.sort((a, b) -> Double.compare(a.distance, b.distance));
        for (int level = 1; level >= 0; level--) {
            for (ChunkBlocks chunk : NEARBY) {
                if (chunk.level != level + 1) {
                    break;
                }
                int extra = chunk.count(level) - chunk.count(level + 1);
                if (total + extra > BLOCK_LIGHT_LIMIT) {
                    break;
                }
                total += extra;
                chunk.level = level;
            }
        }

        CANDIDATES.clear();
        beginCells();
        double maxSquared = BLOCK_RANGE * BLOCK_RANGE;
        for (ChunkBlocks chunk : NEARBY) {
            if (chunk.level == 0) {
                for (int i = 0; i < chunk.positions.length; i++) {
                    long pos = chunk.positions[i];
                    Cluster single = pooled();
                    single.block = chunk.blocks[i];
                    single.add(BlockPos.getX(pos) + 0.5d, BlockPos.getY(pos) + 0.5d, BlockPos.getZ(pos) + 0.5d);
                    single.finish();
                    consider(single, camera, maxSquared);
                }
            } else {
                for (Cluster cluster : chunk.level == 1 ? chunk.medium : chunk.coarse) {
                    consider(cluster, camera, maxSquared);
                }
            }
        }
        emitNearest(frame, CANDIDATES, BLOCK_LIGHT_LIMIT, BLOCK_RANGE, camera, partialTick);
    }

    private static void consider(Cluster cluster, Vec3 camera, double maxSquared) {
        double squared = squaredDistance(cluster, camera);
        if (squared <= maxSquared) {
            cluster.sortKey = squared;
            CANDIDATES.add(cluster);
        }
    }

    /** A pooled cluster that is not in the cell map. */
    private static Cluster pooled() {
        if (cellPoolUsed == CELL_POOL.size()) {
            CELL_POOL.add(new Cluster());
        }
        Cluster cluster = CELL_POOL.get(cellPoolUsed++);
        cluster.reset();
        return cluster;
    }

    private static void collectParticles(LightFrame frame, Vec3 camera, float partialTick) {
        beginCells();
        double maxSquared = PARTICLE_RANGE * PARTICLE_RANGE;
        for (Map.Entry<Particle, ParticleType<?>> entry : TRACKED_PARTICLES.entrySet()) {
            ParticlePosition particle = (ParticlePosition) entry.getKey();
            double x = particle.customlights$previousX() + (particle.customlights$x() - particle.customlights$previousX()) * partialTick;
            double y = particle.customlights$previousY() + (particle.customlights$y() - particle.customlights$previousY()) * partialTick;
            double z = particle.customlights$previousZ() + (particle.customlights$z() - particle.customlights$previousZ()) * partialTick;
            double ox = x - camera.x;
            double oy = y - camera.y;
            double oz = z - camera.z;
            if (ox * ox + oy * oy + oz * oz > maxSquared) {
                continue;
            }
            ParticleType<?> type = entry.getValue();
            long key = cellKey(x, y, z, PARTICLE_CELL) * 31L + System.identityHashCode(type);
            Cluster cluster = cell(key);
            cluster.particle = type;
            cluster.add(x, y, z);
        }
        CANDIDATES.clear();
        for (Cluster cluster : CELLS.values()) {
            Rule rule = PARTICLES.get(cluster.particle);
            if (rule == null) {
                continue;
            }
            cluster.rule = rule;
            cluster.finish();
            cluster.sortKey = squaredDistance(cluster, camera);
            CANDIDATES.add(cluster);
        }
        emitNearest(frame, CANDIDATES, PARTICLE_LIGHT_LIMIT, PARTICLE_RANGE, camera, partialTick);
    }

    private static void collectPending(LightFrame frame, Vec3 camera, float partialTick) {
        beginCells();
        double maxSquared = ENTITY_RANGE * ENTITY_RANGE;
        for (int i = 0; i < pendingCount; i++) {
            Pending pending = PENDING.get(i);
            double ox = pending.x - camera.x;
            double oy = pending.y - camera.y;
            double oz = pending.z - camera.z;
            if (ox * ox + oy * oy + oz * oz > maxSquared) {
                continue;
            }
            // Beams and lone entities keep their own light; crowds of the same entity merge
            boolean merge = pendingCount > ENTITY_LIGHT_LIMIT / 2 && pending.rule.kind == Kind.ENTITY;
            long key = (merge ? cellKey(pending.x, pending.y, pending.z, ENTITY_CELL) : pending.owner) * 31L + System.identityHashCode(pending.rule);
            if (!merge && CELLS.containsKey(key)) {
                continue;
            }
            Cluster cluster = cell(key);
            cluster.rule = pending.rule;
            cluster.yaw = pending.yaw;
            cluster.tint = pending.tint;
            cluster.aim = merge ? null : pending.aim;
            cluster.add(pending.x, pending.y, pending.z);
        }
        CANDIDATES.clear();
        for (Cluster cluster : CELLS.values()) {
            cluster.finish();
            cluster.sortKey = squaredDistance(cluster, camera);
            CANDIDATES.add(cluster);
        }
        emitNearest(frame, CANDIDATES, ENTITY_LIGHT_LIMIT, ENTITY_RANGE, camera, partialTick);
    }

    private static void beginCells() {
        CELLS.clear();
        cellPoolUsed = 0;
    }

    private static Cluster cell(long key) {
        Cluster cluster = CELLS.get(key);
        if (cluster == null) {
            if (cellPoolUsed == CELL_POOL.size()) {
                CELL_POOL.add(new Cluster());
            }
            cluster = CELL_POOL.get(cellPoolUsed++);
            cluster.reset();
            CELLS.put(key, cluster);
        }
        return cluster;
    }

    private static long cellKey(double x, double y, double z, double size) {
        return BlockPos.asLong((int) Math.floor(x / size), (int) Math.floor(y / size), (int) Math.floor(z / size));
    }

    private static double squaredDistance(Cluster cluster, Vec3 camera) {
        double dx = cluster.x - camera.x;
        double dy = cluster.y - camera.y;
        double dz = cluster.z - camera.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static void emitNearest(LightFrame frame, List<Cluster> clusters, int limit, double range, Vec3 camera, float partialTick) {
        if (clusters.size() > limit) {
            clusters.sort((a, b) -> Double.compare(a.sortKey, b.sortKey));
        }
        int count = Math.min(limit, clusters.size());
        for (int i = 0; i < count; i++) {
            Cluster cluster = clusters.get(i);
            Rule rule = cluster.rule != null ? cluster.rule : blockRule(cluster.block);
            if (rule == null) {
                continue;
            }
            // Fade out near the end of the range instead of popping
            double distance = Math.sqrt(cluster.sortKey);
            float fade = (float) Math.min(1.0d, (range - distance) / (range * 0.2d));
            if (fade <= 0.0f) {
                continue;
            }
            RenderLight light = emit(frame, rule, cluster.x, cluster.y, cluster.z, cluster.yaw, camera, partialTick);
            if (light == null) {
                continue;
            }
            if (cluster.aim != null && cluster.aim.lengthSqr() > 1.0e-4d) {
                // Beams that point somewhere reach exactly there
                light.setDirectionVector((float) cluster.aim.x, (float) cluster.aim.y, (float) cluster.aim.z);
                if (light.shape.length > 0.0f && light.radius > 0.001f) {
                    light.stretchY = (float) Math.max(0.05d, cluster.aim.length() / (light.radius * light.shape.length));
                }
            }
            if (cluster.tint >= 0) {
                light.red *= ((cluster.tint >> 16) & 0xFF) / 255.0f;
                light.green *= ((cluster.tint >> 8) & 0xFF) / 255.0f;
                light.blue *= (cluster.tint & 0xFF) / 255.0f;
            }
            light.intensity *= fade;
            light.atmosphere *= fade;
            if (cluster.count > 1) {
                // One light standing for several: the light is swept over the box the group fills, so it lights like
                // all of them together, and only a little brighter where they would overlap
                light.intensity *= (float) Math.min(1.5d, 1.0d + 0.12d * Math.sqrt(cluster.count - 1));
                light.extentX = (float) cluster.halfX;
                light.extentY = (float) cluster.halfY;
                light.extentZ = (float) cluster.halfZ;
            }
        }
    }

    /** A rule's light at an object position (the offset turns with {@code yaw}); null when it is off. */
    public static RenderLight emit(LightFrame frame, Rule rule, double x, double y, double z, float yaw, Vec3 camera, float partialTick) {
        Light light = live(rule).light;
        Light.Snapshot snapshot = light.snapshot(partialTick, ANIMATION);
        if (snapshot.intensity <= 0.001f) {
            return null;
        }
        double yawRad = Math.toRadians(-yaw);
        double cos = Math.cos(yawRad);
        double sin = Math.sin(yawRad);
        double ox = snapshot.x * cos - snapshot.z * sin;
        double oz = snapshot.x * sin + snapshot.z * cos;
        RenderLight render = frame.add();
        render.x = (float) (x + ox - camera.x);
        render.y = (float) (y + snapshot.y - camera.y);
        render.z = (float) (z + oz - camera.z);
        render.shape = light.shape;
        render.setDirection(snapshot.yaw + yaw, snapshot.pitch);
        render.setStretch(light.stretchX, light.stretchY, light.stretchZ);
        render.setPivot(light.pivotX, light.pivotY, light.pivotZ);
        render.setSeed(light.seed);
        render.setColor(snapshot.color, snapshot.intensity);
        render.radius = snapshot.radius;
        render.distance = snapshot.distance;
        render.angle = snapshot.angle;
        render.roll = (float) Math.toRadians(snapshot.roll);
        render.atmosphere = snapshot.atmosphere * snapshot.alpha;
        render.luminosity = rule.light.luminosity * snapshot.alpha;
        render.seeThrough = light.seeThrough;
        return render;
    }

    /** Implemented by particles through a mixin: their position this and last tick. */
    public interface ParticlePosition {
        double customlights$x();

        double customlights$y();

        double customlights$z();

        double customlights$previousX();

        double customlights$previousY();

        double customlights$previousZ();
    }
}
