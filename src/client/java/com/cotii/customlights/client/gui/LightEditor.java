package com.cotii.customlights.client.gui;

import com.cotii.customlights.client.command.CommandWriter;
import com.cotii.customlights.client.command.FollowTargets;
import com.cotii.customlights.client.network.ServerEdits;
import com.cotii.customlights.client.command.LightPaste;
import com.cotii.customlights.client.light.Light;
import com.cotii.customlights.client.light.LightAnimation;
import com.cotii.customlights.client.light.LightColors;
import com.cotii.customlights.client.light.LightGroups;
import com.cotii.customlights.client.light.LightManager;
import com.cotii.customlights.client.light.LightShape;
import com.cotii.customlights.client.light.ObjectLights;
import com.cotii.customlights.client.render.LightRenderer;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The CustomLights editor, in three tabs: <ul> <li>Light: one light (static, follow or the flashlight) with a live preview
 * and its paste command;</li> <li>Lights: every light, to edit, copy or delete;</li> <li>Objects: lights for items, blocks,
 * particles, entities and model bones, previewed in 3D in front of the player and saved in {@code objects.json}.</li> </ul>
 * Immediate mode: widgets are rebuilt every frame, so the state that must survive between frames lives here.
 */
public final class LightEditor {
    private static final int KEY_ESCAPE = 256, KEY_ENTER = 257, KEY_BACKSPACE = 259, KEY_KP_ENTER = 335, KEY_V = 86, KEY_A = 65, KEY_S = 83, KEY_Z = 90, KEY_Y = 89, KEY_TAB = 258;
    private static final int MOD_SHIFT = 1;
    private static final int MOD_CONTROL = 2;

    // Purple theme
    private static final int COLOR_PANEL = 0xDC120A1C;
    private static final int COLOR_BAR = 0xEC120A1C;
    private static final int COLOR_TEXT = 0xFFF2EAFF;
    private static final int COLOR_DIM = 0xFFA898C8;
    private static final int COLOR_HEADER = 0xFFC8A0FF;
    /** Shapes that bring their own animation. */
    private static final int COLOR_ANIMATED = 0xFFFFD24A;
    private static final int COLOR_BUTTON = 0xFF2E2240;
    private static final int COLOR_BUTTON_HOVER = 0xFF46355F;
    private static final int COLOR_SELECTED = 0xFF7446BE;
    private static final int COLOR_TRACK = 0xFF1C1428;
    private static final int COLOR_FILL = 0xFF8657D2;
    private static final int COLOR_FILL_HOVER = 0xFFA27AEC;
    private static final int COLOR_FIELD = 0xFF0C0814;
    private static final int COLOR_FIELD_BORDER = 0xFF4A3868;
    private static final int COLOR_FOCUS = 0xFFB888FF;
    private static final int COLOR_LIST = 0xF6180E24;
    private static final int COLOR_COMMAND = 0xFFD8C0FF;
    private static final int COLOR_OK = 0xFFA0FFB0;
    private static final int COLOR_ERROR = 0xFFFF7080;

    private static final String VERSION = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(com.cotii.customlights.Customlights.MOD_ID)
            .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("");
    private static final int PANEL_WIDTH = 300;
    private static final int LEFT = 6;
    private static final int INNER = PANEL_WIDTH - 16;
    private static final int HALF = (INNER - 4) / 2;
    private static final int RIGHT = LEFT + HALF + 4;
    private static final int AREA_TOP = 32;
    private static final int H = 14;
    private static final int ROW = 17;
    private static final int BOTTOM_HEIGHT = 40;
    private static final int RESET = 13;
    private static final int WHEEL_SIZE = 64;
    private static final int WHEEL_CELL = 2;
    private static final int LIST_ROW = 12;
    private static final int PICK_ROW = 16;
    private static final int PICK_ROWS = 6;
    private static final int DRAG_THRESHOLD = 2;
    private static final long DOUBLE_CLICK_MS = 300L;
    private static final int[] SWATCHES = {0xFFFFFF, 0xFFC98A, 0xFF2A2A, 0xFF8A1E, 0xFFF03C, 0x8CFF32, 0x2EE85A, 0x3CF0FF, 0x2A5CFF,
            0x9A3CFF, 0xFF32E0, 0xFF7AC0, 0x202028, 0x000000, LightColors.PARTY};

    private static final java.util.Set<String> SIZE_SLIDERS = java.util.Set.of("radius", "distance", "size_radius", "size_distance");
    private static final IntPredicate ID_CHARS = c -> c < 128 && (Character.isLetterOrDigit(c) || "_-.+:".indexOf(c) >= 0);
    private static final IntPredicate NUMBER_CHARS = c -> Character.isDigit(c) || c == '-' || c == '.';
    private static final IntPredicate COLOR_CHARS = c -> Character.isLetterOrDigit(c) || c == '#';
    private static final IntPredicate TEXT_CHARS = c -> c > 32 && c < 127;

    enum Mode {
        STATIC("Static"),
        FOLLOW("Follow"),
        FLASHLIGHT("Flashlight"),
        OBJECT("Object");

        final String label;

        Mode(String label) {
            this.label = label;
        }
    }

    private enum Page {
        LIGHT("Light"),
        LIGHTS("Saved lights"),
        OBJECTS("Objects");

        final String label;

        Page(String label) {
            this.label = label;
        }
    }

    private static final int DEFAULT_FADE = 10;
    private static final int DEFAULT_COLOR = 0xFF8A1E;

    /** Everything one tab edits; kept between openings so closing the editor never loses work. */
    private static final class Draft {
        Light light;
        Mode mode;
        String editedId;
        LightAnimation animation = new LightAnimation();
        boolean animationsOn;
        final List<int[]> transition = new ArrayList<>();
        boolean transitionOn;
        float sizeRadius = 3.0f, sizeDistance = 3.0f;
        int sizeTime = 40;
        boolean sizeOn;
        boolean stretchOn;
        float hue = 28.0f, saturation = 0.88f, brightness = 1.0f;
        // Objects tab
        ObjectLights.Kind kind = ObjectLights.Kind.ITEM;
        String target = "minecraft:torch";
        int customModelData = -1;
        String search = "";
        int pickScroll;
        /** Items: where the light shows per display context, and the context being edited. */
        java.util.Map<ItemDisplayContext, ObjectLights.Display> displays = ObjectLights.defaultDisplays();
        ItemDisplayContext display = ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;

        void syncHsv() {
            if (light.color == LightColors.PARTY) {
                return;
            }
            float[] hsv = LightColors.toHsv(light.color);
            if (hsv[2] > 0.0f && hsv[1] > 0.0f) {
                hue = hsv[0];
            }
            if (hsv[2] > 0.0f) {
                saturation = hsv[1];
            }
            brightness = hsv[2];
        }

        void setColor(int color) {
            light.color = color;
            syncHsv();
        }

        void setHsv(float newHue, float newSaturation, float newBrightness) {
            hue = ((newHue % 360.0f) + 360.0f) % 360.0f;
            saturation = Math.max(0.0f, Math.min(1.0f, newSaturation));
            brightness = Math.max(0.0f, Math.min(1.0f, newBrightness));
            light.color = LightColors.fromHsv(hue, saturation, brightness);
        }

        /** Takes a light's settings (and animations) for editing. */
        void load(Light source, Mode newMode) {
            light = source.copy(source.id);
            mode = newMode;
            animation = light.animation.copy();
            animationsOn = !animation.isEmpty();
            transitionOn = animation.colors.length >= 2;
            transition.clear();
            for (int i = 0; i < animation.colors.length; i++) {
                transition.add(new int[]{animation.colors[i], animation.colorTime(i)});
            }
            if (transition.size() < 2) {
                resetTransition();
            }
            sizeOn = animation.sizeTime > 0;
            if (sizeOn) {
                sizeRadius = animation.sizeRadius;
                sizeDistance = animation.sizeDistance;
                sizeTime = animation.sizeTime;
            }
            stretchOn = light.isStretched();
            syncHsv();
        }

        void resetTransition() {
            transition.clear();
            transition.add(new int[]{0xFF2A2A, 40});
            transition.add(new int[]{0x2A5CFF, 40});
        }

        /** Writes the animation toggles (and the kind the type stands for) into the light. */
        void sync() {
            if (mode != Mode.OBJECT) {
                light.kind = mode == Mode.FOLLOW ? Light.Kind.FOLLOW : Light.Kind.STATIC;
            }
            if (transitionOn && transition.size() >= 2) {
                int[] colors = new int[transition.size()];
                int[] times = new int[transition.size()];
                for (int i = 0; i < transition.size(); i++) {
                    colors[i] = transition.get(i)[0];
                    times[i] = transition.get(i)[1];
                }
                animation.setTransition(colors, times);
            } else {
                animation.setTransition(new int[0], new int[0]);
            }
            if (sizeOn) {
                animation.sizeRadius = sizeRadius;
                animation.sizeDistance = sizeDistance;
                animation.sizeTime = Math.max(1, sizeTime);
            } else {
                animation.sizeTime = 0;
            }
            if (animationsOn && mode != Mode.FLASHLIGHT) {
                light.animation.read(animation.write());
            } else {
                light.animation.clear();
            }
            if (!stretchOn) {
                light.stretchX = light.stretchY = light.stretchZ = 1.0f;
                light.pivotX = light.pivotY = light.pivotZ = 0.0f;
            }
        }

        /** Everything the draft holds, for undo and redo. */
        String state() {
            JsonObject object = new JsonObject();
            JsonObject saved = light.toJson();
            saved.remove("animation");
            saved.addProperty("x", light.x);
            saved.addProperty("y", light.y);
            saved.addProperty("z", light.z);
            saved.addProperty("target", light.target);
            saved.addProperty("offsetX", light.offsetX);
            saved.addProperty("offsetY", light.offsetY);
            saved.addProperty("offsetZ", light.offsetZ);
            saved.addProperty("stretchX", light.stretchX);
            saved.addProperty("stretchY", light.stretchY);
            saved.addProperty("stretchZ", light.stretchZ);
            saved.addProperty("pivotX", light.pivotX);
            saved.addProperty("pivotY", light.pivotY);
            saved.addProperty("pivotZ", light.pivotZ);
            saved.addProperty("angle", light.angle);
            object.add("light", saved);
            object.addProperty("mode", mode.name());
            if (editedId != null) {
                object.addProperty("editedId", editedId);
            }
            object.add("animation", animation.write());
            object.addProperty("animationsOn", animationsOn);
            JsonArray steps = new JsonArray();
            for (int[] step : transition) {
                steps.add(step[0]);
                steps.add(step[1]);
            }
            object.add("transition", steps);
            object.addProperty("transitionOn", transitionOn);
            object.addProperty("sizeOn", sizeOn);
            object.addProperty("sizeRadius", sizeRadius);
            object.addProperty("sizeDistance", sizeDistance);
            object.addProperty("sizeTime", sizeTime);
            object.addProperty("stretchOn", stretchOn);
            object.addProperty("hue", hue);
            object.addProperty("saturation", saturation);
            object.addProperty("brightness", brightness);
            object.addProperty("kind", kind.id);
            object.addProperty("target", target);
            object.addProperty("customModelData", customModelData);
            JsonObject shown = rule().toJson();
            if (shown.has("displays")) {
                object.add("displays", shown.get("displays"));
            }
            object.addProperty("display", display.name());
            return object.toString();
        }

        void restore(String text) {
            JsonObject object = JsonParser.parseString(text).getAsJsonObject();
            light = Light.fromJson(object.getAsJsonObject("light"));
            mode = Mode.valueOf(object.get("mode").getAsString());
            editedId = object.has("editedId") ? object.get("editedId").getAsString() : null;
            animation = new LightAnimation();
            animation.read(object.getAsJsonObject("animation"));
            animationsOn = object.get("animationsOn").getAsBoolean();
            transition.clear();
            JsonArray steps = object.getAsJsonArray("transition");
            for (int i = 0; i + 1 < steps.size(); i += 2) {
                transition.add(new int[]{steps.get(i).getAsInt(), steps.get(i + 1).getAsInt()});
            }
            transitionOn = object.get("transitionOn").getAsBoolean();
            sizeOn = object.get("sizeOn").getAsBoolean();
            sizeRadius = object.get("sizeRadius").getAsFloat();
            sizeDistance = object.get("sizeDistance").getAsFloat();
            sizeTime = object.get("sizeTime").getAsInt();
            stretchOn = object.get("stretchOn").getAsBoolean();
            hue = object.get("hue").getAsFloat();
            saturation = object.get("saturation").getAsFloat();
            brightness = object.get("brightness").getAsFloat();
            ObjectLights.Kind savedKind = ObjectLights.Kind.byName(object.get("kind").getAsString());
            kind = savedKind == null ? ObjectLights.Kind.ITEM : savedKind;
            target = object.get("target").getAsString();
            customModelData = object.get("customModelData").getAsInt();
            displays = ObjectLights.defaultDisplays();
            if (object.has("displays")) {
                JsonObject wrapper = new JsonObject();
                wrapper.addProperty("kind", "item");
                wrapper.addProperty("target", "minecraft:stick");
                wrapper.add("light", new Light("state").toJson());
                wrapper.add("displays", object.get("displays"));
                displays = ObjectLights.Rule.fromJson(wrapper).displays;
            }
            if (object.has("display")) {
                display = ItemDisplayContext.valueOf(object.get("display").getAsString());
            }
        }

        ObjectLights.Rule rule() {
            String ruleTarget = kind == ObjectLights.Kind.BONE ? target.toLowerCase(Locale.ROOT) : target;
            Light copy = light.copy(ruleTarget.isEmpty() ? "object" : ruleTarget);
            ObjectLights.Rule rule = new ObjectLights.Rule(kind, ruleTarget, kind == ObjectLights.Kind.ITEM ? customModelData : -1, copy);
            displays.forEach((context, shown) -> rule.displays.put(context, shown.copy()));
            if (kind == ObjectLights.Kind.ITEM) {
                // The context being edited keeps the settings on screen
                rule.displays.computeIfAbsent(display, ignored -> new ObjectLights.Display(false, 0.0d, 0.0d, 0.0d)).light = light.copy(copy.id);
            }
            return rule;
        }
    }

    /** Undo and redo of one tab's draft. */
    private static final class History {
        private static final int LIMIT = 100;
        final Deque<String> undo = new ArrayDeque<>();
        final Deque<String> redo = new ArrayDeque<>();
        String current;

        void record(String state) {
            if (current == null) {
                current = state;
            } else if (!state.equals(current)) {
                undo.push(current);
                if (undo.size() > LIMIT) {
                    undo.removeLast();
                }
                current = state;
                redo.clear();
            }
        }
    }

    private static final History LIGHT_HISTORY = new History();
    private static final History OBJECT_HISTORY = new History();

    /** The gizmo moves the light being edited (its position, or its offset from what it follows). */
    private static class LightTarget implements LightGizmo.Target {
        private final Draft draft;
        private final Supplier<Vec3> base;
        private final boolean offset;

        LightTarget(Draft draft, Supplier<Vec3> base, boolean offset) {
            this.draft = draft;
            this.base = base;
            this.offset = offset;
        }

        @Override
        public Vec3 position() {
            Light light = draft.light;
            return base.get().add(offset ? light.offsetX : light.x, offset ? light.offsetY : light.y, offset ? light.offsetZ : light.z);
        }

        @Override
        public void setPosition(Vec3 position) {
            Vec3 local = position.subtract(base.get());
            Light light = draft.light;
            if (offset) {
                light.offsetX = local.x;
                light.offsetY = local.y;
                light.offsetZ = local.z;
            } else {
                light.x = local.x;
                light.y = local.y;
                light.z = local.z;
            }
        }

        @Override
        public boolean directional() {
            return draft.light.shape.directional();
        }

        @Override
        public float yaw() {
            return draft.light.yaw;
        }

        @Override
        public float pitch() {
            return draft.light.pitch;
        }

        @Override
        public void setDirection(float yaw, float pitch) {
            draft.light.yaw = yaw;
            draft.light.pitch = pitch;
        }

        @Override
        public float radius() {
            return draft.light.radius;
        }

        @Override
        public void setRadius(float radius) {
            draft.light.radius = Math.min(512.0f, radius);
        }

        @Override
        public boolean stretchEnabled() {
            return draft.stretchOn;
        }

        @Override
        public float stretch(int axis) {
            Light light = draft.light;
            return axis == 0 ? light.stretchX : axis == 1 ? light.stretchY : light.stretchZ;
        }

        @Override
        public void setStretch(int axis, float value) {
            Light light = draft.light;
            draft.stretchOn = true;
            switch (axis) {
                case 0 -> light.stretchX = value;
                case 1 -> light.stretchY = value;
                default -> light.stretchZ = value;
            }
        }

        @Override
        public float pivot(int axis) {
            Light light = draft.light;
            return axis == 0 ? light.pivotX : axis == 1 ? light.pivotY : light.pivotZ;
        }

        @Override
        public void setPivot(int axis, float value) {
            Light light = draft.light;
            draft.stretchOn = true;
            switch (axis) {
                case 0 -> light.pivotX = value;
                case 1 -> light.pivotY = value;
                default -> light.pivotZ = value;
            }
        }
    }

    /** The gizmo moves an item light's offset (in pixels) for the display context being edited. */
    private static final class ItemTarget extends LightTarget {
        private final Draft item;

        ItemTarget(Draft draft) {
            super(draft, () -> Vec3.ZERO, false);
            item = draft;
        }

        private ObjectLights.Display shown() {
            return item.displays.computeIfAbsent(item.display, ignored -> new ObjectLights.Display(false, 0.0d, 0.0d, 0.0d));
        }

        @Override
        public Vec3 position() {
            ObjectLights.Display shown = shown();
            Light light = item.light;
            return ObjectPreview.itemPoint(0.5d + shown.x / 16.0d + light.x, 0.5d + shown.y / 16.0d + light.y, 0.5d + shown.z / 16.0d + light.z);
        }

        @Override
        public void setPosition(Vec3 position) {
            org.joml.Vector3f local = ObjectPreview.itemLocal(position);
            ObjectLights.Display shown = shown();
            Light light = item.light;
            shown.x = Math.round((local.x - 0.5d - light.x) * 160.0d) / 10.0d;
            shown.y = Math.round((local.y - 0.5d - light.y) * 160.0d) / 10.0d;
            shown.z = Math.round((local.z - 0.5d - light.z) * 160.0d) / 10.0d;
        }
    }

    private static Draft lightDraft;
    private static Draft objectDraft;
    private static Page page = Page.LIGHT;
    private static boolean presetsView;
    private static String presetName = "my_preset";
    private static String cachedKind;
    private static List<String> cachedIds = List.of();

    private final Runnable close;
    private final List<Widget> widgets = new ArrayList<>();
    private final LightGizmo gizmo = new LightGizmo();
    private Light preview;
    private ObjectLights.Rule objectPreview;
    private Vec3 stage;
    private int width = 320, height = 240;
    private double scroll;
    private int contentHeight;

    private String focusedKey;
    private String fieldBuffer = "";
    private Consumer<String> focusedCommit;
    private IntPredicate focusedFilter;

    private Widget pressed;
    private boolean turning;
    private Light draggedLight;
    private double lastMouseX, lastMouseY;
    private String confirmDelete;
    private double pressX, pressY;
    private boolean pressMoved;
    private String lastClickKey;
    private long lastClickTime;

    private OptionList openList;
    private String status = "";
    private int statusColor = COLOR_TEXT;
    private long statusUntil;

    public LightEditor(Runnable close) {
        this.close = close;
        if (lightDraft == null) {
            lightDraft = newLight(Mode.STATIC);
        }
        if (objectDraft == null) {
            objectDraft = newObject();
        }
    }

    private static Draft newLight(Mode mode) {
        Draft draft = new Draft();
        draft.mode = mode;
        Light light = new Light(mode == Mode.FLASHLIGHT ? "flashlight" : freeId());
        (mode == Mode.FLASHLIGHT ? LightShape.FLASHLIGHT : LightShape.SPHERE).applyLook(light);
        // A flashlight is white by default, like a real torch
        light.color = mode == Mode.FLASHLIGHT ? 0xFFFFFF : DEFAULT_COLOR;
        light.fadeIn = DEFAULT_FADE;
        light.fadeOut = DEFAULT_FADE;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            Vec3 point = lookPoint(minecraft);
            light.x = round(point.x);
            light.y = round(point.y);
            light.z = round(point.z);
            light.target = minecraft.player.getGameProfile().getName();
        }
        light.offsetY = 1.0d;
        draft.light = light;
        draft.stretchOn = light.isStretched();
        draft.resetTransition();
        draft.syncHsv();
        return draft;
    }

    private static Draft newObject() {
        Draft draft = new Draft();
        draft.mode = Mode.OBJECT;
        draft.kind = ObjectLights.Kind.ITEM;
        draft.target = "minecraft:torch";
        draft.light = ObjectLights.defaultLight(draft.kind, draft.target);
        draft.resetTransition();
        draft.syncHsv();
        return draft;
    }

    /** Shape looks are scaled down for objects: they are small. */
    private static float objectScale(Draft draft) {
        if (draft.kind == ObjectLights.Kind.SPECIAL && ObjectLights.BEACON_BEAM.equals(draft.target)) {
            return 1.0f;
        }
        return switch (draft.kind) {
            case ITEM, BONE -> 0.25f;
            case PARTICLE -> 0.15f;
            case BLOCK -> 0.45f;
            case ENTITY, SPECIAL -> 0.6f;
        };
    }

    /** Starts a fresh object light of the draft's kind and target. */
    private static void resetObjectLight(Draft draft) {
        ObjectLights.Kind kind = draft.kind;
        String target = draft.target;
        int customModelData = draft.customModelData;
        draft.load(ObjectLights.defaultLight(kind, target), Mode.OBJECT);
        draft.displays = ObjectLights.defaultDisplays();
        draft.kind = kind;
        draft.target = target;
        draft.customModelData = customModelData;
    }

    /** light1, light2... */
    private static String freeId() {
        for (int i = 1; ; i++) {
            if (LightManager.get("light" + i).isEmpty()) {
                return "light" + i;
            }
        }
    }

    /** Opens the editor on an existing light, model light or the flashlight. */
    static void loadForEditing(String id) {
        Light flashlight = LightManager.flashlight();
        if (id.equalsIgnoreCase("flashlight") && flashlight != null) {
            editLight(flashlight, Mode.FLASHLIGHT);
            return;
        }
        LightManager.get(id).ifPresentOrElse(light -> editLight(light, light.isFollow() ? Mode.FOLLOW : Mode.STATIC), () -> {
            ObjectLights.Rule rule = ObjectLights.rule(ObjectLights.keyOf(ObjectLights.Kind.BONE, id, -1));
            if (rule != null) {
                editObject(rule);
            }
        });
    }

    private static void editLight(Light light, Mode mode) {
        if (lightDraft == null) {
            lightDraft = new Draft();
        }
        lightDraft.load(light, mode);
        lightDraft.editedId = mode == Mode.FLASHLIGHT ? null : light.id;
        page = Page.LIGHT;
    }

    /** Opens the editor on the Objects tab (development test). */
    public static void showObjects(String kind, String target) {
        page = Page.OBJECTS;
        if (objectDraft != null) {
            objectDraft.kind = ObjectLights.Kind.byName(kind);
            objectDraft.target = target;
            ObjectLights.Rule saved = ObjectLights.rule(ObjectLights.keyOf(objectDraft.kind, target, -1));
            if (saved != null) {
                editObject(saved);
            }
        }
    }

    /** Opens the editor on a new follow light (development test). */
    public static void showFollow(String target) {
        page = Page.LIGHT;
        lightDraft = newLight(Mode.FOLLOW);
        lightDraft.light.target = target;
    }

    /** Saves every object light as a preset and opens the presets view (development test). */
    public static void showPresets(String name) {
        page = Page.OBJECTS;
        presetsView = true;
        presetName = name;
        ObjectPresets.saveAll(name);
    }

    /** Opens the saved lights tab with the history open (development test). */
    public static void showSavedLights() {
        page = Page.LIGHTS;
        historyOpen = true;
    }

    /** Leaves the presets view (development test). */
    public static void hidePresets() {
        presetsView = false;
    }

    /** The paste command of the light being edited (development test). */
    public static String draftCommand() {
        lightDraft.sync();
        return LightPaste.command(lightDraft.light.copy(lightDraft.light.id), type(lightDraft.mode));
    }

    private static void editObject(ObjectLights.Rule rule) {
        if (objectDraft == null) {
            objectDraft = newObject();
        }
        objectDraft.load(rule.light, Mode.OBJECT);
        objectDraft.kind = rule.kind;
        objectDraft.target = rule.target;
        objectDraft.customModelData = rule.customModelData;
        objectDraft.displays = new java.util.EnumMap<>(ItemDisplayContext.class);
        rule.displays.forEach((context, shown) -> objectDraft.displays.put(context, shown.copy()));
        if (rule.kind == ObjectLights.Kind.ITEM) {
            objectDraft.load(rule.displayLight(objectDraft.display), Mode.OBJECT);
        }
        page = Page.OBJECTS;
    }

    // ── Lifecycle
    // ──────────────────────────────────────────────────

    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
        openList = null;
    }

    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        // Ctrl is for shortcuts here (Ctrl+S would also walk backwards)
        EditorMovement.update(minecraft, focusedKey != null || (pressed != null && pressMoved) || gizmo.dragging() || controlDown());
        lightDraft.sync();
        objectDraft.sync();
        if (pressed == null && !gizmo.dragging() && focusedKey == null) {
            LIGHT_HISTORY.record(lightDraft.state());
            OBJECT_HISTORY.record(objectDraft.state());
        }
        if (minecraft.player == null) {
            return;
        }
        if (page == Page.LIGHT) {
            updateLightPreview(minecraft);
            shareLive();
        } else {
            LightManager.setPreview(null, null);
            preview = null;
        }
        if (page == Page.OBJECTS) {
            updateObjectPreview(minecraft);
        } else {
            stopObjectPreview();
        }
    }

    public void onClosed() {
        commitField();
        LightManager.setPreview(null, null);
        stopObjectPreview();
        // Keys released while the editor was open never reached the game
        EditorMovement.update(Minecraft.getInstance(), false);
    }

    /** Keeps the preview light in step with the draft without restarting its animations. */
    private void updateLightPreview(Minecraft minecraft) {
        Draft draft = lightDraft;
        int age = preview == null ? draft.light.fadeIn : preview.age;
        Light next = draft.light.copy(draft.light.id);
        next.age = Math.max(age, draft.light.fadeIn);
        next.kind = draft.mode == Mode.FOLLOW ? Light.Kind.FOLLOW : Light.Kind.STATIC;
        next.dimension = null;
        String replaces;
        if (draft.mode == Mode.FLASHLIGHT) {
            Vec3 eye = minecraft.player.getEyePosition();
            next.x = eye.x;
            next.y = eye.y - 0.2d;
            next.z = eye.z;
            next.yaw = minecraft.player.getYRot();
            next.pitch = minecraft.player.getXRot();
            replaces = "flashlight";
        } else {
            replaces = draft.editedId != null ? draft.editedId : draft.light.id;
        }
        preview = next;
        LightManager.setPreview(preview, replaces);
    }

    private void updateObjectPreview(Minecraft minecraft) {
        if (stage == null) {
            placeStage(minecraft);
        }
        if (objectDraft.target.isEmpty()) {
            stopObjectPreview();
            return;
        }
        ObjectLights.Rule rule = objectDraft.rule();
        boolean same = objectPreview != null && objectPreview.key().equals(rule.key());
        // Same object: keep the running animations (and never fade in again)
        rule.light.age = same ? Math.max(objectPreview.light.age, rule.light.fadeIn) : rule.light.fadeIn;
        for (var entry : rule.displays.entrySet()) {
            Light shown = entry.getValue().light;
            if (shown == null) {
                continue;
            }
            ObjectLights.Display before = same ? objectPreview.displays.get(entry.getKey()) : null;
            int age = before != null && before.light != null ? before.light.age : rule.light.age;
            shown.age = Math.max(age, shown.fadeIn);
        }
        objectPreview = rule;
        ObjectLights.setPreview(rule);
        ObjectPreview.itemContext = objectDraft.display;
        ObjectPreview.show(rule, stage);
    }

    private void stopObjectPreview() {
        if (objectPreview != null) {
            objectPreview = null;
            ObjectLights.setPreview(null);
            ObjectPreview.hide();
        }
        if (page != Page.OBJECTS) {
            stage = null;
        }
    }

    private void placeStage(Minecraft minecraft) {
        Vec3 look = minecraft.player.getViewVector(1.0f);
        Vec3 flat = new Vec3(look.x, 0.0d, look.z);
        flat = flat.lengthSqr() < 1.0e-4d ? new Vec3(0.0d, 0.0d, 1.0d) : flat.normalize();
        Vec3 eye = minecraft.player.getEyePosition();
        stage = new Vec3(eye.x + flat.x * 2.5d, eye.y - 0.3d, eye.z + flat.z * 2.5d);
    }

    // ── Save and copy
    // ──────────────────────────────────────────────

    private static String type(Mode mode) {
        return switch (mode) {
            case STATIC -> "static";
            case FOLLOW -> "follow";
            case FLASHLIGHT -> "flashlight";
            case OBJECT -> "model";
        };
    }

    private String command() {
        Draft draft = lightDraft;
        return serverCommand(LightPaste.command(draft.light.copy(draft.light.id), type(draft.mode)));
    }

    /** While "Set to everyone" is on, commands are the server plugin's (for every player). */
    private static String serverCommand(String command) {
        if (ServerEdits.everyone() && command.startsWith(LightPaste.COMMAND)) {
            return "/lightplugin paste @a " + command.substring(LightPaste.COMMAND.length());
        }
        return command;
    }

    /** "Set to everyone" toggle, shown on servers with the CustomLights plugin when this player may edit them. */
    private int everyoneRow(int y) {
        if (!ServerEdits.canEdit()) {
            return y;
        }
        boolean on = ServerEdits.everyone();
        Button toggle = new Button("everyone", LEFT, y, INNER, H, "Set to everyone: " + (on ? "On" : "Off"), on,
                () -> ServerEdits.setEveryone(!ServerEdits.everyone()));
        toggle.tooltip = on ? "Save sends it to every player on the server (players who join later get it too)"
                : "Save keeps it on your client only";
        widgets.add(toggle);
        return y + ROW + 2;
    }

    private void sharedStatus(String what) {
        if (ServerEdits.lastTooLarge()) {
            setStatus("Too big to send to the server", COLOR_ERROR);
        } else {
            setStatus(what, COLOR_OK);
        }
    }

    private static String lastShared;
    private static int shareCooldown;

    /** While sharing, a light the server already has follows the editor live (a few times a second). */
    private void shareLive() {
        Draft draft = lightDraft;
        if (!ServerEdits.everyone() || draft.mode == Mode.FLASHLIGHT || --shareCooldown > 0) {
            return;
        }
        String id = draft.editedId != null ? draft.editedId : draft.light.id;
        if (!id.equalsIgnoreCase(draft.light.id) || LightManager.serverLight(id) == null) {
            return;
        }
        if (draft.mode == Mode.FOLLOW && draft.light.target.isBlank()) {
            return;
        }
        JsonObject json = sharedJson(draft);
        String text = json.toString();
        if (!text.equals(lastShared)) {
            lastShared = text;
            shareCooldown = 4;
            ServerEdits.putLight(json, type(draft.mode));
        }
    }

    private static JsonObject sharedJson(Draft draft) {
        Light light = draft.light.copy(draft.light.id);
        light.server = false;
        if (draft.mode == Mode.STATIC) {
            light.dimension = LightManager.currentDimension();
        }
        return light.toJson();
    }

    private void copyCommand() {
        commitField();
        lightDraft.sync();
        String command = command();
        Minecraft.getInstance().keyboardHandler.setClipboard(command);
        setStatus(command.length() > 256 ? (ServerEdits.everyone() ? "Copied (too long for chat: use a command block)" : "Copied (run /customlight paste to paste it)") : "Command copied", COLOR_OK);
    }

    private void saveLight() {
        commitField();
        Draft draft = lightDraft;
        draft.sync();
        Light light = draft.light.copy(draft.light.id);
        if (draft.mode == Mode.FOLLOW && light.target.isBlank()) {
            setStatus("Choose a target first", COLOR_ERROR);
            return;
        }
        if (draft.mode == Mode.FLASHLIGHT) {
            // A flashlight is a follow light with the flashlight shape, saved with its own ID like any other
            light.kind = Light.Kind.FOLLOW;
            light.shape = LightShape.FLASHLIGHT;
            if (light.target == null || light.target.isBlank()) {
                light.target = LightManager.SELF_TARGET;
            }
            if (ServerEdits.everyone()) {
                JsonObject json = light.toJson();
                json.remove("server");
                ServerEdits.putLight(json, "follow");
                sharedStatus("Sent " + light.id + " to everyone");
                return;
            }
            LightPaste.apply(light, "follow");
            setStatus("Saved flashlight " + light.id, COLOR_OK);
            return;
        }
        if (ServerEdits.everyone()) {
            JsonObject json = sharedJson(draft);
            ServerEdits.putLight(json, type(draft.mode));
            lastShared = json.toString();
            // Your own copy steps aside, so you see the one everyone sees
            LightManager.get(light.id).filter(own -> !own.server).ifPresent(own -> LightManager.remove(own.id, 0));
            if (draft.mode != Mode.FLASHLIGHT) {
                draft.editedId = light.id;
            }
            sharedStatus("Sent " + (draft.mode == Mode.FLASHLIGHT ? "the flashlight" : light.id) + " to everyone");
            return;
        }
        // A new ID makes a copy: the light that was being edited stays as it is
        boolean copy = draft.mode != Mode.FLASHLIGHT && draft.editedId != null && !draft.editedId.equalsIgnoreCase(light.id);
        Light before = draft.mode == Mode.FLASHLIGHT ? LightManager.flashlight() : LightManager.get(light.id).orElse(null);
        if (before != null && (before.removing || before.server)) {
            before = null;
        }
        if (before == null) {
            EditHistory.light(EditHistory.Action.ADDED, type(draft.mode), light);
        } else {
            EditHistory.light(EditHistory.Action.UPDATED, type(draft.mode), before);
        }
        LightPaste.apply(light, type(draft.mode));
        String previous = draft.editedId;
        if (draft.mode != Mode.FLASHLIGHT) {
            draft.editedId = light.id;
        }
        if (copy) {
            setStatus("Copied " + previous + " as " + light.id, COLOR_OK);
        } else {
            setStatus("Saved " + (draft.mode == Mode.FLASHLIGHT ? "flashlight" : light.id), COLOR_OK);
        }
    }

    private void saveObject() {
        commitField();
        Draft draft = objectDraft;
        draft.sync();
        if (draft.target.isBlank()) {
            setStatus("Choose an object first", COLOR_ERROR);
            return;
        }
        ObjectLights.Rule rule = draft.rule();
        if (ServerEdits.everyone()) {
            ServerEdits.putObject(rule);
            if (ObjectLights.rule(rule.key()) != null && ObjectLights.remove(rule.key(), 0)) {
                ObjectLights.saveIfDirty();
            }
            sharedStatus("Sent " + shortId(rule.label()) + " to everyone");
            return;
        }
        rule.light.age = rule.light.fadeIn;
        ObjectLights.Rule before = ObjectLights.rule(rule.key());
        if (before != null && before.light.removing) {
            before = null;
        }
        EditHistory.object(before == null ? EditHistory.Action.ADDED : EditHistory.Action.UPDATED, shortId(rule.label()), before == null ? rule : before);
        ObjectLights.put(rule);
        ObjectLights.saveIfDirty();
        setStatus("Saved " + rule.label(), COLOR_OK);
    }

    private void deleteObject() {
        ObjectLights.Rule rule = objectDraft.rule();
        if (ServerEdits.everyone() && ObjectLights.serverRule(rule.key()) != null) {
            ServerEdits.removeObject(rule.key());
            setStatus("Removed " + shortId(rule.label()) + " for everyone", COLOR_OK);
            return;
        }
        if (!deleteRule(ObjectLights.rule(rule.key()))) {
            setStatus("Not saved yet", COLOR_ERROR);
        }
    }

    private boolean deleteRule(ObjectLights.Rule rule) {
        if (rule == null || rule.light.removing) {
            return false;
        }
        EditHistory.object(EditHistory.Action.DELETED, shortId(rule.label()), rule);
        ObjectLights.remove(rule.key(), null);
        ObjectLights.saveIfDirty();
        setStatus("Deleted " + shortId(rule.label()), COLOR_OK);
        return true;
    }

    private void deleteLight(Light light, String type) {
        EditHistory.light(EditHistory.Action.DELETED, type, light);
        if (type.equals("flashlight")) {
            LightManager.turnOffFlashlight();
        } else if (light.server && ServerEdits.canEdit()) {
            ServerEdits.removeLight(light.id, null);
        } else if (light.server) {
            Minecraft.getInstance().player.connection.sendCommand("customlight remove " + light.id);
        } else {
            LightManager.remove(light.id, null);
        }
        setStatus("Deleted " + (type.equals("flashlight") ? "flashlight" : light.id), COLOR_OK);
    }

    /** Ctrl+Z / Ctrl+Y on the open tab. */
    private void undo(boolean redo) {
        commitField();
        if (page == Page.LIGHTS) {
            return;
        }
        Draft draft = page == Page.LIGHT ? lightDraft : objectDraft;
        History history = page == Page.LIGHT ? LIGHT_HISTORY : OBJECT_HISTORY;
        draft.sync();
        history.record(draft.state());
        Deque<String> from = redo ? history.redo : history.undo;
        Deque<String> to = redo ? history.undo : history.redo;
        if (from.isEmpty()) {
            setStatus(redo ? "Nothing to redo" : "Nothing to undo", COLOR_DIM);
            return;
        }
        to.push(history.current);
        history.current = from.pop();
        draft.restore(history.current);
        setStatus(redo ? "Redo" : "Undo", COLOR_OK);
    }

    private static boolean controlDown() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    /** What the gizmo moves on the open tab, or null. */
    private LightGizmo.Target gizmoTarget() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return null;
        }
        if (page == Page.OBJECTS) {
            Vec3 base = stage;
            if (base == null || objectDraft.target.isEmpty()) {
                return null;
            }
            if (objectDraft.kind == ObjectLights.Kind.ITEM) {
                // Item lights move their offset (in pixels) for the display context being edited
                return ObjectPreview.hasItemPose() ? new ItemTarget(objectDraft) : null;
            }
            return new LightTarget(objectDraft, () -> base, false);
        }
        if (page != Page.LIGHT) {
            return null;
        }
        Draft draft = lightDraft;
        if (draft.mode == Mode.STATIC) {
            return new LightTarget(draft, () -> Vec3.ZERO, false);
        }
        if (draft.mode == Mode.FOLLOW) {
            List<net.minecraft.world.entity.Entity> targets = LightManager.targets(minecraft, draft.light);
            if (targets.isEmpty()) {
                return null;
            }
            net.minecraft.world.entity.Entity entity = targets.get(0);
            float partialTick = com.cotii.customlights.client.compat.Compat.partialTick(minecraft);
            return new LightTarget(draft, () -> entity.getPosition(partialTick), true);
        }
        return null;
    }

    /** Light settings copied with Copy props: everything but where the light is and what it is called. */
    private static JsonObject copiedProperties;
    private static final java.util.Set<String> PLACE_KEYS = java.util.Set.of("id", "kind", "dimension", "x", "y", "z", "target", "offsetX", "offsetY",
            "offsetZ", "group", "visibleFirstPerson", "targetIds");
    private static final java.util.Set<String> LOOK_KEYS = java.util.Set.of("animation", "stretchX", "stretchY", "stretchZ", "pivotX", "pivotY", "pivotZ", "angle", "cameraDelay");

    private Draft currentDraft() {
        return page == Page.OBJECTS ? objectDraft : page == Page.LIGHT ? lightDraft : null;
    }

    private void copyProperties() {
        commitField();
        Draft draft = currentDraft();
        if (draft == null) {
            return;
        }
        draft.sync();
        JsonObject properties = draft.light.toJson();
        PLACE_KEYS.forEach(properties::remove);
        copiedProperties = properties;
        setStatus("Copied the light properties (shape, color, size, animations...)", COLOR_OK);
    }

    private void pasteProperties() {
        commitField();
        Draft draft = currentDraft();
        if (draft == null) {
            return;
        }
        if (copiedProperties == null) {
            setStatus("Copy the properties of a light first", COLOR_ERROR);
            return;
        }
        draft.sync();
        JsonObject merged = draft.light.toJson();
        LOOK_KEYS.forEach(merged::remove);
        copiedProperties.entrySet().forEach(entry -> merged.add(entry.getKey(), entry.getValue().deepCopy()));
        Light pasted = Light.fromJson(merged);
        pasted.age = draft.light.age;
        draft.load(pasted, draft.mode);
        setStatus("Pasted the light properties", COLOR_OK);
    }

    private void setStatus(String text, int color) {
        status = text;
        statusColor = color;
        statusUntil = System.currentTimeMillis() + 2500L;
    }

    // ── Rendering
    // ──────────────────────────────────────────────────

    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        widgets.clear();
        int areaBottom = height - BOTTOM_HEIGHT;
        gizmo.setTarget(gizmoTarget());
        gizmo.render(graphics, width, height, mouseX, mouseY, openList == null && pressed == null && mouseX >= PANEL_WIDTH && mouseY < areaBottom);
        graphics.fill(0, 0, PANEL_WIDTH, areaBottom, COLOR_PANEL);
        graphics.drawString(font, "CustomLights " + VERSION, LEFT, 5, COLOR_HEADER, true);

        Page[] pages = Page.values();
        int tabWidth = INNER / pages.length;
        for (int i = 0; i < pages.length; i++) {
            Page each = pages[i];
            fixed(new Button("tab_" + each.name(), LEFT + i * tabWidth, 16, tabWidth - (i < pages.length - 1 ? 2 : 0), H, each.label, page == each, () -> {
                commitField();
                page = each;
                scroll = 0.0d;
            }));
        }

        int start = AREA_TOP + 2 - (int) scroll;
        int end = switch (page) {
            case LIGHT -> layoutLight(start);
            case LIGHTS -> layoutLights(start);
            case OBJECTS -> layoutObjects(start);
        };
        layoutHistory(font);
        contentHeight = end - start + 8;
        int visible = areaBottom - AREA_TOP;
        double maxScroll = Math.max(0, contentHeight - visible);
        if (scroll > maxScroll) {
            scroll = maxScroll;
        }

        boolean overlay = openList != null;
        graphics.enableScissor(0, AREA_TOP, PANEL_WIDTH, areaBottom);
        for (Widget widget : widgets) {
            if (widget.scrolls && widget.y + widget.h >= AREA_TOP && widget.y < areaBottom) {
                widget.render(graphics, font, !overlay && isHovered(widget, mouseX, mouseY));
            }
        }
        graphics.disableScissor();
        if (contentHeight > visible) {
            int barHeight = Math.max(12, visible * visible / contentHeight);
            int barY = AREA_TOP + (int) ((visible - barHeight) * (scroll / maxScroll));
            graphics.fill(PANEL_WIDTH - 4, AREA_TOP, PANEL_WIDTH - 2, areaBottom, 0x30FFFFFF);
            graphics.fill(PANEL_WIDTH - 4, barY, PANEL_WIDTH - 2, barY + barHeight, 0xC0B080FF);
        }
        renderBottom(graphics, font);
        for (Widget widget : widgets) {
            if (!widget.scrolls) {
                widget.render(graphics, font, !overlay && widget.contains(mouseX, mouseY));
            }
        }
        if (draggedLight != null) {
            // The light being dragged, and the group it would join
            for (Widget widget : widgets) {
                if (widget.dropGroup != null && mouseY >= widget.dropY && mouseY < widget.dropY + ROW && mouseX < PANEL_WIDTH) {
                    graphics.fill(LEFT - 2, widget.dropY - 1, LEFT + INNER + 2, widget.dropY + ROW - 1, 0x407446BE);
                    break;
                }
            }
            String text = draggedLight.id;
            graphics.fill(mouseX + 6, mouseY - 2, mouseX + 12 + font.width(text), mouseY + 10, 0xE0180E24);
            graphics.drawString(font, text, mouseX + 9, mouseY, COLOR_TEXT, true);
        }
        if (!overlay) {
            renderTooltip(graphics, font, mouseX, mouseY);
        }
        if (!status.isEmpty() && System.currentTimeMillis() < statusUntil) {
            int textWidth = font.width(status);
            int x = PANEL_WIDTH + 8;
            graphics.fill(x - 4, areaBottom - 16, x + textWidth + 4, areaBottom - 3, 0xC0000000);
            graphics.drawString(font, status, x, areaBottom - 13, statusColor, true);
        }
        if (openList != null) {
            com.cotii.customlights.client.compat.GuiCanvas.raise(graphics, 400.0f);
            openList.render(graphics, font, mouseX, mouseY);
            com.cotii.customlights.client.compat.GuiCanvas.lower(graphics);
        }
    }

    /** A short description above the mouse for widgets that have one. */
    private void renderTooltip(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        for (Widget widget : widgets) {
            if (widget.tooltip == null || !(widget.scrolls ? isHovered(widget, mouseX, mouseY) : widget.contains(mouseX, mouseY))) {
                continue;
            }
            int textWidth = font.width(widget.tooltip);
            int x = Math.max(2, Math.min(mouseX - textWidth / 2, width - textWidth - 6));
            int y = mouseY - 16;
            com.cotii.customlights.client.compat.GuiCanvas.raise(graphics, 300.0f);
            graphics.fill(x - 3, y - 2, x + textWidth + 3, y + 10, 0xF0180E24);
            graphics.fill(x - 3, y - 2, x + textWidth + 3, y - 1, COLOR_FILL);
            graphics.drawString(font, widget.tooltip, x, y, COLOR_TEXT, true);
            com.cotii.customlights.client.compat.GuiCanvas.lower(graphics);
            return;
        }
    }

    /** Whether the mouse is over the history list on the right. */
    private boolean overHistory(double mouseX, double mouseY) {
        int listWidth = Math.min(HISTORY_WIDTH, width - PANEL_WIDTH - 12);
        int rows = historyOpen ? Math.max(1, EditHistory.entries().size()) : 0;
        return mouseX >= width - listWidth - 6 && mouseY <= 4 + H + 4 + rows * HISTORY_ROW;
    }

    private boolean isHovered(Widget widget, double mouseX, double mouseY) {
        return widget.contains(mouseX, mouseY) && mouseY >= AREA_TOP && mouseY < height - BOTTOM_HEIGHT;
    }

    private void renderBottom(GuiGraphics graphics, Font font) {
        int top = height - BOTTOM_HEIGHT;
        graphics.fill(0, top, width, height, COLOR_BAR);
        String line = switch (page) {
            case LIGHT -> command();
            case LIGHTS -> "Edit, copy or delete your lights";
            case OBJECTS -> "Object lights are saved in config/customlights/objects.json";
        };
        int maxWidth = width - 12;
        String shown = font.width(line) > maxWidth ? font.plainSubstrByWidth(line, maxWidth - font.width("...")) + "..." : line;
        graphics.drawString(font, shown, LEFT, top + 5, page == Page.LIGHT ? COLOR_COMMAND : COLOR_DIM, true);
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        switch (page) {
            case LIGHT -> {
                labels.add("Save");
                actions.add(this::saveLight);
                labels.add("Copy command");
                actions.add(this::copyCommand);
                labels.add("Copy props");
                actions.add(this::copyProperties);
                labels.add("Paste props");
                actions.add(this::pasteProperties);
                labels.add("New");
                actions.add(() -> {
                    lightDraft = newLight(lightDraft.mode == Mode.FLASHLIGHT ? Mode.STATIC : lightDraft.mode);
                    scroll = 0.0d;
                });
            }
            case LIGHTS -> {
                labels.add("New light");
                actions.add(() -> {
                    lightDraft = newLight(Mode.STATIC);
                    page = Page.LIGHT;
                    scroll = 0.0d;
                });
            }
            case OBJECTS -> {
                labels.add("Save");
                actions.add(this::saveObject);
                labels.add("Delete");
                actions.add(this::deleteObject);
                labels.add("Copy props");
                actions.add(this::copyProperties);
                labels.add("Paste props");
                actions.add(this::pasteProperties);
                labels.add("New");
                actions.add(() -> {
                    objectDraft = newObject();
                    scroll = 0.0d;
                });
            }
        }
        labels.add("Close");
        actions.add(close);
        int y = top + 20;
        int buttonWidth = (width - 12 - 4 * (labels.size() - 1)) / labels.size();
        for (int i = 0; i < labels.size(); i++) {
            fixed(new Button("bottom_" + labels.get(i), LEFT + i * (buttonWidth + 4), y, buttonWidth, H, labels.get(i), false, actions.get(i)));
        }
    }

    // ── Light tab
    // ──────────────────────────────────────────────────

    private int layoutLight(int y) {
        Minecraft minecraft = Minecraft.getInstance();
        Draft draft = lightDraft;
        Light light = draft.light;
        y = everyoneRow(y);

        final int typeY = y;
        widgets.add(new Button("type", LEFT, y, HALF, H, "Type: " + draft.mode.label + " ▼", false, () -> openList = new OptionList(LEFT, typeY + H, HALF,
                List.of(Mode.STATIC.label, Mode.FOLLOW.label, Mode.FLASHLIGHT.label), draft.mode.ordinal(), index -> {
            Mode chosen = Mode.values()[index];
            if (chosen == Mode.FLASHLIGHT && draft.mode != Mode.FLASHLIGHT) {
                Light flashlight = LightManager.flashlight();
                if (flashlight != null && !flashlight.removing) {
                    editLight(flashlight, Mode.FLASHLIGHT);
                } else {
                    lightDraft = newLight(Mode.FLASHLIGHT);
                }
                return;
            }
            if (chosen == Mode.FOLLOW && light.target.isBlank() && minecraft.player != null) {
                light.target = minecraft.player.getGameProfile().getName();
            }
            if (chosen == Mode.FOLLOW && draft.mode != Mode.FOLLOW && (draft.editedId == null || light.shape == LightShape.FLASHLIGHT)) {
                // Follow lights start as a glow around what they follow
                LightShape.SPHERE.applyLook(light);
                draft.stretchOn = false;
            }
            draft.mode = chosen;
        })));
        if (draft.mode == Mode.FLASHLIGHT) {
            // "flashlight" is the one flashlight; any other ID saves a flashlight of its own (as many as wanted)
            textField("id", RIGHT, y, HALF, "ID ", () -> light.id, text -> {
                if (!text.isEmpty() && !text.equalsIgnoreCase("all")) {
                    light.setId(text);
                }
            }, ID_CHARS);
        } else {
            textField("id", RIGHT, y, HALF, "ID ", () -> light.id, text -> {
                if (!text.isEmpty() && !text.equalsIgnoreCase("all") && !text.equalsIgnoreCase("flashlight")) {
                    light.setId(text);
                }
            }, ID_CHARS);
        }
        y += ROW + 2;
        if (draft.mode != Mode.FLASHLIGHT && draft.editedId != null && !draft.editedId.equalsIgnoreCase(light.id)) {
            widgets.add(new Label(LEFT, y, "New ID: saving makes a copy of " + draft.editedId, COLOR_DIM));
            y += 12;
        }

        switch (draft.mode) {
            case STATIC -> {
                header(LEFT, y + 3, "Position");
                int buttonWidth = (INNER - 64) / 2;
                button("player_position", LEFT + 64, y, buttonWidth - 2, "Player position", false, () -> {
                    Vec3 position = minecraft.player.position();
                    light.x = round(position.x);
                    light.y = round(position.y + 1.0d);
                    light.z = round(position.z);
                });
                button("player_view", LEFT + 64 + buttonWidth, y, buttonWidth, "Player view", false, () -> {
                    Vec3 point = lookPoint(minecraft);
                    light.x = round(point.x);
                    light.y = round(point.y);
                    light.z = round(point.z);
                });
                y += ROW;
                y = coordinates(y, "pos", () -> light.x, v -> light.x = v, () -> light.y, v -> light.y = v, () -> light.z, v -> light.z = v);
            }
            case FOLLOW -> {
                header(LEFT, y + 3, "Target");
                y += ROW - 4;
                final int targetY = y;
                textField("target", LEFT, y, INNER - 18, "", () -> light.target, text -> light.target = text, TEXT_CHARS);
                widgets.add(new Button("target_list", LEFT + INNER - 16, y, 16, H, "▼", false, () -> {
                    List<FollowTargets.Target> targets = FollowTargets.all(minecraft);
                    List<String> labels = new ArrayList<>();
                    for (FollowTargets.Target target : targets) {
                        labels.add(target.label());
                    }
                    labels.add("tag:<tag>");
                    openList = new OptionList(LEFT, targetY + H, INNER, labels, -1, index -> {
                        if (index < targets.size()) {
                            light.target = targets.get(index).value();
                        } else {
                            focus("target", "tag:", text -> light.target = text, TEXT_CHARS);
                        }
                    });
                }));
                y += ROW;
                widgets.add(new Label(LEFT, y, followInfo(minecraft, light), COLOR_DIM));
                y += 12;
                button("first_person", LEFT, y, INNER, "First person: " + (light.visibleFirstPerson ? "visible" : "hidden"),
                        !light.visibleFirstPerson, () -> light.visibleFirstPerson = !light.visibleFirstPerson);
                y += ROW;
                header(LEFT, y + 3, "Offset");
                y += ROW - 4;
                y = coordinates(y, "offset", () -> light.offsetX, v -> light.offsetX = v, () -> light.offsetY, v -> light.offsetY = v,
                        () -> light.offsetZ, v -> light.offsetZ = v);
            }
            case FLASHLIGHT -> {
                // Who holds it: you (@self) or, like a follow light, a player, an entity or tag:<tag>
                header(LEFT, y + 3, "Target");
                y += ROW - 4;
                final int holderY = y;
                textField("holder", LEFT, y, INNER - 18, "", () -> light.target == null || light.target.isEmpty() ? LightManager.SELF_TARGET : light.target,
                        text -> light.target = text.isBlank() ? LightManager.SELF_TARGET : text, TEXT_CHARS);
                widgets.add(new Button("holder_list", LEFT + INNER - 16, y, 16, H, "▼", false, () -> {
                    List<FollowTargets.Target> targets = FollowTargets.all(minecraft);
                    List<String> labels = new ArrayList<>();
                    labels.add("You (@self)");
                    for (FollowTargets.Target target : targets) {
                        labels.add(target.label());
                    }
                    labels.add("tag:<tag>");
                    openList = new OptionList(LEFT, holderY + H, INNER, labels, -1, index -> {
                        if (index == 0) {
                            light.target = LightManager.SELF_TARGET;
                        } else if (index <= targets.size()) {
                            light.target = targets.get(index - 1).value();
                        } else {
                            focus("holder", "tag:", text -> light.target = text, TEXT_CHARS);
                        }
                    });
                }));
                y += ROW;
                boolean self = light.target == null || light.target.isEmpty() || light.target.equalsIgnoreCase(LightManager.SELF_TARGET)
                        || light.target.equalsIgnoreCase("@s");
                widgets.add(new Label(LEFT, y, self ? "From your eyes, where you look" : followInfo(minecraft, light), COLOR_DIM));
                y += 12;
                y = slider(y, LEFT, INNER, "delay", "Camera delay", "t", 0, 5, 0, 5, 0.1, () -> light.cameraDelay,
                        v -> light.cameraDelay = (float) v, 0);
            }
            default -> {
            }
        }
        y += 4;
        y = lightSettings(y, draft);
        if (draft.mode != Mode.FLASHLIGHT) {
            y = animationSection(y, draft);
        }
        return y;
    }

    /** Shape, stretch, direction, light, fades and color, shared by the Light and Objects tabs. */
    private int lightSettings(int y, Draft draft) {
        Minecraft minecraft = Minecraft.getInstance();
        Light light = draft.light;
        if (draft.mode != Mode.FLASHLIGHT) {
            // Follow lights cannot be flashlights (the flashlight is its own type)
            LightShape[] shapes = java.util.Arrays.stream(LightShape.values())
                    .filter(shape -> draft.mode != Mode.FOLLOW || shape != LightShape.FLASHLIGHT).toArray(LightShape[]::new);
            List<String> names = new ArrayList<>();
            for (LightShape shape : shapes) {
                names.add(shape.displayName());
            }
            final int shapeY = y;
            widgets.add(new Button("shape", LEFT, y, HALF, H, "Shape: " + light.shape.displayName() + " ▼", false, () -> openList = new OptionList(LEFT,
                    shapeY + H, HALF, names, java.util.Arrays.asList(shapes).indexOf(light.shape), index -> {
                // A new shape comes with its ready-made look (smaller on objects)
                LightShape shape = shapes[index];
                shape.applyLook(light, draft.mode == Mode.OBJECT ? objectScale(draft) : 1.0f);
                draft.stretchOn = light.isStretched();
                if (shape.animated() && draft.mode != Mode.FLASHLIGHT) {
                    // These shapes move by themselves and come with their own animations
                    draft.animationsOn = true;
                    shape.applyAnimations(draft.animation);
                }
            }, index -> shapes[index].animated())));
        }
        int stretchX = draft.mode == Mode.FLASHLIGHT ? LEFT : RIGHT;
        int stretchWidth = draft.mode == Mode.FLASHLIGHT ? INNER : HALF;
        button("stretch", stretchX, y, stretchWidth, "Stretch: " + (draft.stretchOn ? "On" : "Off"), draft.stretchOn, () -> draft.stretchOn = !draft.stretchOn);
        y += ROW;
        if (draft.stretchOn) {
            int third = (INNER - 8) / 3;
            slider(y, LEFT, third, "stretch_x", "X", "", -8, 8, -64, 64, 0.05, () -> light.stretchX, v -> light.stretchX = (float) v, 1);
            slider(y, LEFT + third + 4, third, "stretch_y", "Y", "", -8, 8, -64, 64, 0.05, () -> light.stretchY, v -> light.stretchY = (float) v, 1);
            y = slider(y, LEFT + (third + 4) * 2, third, "stretch_z", "Z", "", -8, 8, -64, 64, 0.05, () -> light.stretchZ, v -> light.stretchZ = (float) v, 1);
            // Where the stretch grows from, on the light's own axes
            header(LEFT, y + 3, "Pivot");
            int pivotColumn = (INNER - 34 - 8) / 3;
            slider(y, LEFT + 34, pivotColumn, "pivot_x", "X", "", -8, 8, -64, 64, 0.05, () -> light.pivotX, v -> light.pivotX = (float) v, 0);
            slider(y, LEFT + 34 + pivotColumn + 4, pivotColumn, "pivot_y", "Y", "", -8, 8, -64, 64, 0.05, () -> light.pivotY, v -> light.pivotY = (float) v, 0);
            y = slider(y, LEFT + 34 + (pivotColumn + 4) * 2, pivotColumn, "pivot_z", "Z", "", -8, 8, -64, 64, 0.05, () -> light.pivotZ,
                    v -> light.pivotZ = (float) v, 0);
        }
        if (draft.mode != Mode.FLASHLIGHT) {
            slider(y, LEFT, HALF, "yaw", "Yaw", "°", -180, 180, -360, 360, 1, () -> light.yaw, v -> light.yaw = (float) v, 0);
            y = slider(y, RIGHT, HALF, "pitch", "Pitch", "°", -90, 90, -90, 90, 1, () -> light.pitch, v -> light.pitch = (float) v, light.shape.defaultPitch);
            if (draft.mode != Mode.OBJECT) {
                button("aim_view", LEFT, y, INNER, "Player camera position", false, () -> {
                    light.yaw = Math.round(minecraft.player.getYRot() * 10.0f) / 10.0f;
                    light.pitch = Math.round(minecraft.player.getXRot() * 10.0f) / 10.0f;
                });
                y += ROW;
            }
        }
        y += 2;

        header(LEFT, y, "Light");
        y += 12;
        LightShape.Look look = light.shape.look;
        y = slider(y, LEFT, INNER, "radius", draft.mode == Mode.FLASHLIGHT ? "Size" : "Radius", "", 0, 16, 0, 512, 0.01, () -> light.radius,
                v -> light.radius = (float) v, look.radius());
        y = slider(y, LEFT, INNER, "distance", "Distance", "", 0, 16, 0, 512, 0.01, () -> light.distance, v -> light.distance = (float) v, look.distance());
        if (light.shape.hasAngle()) {
            y = slider(y, LEFT, INNER, "angle", "Angle", "°", 1, LightShape.MAX_ANGLE, 1, LightShape.MAX_ANGLE, 0.5, light::effectiveAngle,
                    v -> light.angle = (float) v, look.angle());
        }
        y = slider(y, LEFT, INNER, "intensity", "Intensity", "", 0, 10, 0, 100, 0.05, () -> light.intensity, v -> light.intensity = (float) v,
                look.intensity());
        y = slider(y, LEFT, INNER, "atmosphere", "Atmosphere", "", 0, 10, 0, 10, 0.1, () -> light.atmosphere, v -> light.atmosphere = (float) v,
                look.atmosphere());
        // How much the light also makes what it reaches glow by itself
        y = slider(y, LEFT, INNER, "luminosity", "Luminosity", "", 0, 5, 0, 10, 0.05, () -> light.luminosity,
                v -> light.luminosity = (float) v, 0);
        if (draft.mode != Mode.FLASHLIGHT) {
            slider(y, LEFT, HALF, "fade_in", "Fade in", "t", 0, 100, 0, 10000, 1, () -> light.fadeIn, v -> light.fadeIn = (int) Math.round(v), DEFAULT_FADE);
            y = slider(y, RIGHT, HALF, "fade_out", "Fade out", "t", 0, 100, 0, 10000, 1, () -> light.fadeOut, v -> light.fadeOut = (int) Math.round(v),
                    DEFAULT_FADE);
        }
        // Off: solid blocks stop the light, so it does not show on the other side of walls
        Button through = new Button("see_through", LEFT, y, INNER, H, "Through blocks: " + (light.seeThrough ? "Yes" : "No"), !light.seeThrough,
                () -> light.seeThrough = !light.seeThrough);
        through.tooltip = light.seeThrough ? "The light also shows behind walls (click to stop it at solid blocks)"
                : "Solid blocks stop the light (click to let it pass through)";
        widgets.add(through);
        y += ROW;
        y += 4;
        return colorSection(y, draft);
    }

    private int colorSection(int y, Draft draft) {
        header(LEFT, y, "Color");
        y += 12;
        widgets.add(new ColorWheel(LEFT, y, draft));
        widgets.add(new BrightnessBar(LEFT + WHEEL_SIZE + 6, y, 10, WHEEL_SIZE, draft));
        int x = LEFT + WHEEL_SIZE + 22;
        int w = INNER - (x - LEFT);
        widgets.add(new Swatch("preview", x, y, w, 12, () -> draft.light.color, null));
        textField("color", x, y + 15, w, "", () -> LightColors.format(draft.light.color), text -> {
            Integer color = LightColors.parse(text);
            if (color != null) {
                draft.setColor(color);
            } else {
                setStatus("Unknown color", COLOR_ERROR);
            }
        }, COLOR_CHARS);
        int columns = 8;
        int cell = w / columns;
        for (int i = 0; i < SWATCHES.length; i++) {
            int color = SWATCHES[i];
            widgets.add(new Swatch("swatch" + i, x + (i % columns) * cell, y + 34 + (i / columns) * 14, cell - 2, 12, () -> color, () -> draft.setColor(color)));
        }
        y += WHEEL_SIZE + 4;
        int third = (INNER - 8) / 3;
        slider(y, LEFT, third, "hue", "H", "°", 0, 360, 0, 360, 1, () -> draft.hue, v -> draft.setHsv((float) v, draft.saturation, draft.brightness), 28);
        slider(y, LEFT + third + 4, third, "saturation", "S", "", 0, 1, 0, 1, 0.01, () -> draft.saturation,
                v -> draft.setHsv(draft.hue, (float) v, draft.brightness), 0.88);
        y = slider(y, LEFT + (third + 4) * 2, third, "brightness", "B", "", 0, 1, 0, 1, 0.01, () -> draft.brightness,
                v -> draft.setHsv(draft.hue, draft.saturation, (float) v), 1);
        return y + 4;
    }

    private int animationSection(int y, Draft draft) {
        LightAnimation animation = draft.animation;
        button("animations", LEFT, y, INNER, "Animations: " + (draft.animationsOn ? "On ▼" : "None ▶"), draft.animationsOn,
                () -> draft.animationsOn = !draft.animationsOn);
        y += ROW + 2;
        if (!draft.animationsOn) {
            return y;
        }
        header(LEFT, y, "Presets");
        y += 12;
        LightAnimation.Preset[] presets = LightAnimation.Preset.values();
        int columns = 4;
        int cell = (INNER + 2) / columns;
        for (int i = 0; i < presets.length; i++) {
            LightAnimation.Preset preset = presets[i];
            boolean on = animation.entry(preset) != null;
            button("preset_" + preset.id, LEFT + (i % columns) * cell, y + (i / columns) * (H + 2), cell - 2, preset.id, on, () -> {
                if (!animation.remove(preset)) {
                    animation.put(preset, LightAnimation.DEFAULT_TIME, null);
                }
            });
        }
        y += ((presets.length + columns - 1) / columns) * (H + 2) + 4;

        int nameWidth = 56;
        int sliderWidth = (INNER - nameWidth - 4 - 16) / 2;
        for (LightAnimation.Entry entry : new ArrayList<>(animation.presets)) {
            LightAnimation.Preset preset = entry.preset;
            LightAnimation.AmountKind kind = preset.amount;
            boolean radius = kind == LightAnimation.AmountKind.RADIUS;
            String unit = kind == LightAnimation.AmountKind.TURN || kind == LightAnimation.AmountKind.ANGLE ? "°" : "";
            widgets.add(new Label(LEFT, y + 3, preset.id, COLOR_HEADER));
            int sliderX = LEFT + nameWidth;
            slider(y, sliderX, sliderWidth, "preset_time_" + preset.id, "Time", "t", 1, 200, 1, 10000, 1, () -> entry.time,
                    v -> entry.time = (int) Math.max(1, Math.round(v)), LightAnimation.DEFAULT_TIME);
            slider(y, sliderX + sliderWidth + 4, sliderWidth, "preset_amount_" + preset.id, radius ? "Blocks" : kind.label, unit, kind.min, kind.max,
                    radius ? 0 : kind.min, radius ? 512 : kind.max, kind.step, () -> entry.amount, v -> entry.amount = (float) v, preset.defaultAmount());
            button("preset_remove_" + preset.id, LEFT + INNER - 13, y, 13, "x", false, () -> animation.remove(preset));
            y += ROW;
        }
        if (animation.presets.stream().anyMatch(entry -> entry.preset.moves())) {
            slider(y, LEFT, HALF, "pulse_to", "Pulse to", "", 0, 16, 0, 512, 0.1, () -> animation.radiusPulseTarget,
                    v -> animation.radiusPulseTarget = (float) v, 2);
            y = slider(y, RIGHT, HALF, "pulse_time", "Pulse time", "t", 0, 200, 0, 10000, 1, () -> animation.radiusPulseTime,
                    v -> animation.radiusPulseTime = (int) Math.round(v), 0);
        }
        y += 4;

        button("transition", LEFT, y, INNER, "Color transition: " + (draft.transitionOn ? "On" : "Off"), draft.transitionOn,
                () -> draft.transitionOn = !draft.transitionOn);
        y += ROW;
        if (draft.transitionOn) {
            for (int i = 0; i < draft.transition.size(); i++) {
                int[] step = draft.transition.get(i);
                int index = i;
                widgets.add(new Swatch("step_swatch" + i, LEFT, y + 1, 12, 12, () -> step[0], () -> step[0] = draft.light.color));
                textField("step_color" + i, LEFT + 15, y, 70, "", () -> LightColors.format(step[0]), text -> {
                    Integer color = LightColors.parse(text);
                    if (color != null) {
                        step[0] = color;
                    }
                }, COLOR_CHARS);
                slider(y, LEFT + 89, INNER - 89 - 16, "step_time" + i, "Time", "t", 1, 200, 1, 10000, 1, () -> step[1],
                        v -> step[1] = (int) Math.max(1, Math.round(v)), 40);
                button("step_remove" + i, LEFT + INNER - 13, y, 13, "x", false, () -> {
                    if (draft.transition.size() > 2) {
                        draft.transition.remove(index);
                    }
                });
                y += ROW;
            }
            button("step_add", LEFT, y, INNER, "+ Add color", false, () -> draft.transition.add(new int[]{draft.light.color, 40}));
            y += ROW;
        }
        button("size_pulse", LEFT, y, INNER, "Size pulse: " + (draft.sizeOn ? "On" : "Off"), draft.sizeOn, () -> draft.sizeOn = !draft.sizeOn);
        y += ROW;
        if (draft.sizeOn) {
            int third = (INNER - 8) / 3;
            slider(y, LEFT, third, "size_radius", "Radius", "", 0, 16, 0, 512, 0.01, () -> draft.sizeRadius, v -> draft.sizeRadius = (float) v, 3);
            slider(y, LEFT + third + 4, third, "size_distance", "Dist.", "", 0, 16, 0, 512, 0.01, () -> draft.sizeDistance,
                    v -> draft.sizeDistance = (float) v, 3);
            y = slider(y, LEFT + (third + 4) * 2, third, "size_time", "Time", "t", 1, 200, 1, 10000, 1, () -> draft.sizeTime,
                    v -> draft.sizeTime = (int) Math.max(1, Math.round(v)), 40);
        }
        return y;
    }

    // ── Lights tab
    // ─────────────────────────────────────────────────

    private int layoutLights(int y) {
        boolean allOn = LightGroups.allEnabled();
        button("all_switch", LEFT, y, HALF, allOn ? "Disable all" : "Enable all", !allOn, () -> {
            LightGroups.setAllEnabled(!LightGroups.allEnabled());
            setStatus(LightGroups.allEnabled() ? "Saved lights on" : "Saved lights off", COLOR_OK);
        });
        button("group_new", RIGHT, y, HALF, "+ New group", false, () -> {
            String name = freeGroupName();
            LightGroups.add(name);
            focus("group_name_" + name, name, text -> renameGroup(name, text), ID_CHARS);
        });
        y += ROW + 2;
        widgets.add(new Label(LEFT, y, "Drag a light by its name to move it to another group", COLOR_DIM));
        y += 12;

        List<Light> lights = LightManager.visibleLights();
        // Groups named by lights (pasted or from commands) are listed too
        for (Light light : lights) {
            if (!light.group.isEmpty() && LightGroups.get(light.group) == null) {
                LightGroups.add(light.group);
            }
        }
        for (LightGroups.Group group : LightGroups.groups()) {
            y = groupHeader(y, group);
            if (group.collapsed) {
                continue;
            }
            int count = 0;
            for (Light light : lights) {
                if (light.group.equalsIgnoreCase(group.name)) {
                    y = savedLightRow(y, light, group.name);
                    count++;
                }
            }
            if (count == 0) {
                widgets.add(dropZone(new Label(LEFT + 14, y + 3, "Empty: drop lights here", COLOR_DIM), y, group.name));
                y += ROW;
            }
        }

        // Lights without a group, server lights and the flashlight
        Label ungrouped = new Label(LEFT, y + 3, LightGroups.groups().isEmpty() ? "Saved lights" : "No group", COLOR_HEADER);
        widgets.add(dropZone(ungrouped, y, ""));
        y += ROW;
        boolean any = false;
        for (Light light : lights) {
            if (light.group.isEmpty()) {
                y = savedLightRow(y, light, "");
                any = true;
            }
        }
        if (!any) {
            widgets.add(dropZone(new Label(LEFT + 14, y + 3, lights.isEmpty() ? "No lights yet" : "Empty", COLOR_DIM), y, ""));
            y += ROW;
        }
        return y;
    }

    /** A group: fold arrow, name (click to rename), on/off, animations and delete. */
    private int groupHeader(int y, LightGroups.Group group) {
        String name = group.name;
        widgets.add(dropZone(new Button("group_fold_" + name, LEFT, y, 14, H, group.collapsed ? "▶" : "▼", false, () -> {
            group.collapsed = !group.collapsed;
            LightGroups.markChanged();
        }), y, name));
        int buttons = 3 * 40;
        TextField field = new TextField("group_name_" + name, LEFT + 16, y, INNER - 16 - buttons - 2, H, "", () -> name,
                text -> renameGroup(name, text), ID_CHARS);
        widgets.add(dropZone(field, y, name));
        int x = LEFT + INNER - buttons;
        Button power = new Button("group_power_" + name, x, y, 38, H, group.enabled ? "On" : "Off", !group.enabled,
                () -> LightGroups.setEnabled(group, !group.enabled));
        power.tooltip = group.enabled ? "Turn the group off" : "Turn the group on";
        widgets.add(power);
        Button motion = new Button("group_anim_" + name, x + 40, y, 38, H, group.animations ? "Anim" : "Still", !group.animations,
                () -> LightGroups.setAnimations(group, !group.animations));
        motion.tooltip = group.animations ? "Stop the animations of its lights" : "Play the animations of its lights";
        widgets.add(motion);
        boolean confirming = ("group:" + name).equals(confirmDelete);
        Button delete = new Button("group_delete_" + name, x + 80, y, 38, H, confirming ? "Sure?" : "Del", confirming, () -> {
            if (!("group:" + name).equals(confirmDelete)) {
                confirmDelete = "group:" + name;
                return;
            }
            confirmDelete = null;
            LightGroups.remove(name);
            setStatus("Deleted group " + name + " (its lights stay)", COLOR_OK);
        });
        delete.tooltip = "Delete the group";
        widgets.add(delete);
        return y + ROW;
    }

    private void renameGroup(String name, String text) {
        if (text.isBlank() || text.equals(name)) {
            return;
        }
        if (!LightGroups.rename(name, text)) {
            setStatus("A group called " + text + " already exists", COLOR_ERROR);
        }
    }

    private static String freeGroupName() {
        for (int i = 1; ; i++) {
            if (LightGroups.get("group" + i) == null) {
                return "group" + i;
            }
        }
    }

    private static <T extends Widget> T dropZone(T widget, int y, String group) {
        widget.dropGroup = group;
        widget.dropY = y;
        return widget;
    }

    /** A saved light: drag handle and name (drag to another group), then Edit, Copy and Del. */
    private int savedLightRow(int y, Light light, String group) {
        Mode mode = light.isFollow() ? Mode.FOLLOW : Mode.STATIC;
        int rowY = y;
        y = lightRow(y, light, light.id + (light.server ? " (server)" : ""), () -> editLight(light, mode),
                () -> serverCommand(LightPaste.command(light, type(mode))), () -> deleteLight(light, type(mode)));
        int buttonsX = LEFT + INNER - 118;
        widgets.add(dropZone(new DragHandle("row_drag_" + (light.server ? "server_" : "") + light.id, LEFT, rowY, buttonsX - LEFT - 2, H, light), rowY, group));
        return y;
    }

    /** The name part of a saved light row: dragging it moves the light to the group it is dropped on. */
    private final class DragHandle extends Widget {
        private final Light light;

        DragHandle(String key, int x, int y, int w, int h, Light light) {
            super(key, x, y, w, h);
            this.light = light;
            this.tooltip = null;
        }

        @Override
        void render(GuiGraphics graphics, Font font, boolean hovered) {
            if (hovered || draggedLight == light) {
                graphics.fill(x, y, x + w, y + h, 0x30FFFFFF);
            }
        }

        @Override
        void startDrag() {
            draggedLight = light;
        }

        @Override
        void release(boolean dragged) {
            if (!dragged) {
                return;
            }
            Light moved = draggedLight;
            draggedLight = null;
            if (moved == null) {
                return;
            }
            for (int i = widgets.size() - 1; i >= 0; i--) {
                Widget target = widgets.get(i);
                if (target.dropGroup != null && lastMouseY >= target.dropY && lastMouseY < target.dropY + ROW && lastMouseX < PANEL_WIDTH) {
                    if (!moved.group.equalsIgnoreCase(target.dropGroup)) {
                        LightGroups.assign(moved, target.dropGroup);
                        setStatus(target.dropGroup.isEmpty() ? "Took " + moved.id + " out of its group" : "Moved " + moved.id + " to " + target.dropGroup,
                                COLOR_OK);
                    }
                    return;
                }
            }
        }
    }

    // ── History (right side)
    // ───────────────────────────────────────

    private static final int HISTORY_WIDTH = 230;
    private static final int HISTORY_ROW = 13;
    private static boolean historyOpen;

    /** Added, overwritten and deleted lights, newest first: click one to edit it, Restore saves it back. */
    private void layoutHistory(Font font) {
        int listWidth = Math.min(HISTORY_WIDTH, width - PANEL_WIDTH - 12);
        if (listWidth < 150) {
            return;
        }
        int x = width - listWidth - 4;
        int y = 4;
        fixed(new Button("history_toggle", x, y, listWidth, H, "History " + (historyOpen ? "▲" : "▼"), historyOpen, () -> historyOpen = !historyOpen));
        if (!historyOpen) {
            return;
        }
        y += H + 2;
        List<EditHistory.Entry> entries = EditHistory.entries();
        int rows = Math.max(1, entries.size());
        fixed(new Fill(x - 2, y - 2, listWidth + 4, rows * HISTORY_ROW + 4, 0xE0120A1C));
        if (entries.isEmpty()) {
            fixed(new Label(x + 2, y + 3, "No changes yet", COLOR_DIM));
            return;
        }
        int restoreWidth = 48;
        for (int i = 0; i < entries.size(); i++) {
            EditHistory.Entry entry = entries.get(i);
            fixed(new HistoryRow("history_row_" + i, x, y, listWidth - restoreWidth - 2, HISTORY_ROW, entry));
            fixed(new Button("history_restore_" + i, x + listWidth - restoreWidth, y + 1, restoreWidth, HISTORY_ROW - 2, "Restore", false, () -> {
                commitField();
                EditHistory.restore(entry);
                setStatus("Restored " + entry.name(), COLOR_OK);
            }));
            y += HISTORY_ROW;
        }
    }

    private static final class Fill extends Widget {
        private final int color;

        Fill(int x, int y, int w, int h, int color) {
            super("", x, y, w, h);
            this.color = color;
        }

        @Override
        boolean interactive() {
            return false;
        }

        @Override
        void render(GuiGraphics graphics, Font font, boolean hovered) {
            graphics.fill(x, y, x + w, y + h, color);
        }
    }

    /** Time, name and what happened; a click opens that version in the editor. */
    private final class HistoryRow extends Widget {
        private final EditHistory.Entry entry;

        HistoryRow(String key, int x, int y, int w, int h, EditHistory.Entry entry) {
            super(key, x, y, w, h);
            this.entry = entry;
            this.tooltip = entry.target() == EditHistory.Target.OBJECT ? "Object light: click to edit this version" : "Click to edit this version";
        }

        @Override
        void render(GuiGraphics graphics, Font font, boolean hovered) {
            if (hovered) {
                graphics.fill(x, y, x + w, y + h, 0x607446BE);
            }
            int textY = y + (h - 8) / 2;
            String time = EditHistory.time(entry.time());
            graphics.drawString(font, time, x + 2, textY, COLOR_DIM, true);
            String action = switch (entry.action()) {
                case ADDED -> "added";
                case UPDATED -> "updated";
                case DELETED -> "deleted";
            };
            int nameX = x + 6 + font.width(time);
            String name = font.plainSubstrByWidth(entry.name(), Math.max(12, x + w - nameX - font.width(action) - 6));
            graphics.drawString(font, name, nameX, textY, entry.target() == EditHistory.Target.OBJECT ? COLOR_COMMAND : COLOR_HEADER, true);
            int actionColor = switch (entry.action()) {
                case ADDED -> COLOR_OK;
                case UPDATED -> 0xFFFFD060;
                case DELETED -> COLOR_ERROR;
            };
            graphics.drawString(font, action, nameX + font.width(name) + 4, textY, actionColor, true);
        }

        @Override
        void release(boolean dragged) {
            if (dragged) {
                return;
            }
            commitField();
            if (entry.target() == EditHistory.Target.OBJECT) {
                editObject(entry.rule());
            } else {
                Light light = entry.light();
                Mode mode = entry.type().equals("flashlight") ? Mode.FLASHLIGHT : entry.type().equals("follow") ? Mode.FOLLOW : Mode.STATIC;
                editLight(light, mode);
            }
            scroll = 0.0d;
            setStatus("Editing " + entry.name() + " as it was", COLOR_OK);
        }
    }


    private int lightRow(int y, Light light, String name, Runnable edit, Supplier<String> copy, Runnable delete) {
        Font font = Minecraft.getInstance().font;
        widgets.add(new Swatch("row_swatch_" + name, LEFT, y + 2, 10, 10, () -> light.color, null));
        int buttonsX = LEFT + INNER - (copy != null ? 118 : 78);
        widgets.add(new Label(LEFT + 14, y + 3, font.plainSubstrByWidth(name, buttonsX - LEFT - 18), COLOR_TEXT));
        button("row_edit_" + name, buttonsX, y, 38, "Edit", false, () -> {
            edit.run();
            scroll = 0.0d;
        });
        int x = buttonsX + 40;
        if (copy != null) {
            button("row_copy_" + name, x, y, 38, "Copy", false, () -> {
                Minecraft.getInstance().keyboardHandler.setClipboard(copy.get());
                setStatus("Copied " + light.id, COLOR_OK);
            });
            x += 40;
        }
        button("row_delete_" + name, x, y, 38, "Del", false, delete);
        return y + ROW;
    }

    // ── Objects tab
    // ────────────────────────────────────────────────

    /** Objects tab, presets view: named lists of object lights kept in config/customlights/presets. */
    private int layoutPresets(int y) {
        Font font = Minecraft.getInstance().font;
        header(LEFT, y, "Preset name");
        y += 12;
        textField("preset_name", LEFT, y, INNER, "", () -> presetName, text -> {
            if (!text.isBlank()) {
                presetName = text;
            }
        }, ID_CHARS);
        y += ROW;
        button("preset_save_all", LEFT, y, HALF, "Save all objects", false, () -> {
            commitField();
            int count = ObjectPresets.saveAll(presetName);
            setStatus("Saved " + count + " objects in " + presetName, COLOR_OK);
        });
        button("preset_folder", RIGHT, y, HALF, "Open presets folder", false, ObjectPresets::openFolder);
        y += ROW + 6;

        header(LEFT, y, "Saved presets");
        y += 14;
        List<ObjectPresets.Summary> presets = ObjectPresets.list();
        if (presets.isEmpty()) {
            widgets.add(new Label(LEFT, y + 2, "No presets yet: name one and save", COLOR_DIM));
            y += 14;
        }
        boolean editor = ServerEdits.canEdit();
        int buttons = (editor ? 4 : 3) * 42;
        for (ObjectPresets.Summary preset : presets) {
            String name = preset.name();
            boolean selected = name.equalsIgnoreCase(presetName);
            String text = font.plainSubstrByWidth(name + " (" + preset.count() + ")", INNER - buttons - 8);
            button("preset_pick_" + name, LEFT, y, INNER - buttons - 2, text, selected, () -> presetName = name);
            int x = LEFT + INNER - buttons;
            button("preset_load_" + name, x, y, 40, "Load", false, () -> {
                if (ServerEdits.everyone()) {
                    // Every object of the preset for everyone
                    java.util.Map<String, ObjectLights.Rule> rules = ObjectPresets.read(name);
                    rules.values().forEach(ServerEdits::putObject);
                    sharedStatus("Sent " + rules.size() + " objects of " + name + " to everyone");
                    return;
                }
                int count = ObjectPresets.load(name);
                setStatus("Loaded " + count + " objects from " + name, COLOR_OK);
            });
            button("preset_unload_" + name, x + 42, y, 40, "Unload", false, () -> {
                if (ServerEdits.everyone()) {
                    java.util.Set<String> keys = ObjectPresets.read(name).keySet();
                    keys.forEach(ServerEdits::removeObject);
                    setStatus("Took out " + keys.size() + " objects of " + name + " for everyone", COLOR_OK);
                    return;
                }
                int count = ObjectPresets.unload(name);
                setStatus("Took out " + count + " objects of " + name, COLOR_OK);
            });
            if (editor) {
                // Shares the preset with every player (it shows in their list), or stops sharing it
                boolean shared = preset.server();
                Button share = new Button("preset_share_" + name, x + 126, y, 40, H, shared ? "Unshare" : "Share", false, () -> {
                    if (shared) {
                        ServerEdits.removePreset(name.substring(ObjectPresets.SERVER_PREFIX.length()));
                        setStatus("Stopped sharing " + name, COLOR_OK);
                    } else {
                        JsonObject json = ObjectPresets.json(name);
                        if (json != null) {
                            ServerEdits.putPreset(name, json);
                            sharedStatus("Shared " + name + " with everyone");
                        }
                    }
                });
                share.tooltip = shared ? "Take this preset away from every player's list" : "Put this preset in every player's preset list";
                widgets.add(share);
            }
            if (preset.server()) {
                y += ROW;
                continue;
            }
            boolean confirming = name.equals(confirmDelete);
            button("preset_delete_" + name, x + 84, y, 40, confirming ? "Sure?" : "Del", confirming, () -> {
                if (!name.equals(confirmDelete)) {
                    confirmDelete = name;
                    return;
                }
                confirmDelete = null;
                if (ObjectPresets.delete(name)) {
                    setStatus("Deleted preset " + name, COLOR_OK);
                }
            });
            y += ROW;
        }

        y += 6;
        header(LEFT, y, "In " + font.plainSubstrByWidth(presetName, INNER - 30));
        y += 14;
        List<ObjectLights.Rule> contents = ObjectPresets.contents(presetName);
        if (contents.isEmpty()) {
            widgets.add(new Label(LEFT, y + 2, "Empty", COLOR_DIM));
            y += 14;
        }
        for (ObjectLights.Rule rule : contents) {
            String key = rule.key();
            widgets.add(new Swatch("preset_swatch_" + key, LEFT, y + 2, 10, 10, () -> rule.light.color, null));
            widgets.add(new Label(LEFT + 14, y + 3, font.plainSubstrByWidth(rule.kind.id + " " + shortId(rule.label()), INNER - 100), COLOR_TEXT));
            button("preset_edit_" + key, LEFT + INNER - 80, y, 38, "Edit", false, () -> {
                editObject(rule.copy());
                presetsView = false;
                scroll = 0.0d;
            });
            button("preset_remove_" + key, LEFT + INNER - 40, y, 38, "Del", false, () -> {
                if (ObjectPresets.removeObject(presetName, key)) {
                    setStatus("Removed " + shortId(rule.label()) + " from " + presetName, COLOR_OK);
                }
            });
            y += ROW;
        }
        return y;
    }

    private int layoutObjects(int y) {
        Minecraft minecraft = Minecraft.getInstance();
        Draft draft = objectDraft;
        Light light = draft.light;

        y = everyoneRow(y);
        // Edit one object, or manage presets (lists of objects)
        button("view_object", LEFT, y, HALF, "Edit object", !presetsView, () -> presetsView = false);
        button("view_presets", RIGHT, y, HALF, "Presets", presetsView, () -> presetsView = true);
        y += ROW + 4;
        if (presetsView) {
            return layoutPresets(y);
        }
        header(LEFT, y, "Object");
        y += 12;
        ObjectLights.Kind[] kinds = ObjectLights.Kind.values();
        int cell = (INNER + 2) / kinds.length;
        for (int i = 0; i < kinds.length; i++) {
            ObjectLights.Kind kind = kinds[i];
            button("kind_" + kind.id, LEFT + i * cell, y, cell - 2, kind.label(), draft.kind == kind, () -> {
                if (draft.kind != kind) {
                    draft.kind = kind;
                    draft.target = switch (kind) {
                        case BONE -> "light";
                        case SPECIAL -> ObjectLights.BEACON_BEAM;
                        default -> "";
                    };
                    draft.customModelData = -1;
                    draft.search = "";
                    draft.pickScroll = 0;
                    ObjectLights.Rule saved = ObjectLights.rule(ObjectLights.keyOf(kind, draft.target, -1));
                    if (saved != null && !saved.light.removing) {
                        editObject(saved);
                    } else {
                        resetObjectLight(draft);
                    }
                }
            });
        }
        y += ROW + 2;

        if (draft.kind == ObjectLights.Kind.BONE) {
            textField("bone", LEFT, y, INNER, "Bone id ", () -> draft.target, text -> {
                if (!text.isEmpty()) {
                    draft.target = text.toLowerCase(Locale.ROOT);
                }
            }, ID_CHARS);
            y += ROW;
            widgets.add(new Label(LEFT, y, "Name the bone on Blockbench:", COLOR_DIM));
            y += 11;
            widgets.add(new Label(LEFT, y, "customlights_" + draft.target, COLOR_COMMAND));
            y += 14;
        } else {
            textField("search", LEFT, y, INNER, "Search ", () -> draft.search, text -> {
                draft.search = text;
                draft.pickScroll = 0;
            }, TEXT_CHARS);
            y += ROW;
            widgets.add(new Picker(LEFT, y, INNER, PICK_ROWS * PICK_ROW + 2, matches(draft), draft));
            y += PICK_ROWS * PICK_ROW + 6;
            if (draft.kind == ObjectLights.Kind.ITEM) {
                widgets.add(new Label(LEFT, y, "The light moves with the item model", COLOR_DIM));
                y += 12;
            }
            if (draft.kind == ObjectLights.Kind.ITEM) {
                widgets.add(new NumberField("cmd", LEFT, y, INNER, H, "Custom model data ", () -> draft.customModelData,
                        v -> draft.customModelData = (int) Math.max(-1, Math.round(v)), 0.25, draft.customModelData < 0 ? "any" : null));
                y += ROW;
            }
        }
        if (draft.kind == ObjectLights.Kind.PARTICLE && !draft.target.isEmpty() && !ObjectPreview.canShowObject()) {
            widgets.add(new Label(LEFT, y, "Shown as light only (can't spawn it)", COLOR_DIM));
            y += 12;
        }

        header(LEFT, y + 3, draft.target.isEmpty() ? "Pick an object" : shortId(draft.target));
        button("move_stage", LEFT + INNER - 90, y, 90, "Preview here", false, () -> placeStage(minecraft));
        y += ROW;
        if (draft.kind == ObjectLights.Kind.ITEM) {
            y = displaySection(y, draft);
        } else {
            header(LEFT, y + 3, "Offset");
            y += ROW - 4;
            y = coordinates(y, "object_offset", () -> light.x, v -> light.x = v, () -> light.y, v -> light.y = v, () -> light.z, v -> light.z = v);
        }
        y += 4;
        y = lightSettings(y, draft);
        y = animationSection(y, draft);

        y += 6;
        header(LEFT, y, "Saved objects");
        y += 14;
        List<ObjectLights.Rule> rules = new ArrayList<>();
        for (ObjectLights.Rule rule : ObjectLights.rules()) {
            if (!rule.light.removing) {
                rules.add(rule);
            }
        }
        if (rules.isEmpty()) {
            widgets.add(new Label(LEFT, y + 2, "Nothing saved yet", COLOR_DIM));
            y += 14;
        }
        for (ObjectLights.Rule rule : rules) {
            y = lightRow(y, rule.light, rule.kind.id + " " + shortId(rule.label()), () -> editObject(rule), null, () -> deleteRule(rule));
        }
        return y;
    }

    /** What a follow light is on right now, as a short line. */
    private static String followInfo(Minecraft minecraft, Light light) {
        List<net.minecraft.world.entity.Entity> found = LightManager.targets(minecraft, light);
        if (found.isEmpty()) {
            return "Not found nearby";
        }
        if (found.size() > 1) {
            return "On " + found.size() + " entities";
        }
        net.minecraft.world.entity.Entity entity = found.get(0);
        if (entity == minecraft.player) {
            return minecraft.options.getCameraType().isFirstPerson() ? "On you (F5 to see it)" : "On you";
        }
        if (entity instanceof net.minecraft.world.entity.player.Player player) {
            return "On player " + player.getGameProfile().getName();
        }
        return "On " + shortId(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()) + (entity.hasCustomName() ? " " + entity.getCustomName().getString() : "");
    }

    /** Switches the display context being edited: each context keeps its own light settings. */
    private void selectDisplay(Draft draft, ItemDisplayContext context) {
        if (draft.display == context) {
            return;
        }
        commitField();
        draft.sync();
        draft.displays.computeIfAbsent(draft.display, ignored -> new ObjectLights.Display(false, 0.0d, 0.0d, 0.0d)).light = draft.light.copy(draft.light.id);
        ObjectLights.Display next = draft.displays.computeIfAbsent(context, ignored -> new ObjectLights.Display(false, 0.0d, 0.0d, 0.0d));
        Light light = next.light != null ? next.light : draft.light;
        draft.display = context;
        draft.load(light, Mode.OBJECT);
    }

    private static final String[] DISPLAY_NAMES = {"1st R", "1st L", "3rd R", "3rd L", "Head", "Ground", "Frame"};
    private static final String[] DISPLAY_TIPS = {"First person, right hand", "First person, left hand", "Third person, right hand",
            "Third person, left hand", null, null, null};

    /** Items: the display contexts (like Blockbench) with where the light shows in each one. */
    private int displaySection(int y, Draft draft) {
        header(LEFT, y, "Display");
        y += 12;
        int columns = 4;
        int cell = (INNER + 2) / columns;
        List<ItemDisplayContext> contexts = ObjectLights.DISPLAYS;
        for (int i = 0; i < contexts.size(); i++) {
            ItemDisplayContext context = contexts.get(i);
            ObjectLights.Display shown = draft.displays.get(context);
            String text = (shown != null && shown.on ? "● " : "") + DISPLAY_NAMES[i];
            Button choice = new Button("display_" + context.name(), LEFT + (i % columns) * cell, y + (i / columns) * (H + 2), cell - 2, H, text,
                    draft.display == context, () -> selectDisplay(draft, context));
            choice.tooltip = DISPLAY_TIPS[i];
            widgets.add(choice);
        }
        y += ((contexts.size() + columns - 1) / columns) * (H + 2) + 2;
        ObjectLights.Display shown = draft.displays.computeIfAbsent(draft.display, ignored -> new ObjectLights.Display(false, 0.0d, 0.0d, 0.0d));
        button("display_on", LEFT, y, INNER, "Light in " + DISPLAY_NAMES[contexts.indexOf(draft.display)] + ": " + (shown.on ? "On" : "Off"), shown.on,
                () -> shown.on = !shown.on);
        y += ROW;
        header(LEFT, y + 3, "Offset (pixels)");
        y += ROW - 4;
        y = coordinates(y, "display_offset_" + draft.display.name(), () -> shown.x, v -> shown.x = v, () -> shown.y, v -> shown.y = v,
                () -> shown.z, v -> shown.z = v);
        widgets.add(new Label(LEFT, y, "The preview shows the item like this context", COLOR_DIM));
        return y + 12;
    }

    private static String shortId(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    private static List<String> registryIds(ObjectLights.Kind kind) {
        List<String> ids = new ArrayList<>();
        Iterable<ResourceLocation> keys = switch (kind) {
            case ITEM -> BuiltInRegistries.ITEM.keySet();
            case BLOCK -> BuiltInRegistries.BLOCK.keySet();
            case PARTICLE -> BuiltInRegistries.PARTICLE_TYPE.keySet();
            case ENTITY -> BuiltInRegistries.ENTITY_TYPE.keySet();
            case SPECIAL, BONE -> List.of();
        };
        if (kind == ObjectLights.Kind.SPECIAL) {
            return ObjectLights.SPECIAL_TARGETS;
        }
        for (ResourceLocation key : keys) {
            ids.add(key.toString());
        }
        ids.sort(String::compareTo);
        return ids;
    }

    private static List<String> matches(Draft draft) {
        if (!draft.kind.id.equals(cachedKind)) {
            cachedKind = draft.kind.id;
            cachedIds = registryIds(draft.kind);
        }
        String query = draft.search.toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return cachedIds;
        }
        List<String> result = new ArrayList<>();
        for (String id : cachedIds) {
            if (id.contains(query)) {
                result.add(id);
            }
        }
        // A full id that is not in the list (a mod that is not loaded yet, a server-side id) can still be used
        String typed = query.trim();
        if (typed.contains(":") && draft.kind != ObjectLights.Kind.SPECIAL && ResourceLocation.tryParse(typed) != null && !cachedIds.contains(typed)) {
            result.add(0, typed);
        }
        return result;
    }

    private static ItemStack iconOf(ObjectLights.Kind kind, String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            return ItemStack.EMPTY;
        }
        return switch (kind) {
            case ITEM -> new ItemStack(com.cotii.customlights.client.compat.Compat.item(location));
            case BLOCK -> new ItemStack(com.cotii.customlights.client.compat.Compat.block(location).asItem());
            case PARTICLE -> new ItemStack(Items.BLAZE_POWDER);
            case ENTITY -> spawnEggOf(location);
            case SPECIAL -> new ItemStack(switch (id) {
                case ObjectLights.BEACON_BEAM -> Items.BEACON;
                case ObjectLights.END_GATEWAY_BEAM -> Items.ENDER_PEARL;
                case ObjectLights.END_CRYSTAL_BEAM -> Items.END_CRYSTAL;
                case ObjectLights.CONDUIT -> Items.CONDUIT;
                case ObjectLights.LIT_BLOCKS -> Items.FURNACE;
                case ObjectLights.BURNING_ENTITY -> Items.FLINT_AND_STEEL;
                case ObjectLights.CHARGED_CREEPER -> Items.CREEPER_HEAD;
                case ObjectLights.ENCHANTED_ITEM -> Items.ENCHANTED_BOOK;
                case ObjectLights.ENCHANTED_GEAR -> Items.DIAMOND_CHESTPLATE;
                case ObjectLights.HOSTILE_MOBS -> Items.ZOMBIE_HEAD;
                case ObjectLights.ANIMALS -> Items.WHEAT;
                case ObjectLights.PLAYERS -> Items.PLAYER_HEAD;
                case ObjectLights.PROJECTILES -> Items.ARROW;
                case ObjectLights.BOSSES -> Items.DRAGON_HEAD;
                case ObjectLights.ITEM_ENTITIES -> Items.CHEST;
                default -> Items.SPECTRAL_ARROW;
            });
            case BONE -> ItemStack.EMPTY;
        };
    }

    private static ItemStack spawnEggOf(ResourceLocation location) {
        net.minecraft.world.entity.EntityType<?> type = com.cotii.customlights.client.compat.Compat.entityType(location);
        net.minecraft.world.item.SpawnEggItem egg = net.minecraft.world.item.SpawnEggItem.byId(type);
        return new ItemStack(egg != null ? egg : Items.SPAWNER);
    }

    // ── Widget helpers
    // ─────────────────────────────────────────────

    private int coordinates(int y, String key, DoubleSupplier getX, DoubleConsumer setX, DoubleSupplier getY, DoubleConsumer setY,
                            DoubleSupplier getZ, DoubleConsumer setZ) {
        int third = (INNER - 8) / 3;
        String[] axes = {"X", "Y", "Z"};
        DoubleSupplier[] getters = {getX, getY, getZ};
        DoubleConsumer[] setters = {setX, setY, setZ};
        for (int i = 0; i < 3; i++) {
            widgets.add(new NumberField(key + axes[i], LEFT + i * (third + 4), y, third, H, axes[i] + " ", getters[i], setters[i], 0.1, null));
        }
        return y + ROW;
    }

    private void header(int x, int y, String text) {
        widgets.add(new Label(x, y, text, COLOR_HEADER));
    }

    private void button(String key, int x, int y, int w, String text, boolean selected, Runnable action) {
        widgets.add(new Button(key, x, y, w, H, text, selected, action));
    }

    private void fixed(Widget widget) {
        widget.scrolls = false;
        widgets.add(widget);
    }

    /** A slider with its reset button; returns the next row's y. */
    private int slider(int y, int x, int w, String key, String label, String unit, double softMin, double softMax, double min, double max,
                       double step, DoubleSupplier getter, DoubleConsumer setter, double reset) {
        widgets.add(new Slider(key, x, y, w - RESET - 2, H, label, unit, softMin, softMax, min, max, step, getter, setter));
        widgets.add(new Button(key + "_reset", x + w - RESET, y, RESET, H, "⟲", false, () -> setter.accept(reset)));
        return y + ROW;
    }

    private void textField(String key, int x, int y, int w, String prefix, Supplier<String> getter, Consumer<String> setter, IntPredicate filter) {
        widgets.add(new TextField(key, x, y, w, H, prefix, getter, setter, filter));
    }

    private void focus(String key, String value, Consumer<String> commit, IntPredicate filter) {
        commitField();
        focusedKey = key;
        fieldBuffer = value;
        focusedCommit = commit;
        focusedFilter = filter;
    }

    /** What Tab would complete the focused field to (follow targets and the flashlight holder only), or null. */
    private static String completion(String key, String typed) {
        if (!"target".equals(key) && !"holder".equals(key)) {
            return null;
        }
        String lower = typed.toLowerCase(java.util.Locale.ROOT);
        for (FollowTargets.Target target : FollowTargets.all(Minecraft.getInstance())) {
            String value = target.value();
            if (value.length() > typed.length() && value.toLowerCase(java.util.Locale.ROOT).startsWith(lower)) {
                return typed + value.substring(typed.length());
            }
        }
        return null;
    }

    private void commitField() {
        if (focusedKey != null && focusedCommit != null) {
            focusedCommit.accept(fieldBuffer.trim());
        }
        focusedKey = null;
        focusedCommit = null;
        focusedFilter = null;
    }

    private static double modifierScale() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS) {
            return 0.1d;
        }
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS) {
            return 10.0d;
        }
        return 1.0d;
    }

    // ── Input
    // ──────────────────────────────────────────────────────

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean overPanel = mouseX < PANEL_WIDTH || mouseY >= height - BOTTOM_HEIGHT || openList != null || overHistory(mouseX, mouseY);
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        boolean onFixed = false;
        for (Widget widget : widgets) {
            if (!widget.scrolls && widget.contains(mouseX, mouseY)) {
                onFixed = true;
                break;
            }
        }
        if (button == 0 && openList == null && !onFixed && mouseX >= PANEL_WIDTH && mouseY < height - BOTTOM_HEIGHT && gizmo.press(mouseX, mouseY)) {
            commitField();
            return true;
        }
        if (button == 1) {
            // Right button held over the world turns the camera
            if (!overPanel) {
                commitField();
                turning = true;
                return true;
            }
            return true;
        }
        if (button != 0) {
            return overPanel;
        }
        if (openList != null) {
            OptionList list = openList;
            openList = null;
            list.click(mouseX, mouseY);
            return true;
        }
        for (int i = widgets.size() - 1; i >= 0; i--) {
            Widget widget = widgets.get(i);
            if (!widget.contains(mouseX, mouseY) || !widget.interactive()) {
                continue;
            }
            if (widget.scrolls && (mouseY < AREA_TOP || mouseY >= height - BOTTOM_HEIGHT)) {
                continue;
            }
            if (!widget.key.equals(focusedKey)) {
                commitField();
            }
            long now = System.currentTimeMillis();
            boolean doubleClick = widget.key.equals(lastClickKey) && now - lastClickTime < DOUBLE_CLICK_MS;
            lastClickKey = widget.key;
            lastClickTime = now;
            pressed = widget;
            pressX = mouseX;
            pressY = mouseY;
            pressMoved = false;
            widget.press(mouseX, mouseY, doubleClick);
            return true;
        }
        commitField();
        return overPanel;
    }

    public boolean mouseReleased(int button) {
        if (button == 1) {
            boolean was = turning;
            turning = false;
            return was;
        }
        if (gizmo.dragging()) {
            gizmo.release();
            return true;
        }
        if (pressed == null) {
            return false;
        }
        Widget widget = pressed;
        pressed = null;
        widget.release(pressMoved);
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (button == 1) {
            if (turning) {
                turnCamera(deltaX, deltaY);
            }
            return true;
        }
        if (gizmo.dragging()) {
            gizmo.drag(mouseX, mouseY);
            return true;
        }
        if (pressed == null) {
            return false;
        }
        if (!pressMoved && Math.abs(mouseX - pressX) < DRAG_THRESHOLD && Math.abs(mouseY - pressY) < DRAG_THRESHOLD) {
            return true;
        }
        if (!pressMoved) {
            pressMoved = true;
            pressX = mouseX;
            pressY = mouseY;
            pressed.startDrag();
        }
        pressed.drag(mouseX, mouseY, mouseX - pressX);
        return true;
    }

    /** Turns the camera like the mouse does in game (deltas are in GUI pixels). */
    private static void turnCamera(double deltaX, double deltaY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        double sensitivity = minecraft.options.sensitivity().get() * 0.6d + 0.2d;
        double factor = sensitivity * sensitivity * sensitivity * 8.0d * minecraft.getWindow().getGuiScale();
        int invert = minecraft.options.invertYMouse().get() ? -1 : 1;
        minecraft.player.turn(deltaX * factor, deltaY * factor * invert);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (openList != null) {
            openList.scroll(amount);
            return true;
        }
        if (mouseX >= PANEL_WIDTH || mouseY >= height - BOTTOM_HEIGHT) {
            return false;
        }
        for (Widget widget : widgets) {
            if (widget instanceof Picker picker && picker.contains(mouseX, mouseY)) {
                picker.scroll(amount);
                return true;
            }
        }
        int visible = height - BOTTOM_HEIGHT - AREA_TOP;
        scroll = Math.max(0.0d, Math.min(Math.max(0, contentHeight - visible), scroll - amount * 24.0d));
        return true;
    }

    public boolean keyPressed(int keyCode, int modifiers) {
        Minecraft minecraft = Minecraft.getInstance();
        if ((modifiers & MOD_CONTROL) != 0 && (keyCode == KEY_S || keyCode == KEY_Z || keyCode == KEY_Y)) {
            openList = null;
            if (keyCode == KEY_S) {
                if (page == Page.OBJECTS) {
                    saveObject();
                } else if (page == Page.LIGHT) {
                    saveLight();
                }
            } else {
                undo(keyCode == KEY_Y || (modifiers & MOD_SHIFT) != 0);
            }
            return true;
        }
        if (openList != null && keyCode == KEY_ESCAPE) {
            openList = null;
            return true;
        }
        if (focusedKey == null) {
            if (EditorMovement.matches(EditorKeys.OPEN, keyCode)) {
                close.run();
                return true;
            }
            if (EditorMovement.matches(minecraft.options.keyTogglePerspective, keyCode)) {
                // F5 works here too, to see follow lights on yourself
                minecraft.options.setCameraType(minecraft.options.getCameraType().cycle());
                return true;
            }
            if (EditorMovement.switchScreen(minecraft, keyCode, close)) {
                return true;
            }
            // Movement keys are read every tick; nothing else should react to them
            return EditorMovement.isMovementKey(minecraft, keyCode);
        }
        switch (keyCode) {
            case KEY_ESCAPE -> {
                focusedKey = null;
                focusedCommit = null;
                return true;
            }
            case KEY_TAB -> {
                String completion = completion(focusedKey, fieldBuffer);
                if (completion != null) {
                    fieldBuffer = completion;
                }
                return true;
            }
            case KEY_ENTER, KEY_KP_ENTER -> {
                commitField();
                return true;
            }
            case KEY_BACKSPACE -> {
                if (!fieldBuffer.isEmpty()) {
                    fieldBuffer = (modifiers & MOD_CONTROL) != 0 ? "" : fieldBuffer.substring(0, fieldBuffer.length() - 1);
                    liveSearch();
                }
                return true;
            }
            default -> {
                if ((modifiers & MOD_CONTROL) != 0 && keyCode == KEY_V) {
                    String clipboard = minecraft.keyboardHandler.getClipboard();
                    StringBuilder builder = new StringBuilder(fieldBuffer);
                    clipboard.chars().filter(focusedFilter == null ? c -> true : focusedFilter).forEach(c -> builder.append((char) c));
                    fieldBuffer = builder.length() > 64 ? builder.substring(0, 64) : builder.toString();
                    liveSearch();
                    return true;
                }
                if ((modifiers & MOD_CONTROL) != 0 && keyCode == KEY_A) {
                    fieldBuffer = "";
                    liveSearch();
                    return true;
                }
                // Typing keys arrive through charTyped; keep them from reaching the game
                return true;
            }
        }
    }

    public boolean charTyped(char chr) {
        if (focusedKey == null) {
            return false;
        }
        if ((focusedFilter == null || focusedFilter.test(chr)) && fieldBuffer.length() < 64) {
            fieldBuffer += chr;
            liveSearch();
        }
        return true;
    }

    /** The search box filters while typing. */
    private void liveSearch() {
        if ("search".equals(focusedKey)) {
            objectDraft.search = fieldBuffer;
            objectDraft.pickScroll = 0;
        }
    }

    private static Vec3 lookPoint(Minecraft minecraft) {
        HitResult hit = minecraft.player.pick(32.0d, 0.0f, false);
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = block.getBlockPos().relative(block.getDirection());
            return new Vec3(pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d);
        }
        return minecraft.player.getEyePosition().add(minecraft.player.getViewVector(1.0f).scale(6.0d));
    }

    private static double round(double value) {
        return Math.round(value * 10.0d) / 10.0d;
    }

    private static String formatNumber(double value, double step) {
        return CommandWriter.number(Math.round(value / step) * step);
    }

    // ── Widgets
    // ────────────────────────────────────────────────────

    private abstract static class Widget {
        final String key;
        final int x, y, w, h;
        boolean scrolls = true;
        /** Shown above the mouse while hovering, or null. */
        String tooltip;
        /** Saved lights tab: the group a dragged light joins when dropped on this row, or null. */
        String dropGroup;
        int dropY;

        Widget(String key, int x, int y, int w, int h) {
            this.key = key;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        }

        boolean interactive() {
            return true;
        }

        abstract void render(GuiGraphics graphics, Font font, boolean hovered);

        void press(double mouseX, double mouseY, boolean doubleClick) {
        }

        void startDrag() {
        }

        void drag(double mouseX, double mouseY, double deltaX) {
        }

        void release(boolean dragged) {
        }
    }

    private static final class Label extends Widget {
        private final String text;
        private final int color;

        Label(int x, int y, String text, int color) {
            super("", x, y, 0, 9);
            this.text = text;
            this.color = color;
        }

        @Override
        boolean interactive() {
            return false;
        }

        @Override
        void render(GuiGraphics graphics, Font font, boolean hovered) {
            graphics.drawString(font, text, x, y, color, true);
        }
    }

    private static final class Button extends Widget {
        private final String text;
        private final boolean selected;
        private final Runnable action;

        Button(String key, int x, int y, int w, int h, String text, boolean selected, Runnable action) {
            super(key, x, y, w, h);
            this.text = text;
            this.selected = selected;
            this.action = action;
        }

        @Override
        void render(GuiGraphics graphics, Font font, boolean hovered) {
            graphics.fill(x, y, x + w, y + h, 0xFF000000);
            graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, selected ? COLOR_SELECTED : (hovered ? COLOR_BUTTON_HOVER : COLOR_BUTTON));
            String shown = font.width(text) > w - 4 ? font.plainSubstrByWidth(text, w - 4) : text;
            graphics.drawString(font, shown, x + (w - font.width(shown)) / 2, y + (h - 8) / 2, COLOR_TEXT, true);
        }

        @Override
        void release(boolean dragged) {
            if (!dragged) {
                action.run();
            }
        }
    }

    private static final class Swatch extends Widget {
        private final IntSupplier color;
        private final Runnable action;

        Swatch(String key, int x, int y, int w, int h, IntSupplier color, Runnable action) {
            super(key, x, y, w, h);
            this.color = color;
            this.action = action;
        }

        @Override
        boolean interactive() {
            return action != null;
        }

        @Override
        void render(GuiGraphics graphics, Font font, boolean hovered) {
            graphics.fill(x, y, x + w, y + h, hovered && action != null ? 0xFFFFFFFF : 0xFF000000);
            int value = color.getAsInt();
            if (value == LightColors.PARTY) {
                int stripes = Math.max(1, w - 2);
                for (int i = 0; i < stripes; i++) {
                    int rgb = LightColors.fromHsv(360.0f * i / stripes + (System.currentTimeMillis() % 3000L) * 0.12f, 1.0f, 1.0f);
                    graphics.fill(x + 1 + i, y + 1, x + 2 + i, y + h - 1, 0xFF000000 | rgb);
                }
            } else {
                graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF000000 | value);
            }
        }

        @Override
        void release(boolean dragged) {
            action.run();
        }
    }

    /** Hue by angle, saturation by distance from the centre, at the current brightness. */
    private static final class ColorWheel extends Widget {
        private final Draft draft;

        ColorWheel(int x, int y, Draft draft) {
            super("wheel", x, y, WHEEL_SIZE, WHEEL_SIZE);
            this.draft = draft;
        }

        @Override
        void render(GuiGraphics graphics, Font font, boolean hovered) {
            int radius = w / 2;
            int centerX = x + radius;
            int centerY = y + radius;
            float shade = Math.max(0.3f, draft.brightness);
            for (int dy = -radius; dy < radius; dy += WHEEL_CELL) {
                for (int dx = -radius; dx < radius; dx += WHEEL_CELL) {
                    double cx = dx + WHEEL_CELL / 2.0d;
                    double cy = dy + WHEEL_CELL / 2.0d;
                    double distance = Math.sqrt(cx * cx + cy * cy);
                    if (distance > radius) {
                        continue;
                    }
                    float cellHue = (float) ((Math.toDegrees(Math.atan2(cy, cx)) + 360.0d) % 360.0d);
                    int rgb = LightColors.fromHsv(cellHue, (float) Math.min(1.0d, distance / radius), shade);
                    graphics.fill(centerX + dx, centerY + dy, centerX + dx + WHEEL_CELL, centerY + dy + WHEEL_CELL, 0xFF000000 | rgb);
                }
            }
            if (draft.light.color != LightColors.PARTY) {
                double angle = Math.toRadians(draft.hue);
                int markerX = centerX + (int) Math.round(Math.cos(angle) * draft.saturation * (radius - 1));
                int markerY = centerY + (int) Math.round(Math.sin(angle) * draft.saturation * (radius - 1));
                graphics.fill(markerX - 2, markerY - 2, markerX + 3, markerY + 3, 0xFF000000);
                graphics.fill(markerX - 1, markerY - 1, markerX + 2, markerY + 2, 0xFFFFFFFF);
            }
        }

        @Override
        boolean contains(double mouseX, double mouseY) {
            double radius = w / 2.0d;
            double dx = mouseX - (x + radius);
            double dy = mouseY - (y + radius);
            return dx * dx + dy * dy <= (radius + 1) * (radius + 1);
        }

        @Override
        void press(double mouseX, double mouseY, boolean doubleClick) {
            pick(mouseX, mouseY);
        }

        @Override
        void drag(double mouseX, double mouseY, double deltaX) {
            pick(mouseX, mouseY);
        }

        private void pick(double mouseX, double mouseY) {
            double radius = w / 2.0d;
            double dx = mouseX - (x + radius);
            double dy = mouseY - (y + radius);
            float newHue = (float) ((Math.toDegrees(Math.atan2(dy, dx)) + 360.0d) % 360.0d);
            float newSaturation = (float) Math.min(1.0d, Math.sqrt(dx * dx + dy * dy) / radius);
            float value = draft.light.color == LightColors.PARTY || draft.brightness <= 0.0f ? 1.0f : draft.brightness;
            draft.setHsv(Math.round(newHue), Math.round(newSaturation * 100.0f) / 100.0f, value);
        }
    }

    /** Vertical brightness bar next to the wheel. */
    private static final class BrightnessBar extends Widget {
        private final Draft draft;

        BrightnessBar(int x, int y, int w, int h, Draft draft) {
            super("brightness_bar", x, y, w, h);
            this.draft = draft;
        }

        @Override
        void render(GuiGraphics graphics, Font font, boolean hovered) {
            int top = 0xFF000000 | LightColors.fromHsv(draft.hue, draft.saturation, 1.0f);
            graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, hovered ? 0xFFFFFFFF : 0xFF000000);
            graphics.fillGradient(x, y, x + w, y + h, top, 0xFF000000);
            int markerY = y + Math.round((1.0f - draft.brightness) * (h - 1));
            graphics.fill(x - 2, markerY - 1, x + w + 2, markerY + 2, 0xFF000000);
            graphics.fill(x - 1, markerY, x + w + 1, markerY + 1, 0xFFFFFFFF);
        }

        @Override
        void press(double mouseX, double mouseY, boolean doubleClick) {
            pick(mouseY);
        }

        @Override
        void drag(double mouseX, double mouseY, double deltaX) {
            pick(mouseY);
        }

        private void pick(double mouseY) {
            float value = (float) Math.max(0.0d, Math.min(1.0d, 1.0d - (mouseY - y) / (h - 1)));
            draft.setHsv(draft.hue, draft.saturation, Math.round(value * 100.0f) / 100.0f);
        }
    }

    /**
     * Drag to change the value relative to where it was (the soft range spans the slider width; Shift = fine, Ctrl =
     * coarse); double click to type a value, which may go past the soft range up to the hard limits.
     */
    private final class Slider extends Widget {
        private final String label, unit;
        private final double softMin, softMax, min, max, step;
        /** Sizes change in proportion to themselves, so small values are as easy to set as big ones. */
        private final boolean proportional;
        private final DoubleSupplier getter;
        private final DoubleConsumer setter;
        private double dragStart;

        Slider(String key, int x, int y, int w, int h, String label, String unit, double softMin, double softMax, double min, double max,
               double step, DoubleSupplier getter, DoubleConsumer setter) {
            super(key, x, y, w, h);
            this.label = label;
            this.unit = unit;
            this.softMin = softMin;
            this.softMax = softMax;
            this.min = min;
            this.max = max;
            this.step = step;
            this.getter = getter;
            this.setter = setter;
            this.proportional = SIZE_SLIDERS.contains(key);
        }

        @Override
        void render(GuiGraphics graphics, Font font, boolean hovered) {
            if (key.equals(focusedKey)) {
                renderField(graphics, font, x, y, w, h, fieldBuffer, true, label + ": ");
                return;
            }
            double value = getter.getAsDouble();
            graphics.fill(x, y, x + w, y + h, 0xFF000000);
            graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, COLOR_TRACK);
            double t = proportional
                    ? Math.max(0.0d, Math.min(1.0d, Math.log1p(Math.max(0.0d, value) * 4.0d) / Math.log1p(softMax * 4.0d)))
                    : Math.max(0.0d, Math.min(1.0d, (value - softMin) / (softMax - softMin)));
            int filled = (int) Math.round((w - 2) * t);
            graphics.fill(x + 1, y + 1, x + 1 + filled, y + h - 1, hovered || pressed == this ? COLOR_FILL_HOVER : COLOR_FILL);
            String text = label + ": " + formatNumber(value, step) + unit;
            String shown = font.width(text) > w - 4 ? font.plainSubstrByWidth(text, w - 4) : text;
            graphics.drawString(font, shown, x + (w - font.width(shown)) / 2, y + (h - 8) / 2, COLOR_TEXT, true);
        }

        @Override
        void press(double mouseX, double mouseY, boolean doubleClick) {
            if (doubleClick) {
                focus(key, formatNumber(getter.getAsDouble(), step), text -> {
                    try {
                        set(Double.parseDouble(text));
                    } catch (NumberFormatException ignored) {
                        setStatus("Not a number", COLOR_ERROR);
                    }
                }, NUMBER_CHARS);
            }
        }

        @Override
        void startDrag() {
            dragStart = getter.getAsDouble();
        }

        @Override
        void drag(double mouseX, double mouseY, double deltaX) {
            if (proportional) {
                // The whole width multiplies the value by about 150 (Shift: much finer)
                double scale = Math.min(2.0d, modifierScale());
                set((dragStart + 0.05d) * Math.exp(deltaX / Math.max(1, w - 2) * 5.0d * scale) - 0.05d);
                return;
            }
            double perPixel = (softMax - softMin) / Math.max(1, w - 2);
            set(dragStart + deltaX * perPixel * modifierScale());
        }

        private void set(double value) {
            double snapped = Math.round(value / step) * step;
            setter.accept(Math.max(min, Math.min(max, snapped)));
        }
    }

    /** A number box: drag sideways to change it (Shift = fine, Ctrl = coarse), click to type it. */
    private final class NumberField extends Widget {
        private final String prefix;
        private final DoubleSupplier getter;
        private final DoubleConsumer setter;
        private final double perPixel;
        private final String special;
        private double dragStart;

        NumberField(String key, int x, int y, int w, int h, String prefix, DoubleSupplier getter, DoubleConsumer setter, double perPixel, String special) {
            super(key, x, y, w, h);
            this.prefix = prefix;
            this.getter = getter;
            this.setter = setter;
            this.perPixel = perPixel;
            this.special = special;
        }

        @Override
        void render(GuiGraphics graphics, Font font, boolean hovered) {
            boolean focused = key.equals(focusedKey);
            String text = focused ? fieldBuffer : special != null ? special : CommandWriter.number(getter.getAsDouble());
            renderField(graphics, font, x, y, w, h, text, focused, prefix);
            if (hovered && !focused) {
                graphics.drawString(font, "↔", x + w - 9, y + 3, COLOR_DIM, true);
            }
        }

        @Override
        void startDrag() {
            dragStart = getter.getAsDouble();
        }

        @Override
        void drag(double mouseX, double mouseY, double deltaX) {
            double value = dragStart + deltaX * perPixel * modifierScale();
            setter.accept(Math.round(value * 100.0d) / 100.0d);
        }

        @Override
        void release(boolean dragged) {
            if (!dragged && !key.equals(focusedKey)) {
                focus(key, special != null ? "" : CommandWriter.number(getter.getAsDouble()), text -> {
                    if (text.isEmpty() && special != null) {
                        return;
                    }
                    try {
                        setter.accept(Double.parseDouble(text));
                    } catch (NumberFormatException ignored) {
                        setStatus("Not a number", COLOR_ERROR);
                    }
                }, NUMBER_CHARS);
            }
        }
    }

    private final class TextField extends Widget {
        private final String prefix;
        private final Supplier<String> getter;
        private final Consumer<String> setter;
        private final IntPredicate filter;

        TextField(String key, int x, int y, int w, int h, String prefix, Supplier<String> getter, Consumer<String> setter, IntPredicate filter) {
            super(key, x, y, w, h);
            this.prefix = prefix;
            this.getter = getter;
            this.setter = setter;
            this.filter = filter;
        }

        @Override
        void render(GuiGraphics graphics, Font font, boolean hovered) {
            boolean focused = key.equals(focusedKey);
            renderField(graphics, font, x, y, w, h, focused ? fieldBuffer : getter.get(), focused, prefix);
            String completion = focused ? completion(key, fieldBuffer) : null;
            if (completion != null) {
                // The rest of the suggestion in grey after the typed text (Tab takes it)
                int start = x + 3 + font.width(prefix) + font.width(fieldBuffer) + 1;
                String rest = completion.substring(fieldBuffer.length());
                if (start + font.width(rest) <= x + w - 3) {
                    graphics.drawString(font, rest, start, y + 3, COLOR_DIM, false);
                }
            }
        }

        @Override
        void release(boolean dragged) {
            if (!key.equals(focusedKey)) {
                focus(key, getter.get(), setter, filter);
            }
        }
    }

    /** Scrollable list of registry ids with icons; a click picks the object (and loads it if it was saved). */
    private final class Picker extends Widget {
        private final List<String> ids;
        private final Draft draft;

        Picker(int x, int y, int w, int h, List<String> ids, Draft draft) {
            super("picker", x, y, w, h);
            this.ids = ids;
            this.draft = draft;
        }

        @Override
        void render(GuiGraphics graphics, Font font, boolean hovered) {
            graphics.fill(x, y, x + w, y + h, COLOR_FIELD_BORDER);
            graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, COLOR_FIELD);
            int maxFirst = Math.max(0, ids.size() - PICK_ROWS);
            draft.pickScroll = Math.max(0, Math.min(maxFirst, draft.pickScroll));
            if (ids.isEmpty()) {
                graphics.drawString(font, "Nothing found", x + 4, y + 4, COLOR_DIM, true);
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            double mouseY = minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getScreenHeight();
            for (int row = 0; row < PICK_ROWS && draft.pickScroll + row < ids.size(); row++) {
                String id = ids.get(draft.pickScroll + row);
                int rowY = y + 1 + row * PICK_ROW;
                boolean selected = id.equals(draft.target);
                boolean rowHovered = hovered && mouseY >= rowY && mouseY < rowY + PICK_ROW;
                if (selected || rowHovered) {
                    graphics.fill(x + 1, rowY, x + w - 1, rowY + PICK_ROW, selected ? COLOR_SELECTED : COLOR_BUTTON_HOVER);
                }
                ItemStack icon = iconOf(draft.kind, id);
                if (!icon.isEmpty()) {
                    if (draft.kind == ObjectLights.Kind.ITEM && selected && draft.customModelData >= 0) {
                        com.cotii.customlights.client.compat.Compat.setCustomModelData(icon, draft.customModelData);
                    }
                    graphics.renderFakeItem(icon, x + 2, rowY);
                }
                String name = cachedIds.contains(id) ? shortId(id) : "+ Use " + id;
                graphics.drawString(font, font.plainSubstrByWidth(name, w - 26), x + 22, rowY + 4, cachedIds.contains(id) ? COLOR_TEXT : COLOR_OK, true);
            }
            if (ids.size() > PICK_ROWS) {
                int barHeight = Math.max(6, h * PICK_ROWS / ids.size());
                int barY = y + (int) ((h - barHeight) * (draft.pickScroll / (double) maxFirst));
                graphics.fill(x + w - 3, barY, x + w - 1, barY + barHeight, 0xC0B080FF);
            }
        }

        @Override
        void press(double mouseX, double mouseY, boolean doubleClick) {
            int row = (int) ((mouseY - y - 1) / PICK_ROW);
            int index = draft.pickScroll + row;
            if (row >= 0 && row < PICK_ROWS && index < ids.size()) {
                String before = draft.target;
                draft.target = ids.get(index);
                ObjectLights.Rule saved = ObjectLights.rule(ObjectLights.keyOf(draft.kind, draft.target, draft.customModelData));
                if (saved != null && !saved.light.removing) {
                    editObject(saved);
                } else if (draft.kind == ObjectLights.Kind.SPECIAL && !draft.target.equals(before)) {
                    // Beams and glows start from their own look
                    resetObjectLight(draft);
                }
            }
        }

        void scroll(double amount) {
            draft.pickScroll = Math.max(0, draft.pickScroll - (int) Math.signum(amount) * 2);
        }
    }

    private static void renderField(GuiGraphics graphics, Font font, int x, int y, int w, int h, String text, boolean focused, String prefix) {
        graphics.fill(x, y, x + w, y + h, focused ? COLOR_FOCUS : COLOR_FIELD_BORDER);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, COLOR_FIELD);
        int prefixWidth = font.width(prefix);
        graphics.drawString(font, prefix, x + 3, y + 3, COLOR_DIM, true);
        String shown = text;
        int room = w - 8 - prefixWidth;
        while (font.width(shown) > room && !shown.isEmpty()) {
            shown = focused ? shown.substring(1) : shown.substring(0, shown.length() - 1);
        }
        graphics.drawString(font, shown, x + 3 + prefixWidth, y + 3, COLOR_TEXT, true);
        if (focused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            int caretX = x + 3 + prefixWidth + font.width(shown);
            graphics.fill(caretX, y + 2, caretX + 1, y + h - 2, 0xFFE8D8FF);
        }
    }

    /** A drop-down list drawn over everything; a click anywhere closes it. */
    private final class OptionList {
        private final int x, y, w;
        private final List<String> options;
        private final int selected;
        private final IntConsumer onSelect;
        /** Rows drawn in yellow: shapes that bring their own animation. */
        private final java.util.function.IntPredicate marked;
        private int first;
        private final int visibleRows;

        OptionList(int x, int y, int w, List<String> options, int selected, IntConsumer onSelect) {
            this(x, y, w, options, selected, onSelect, null);
        }

        OptionList(int x, int y, int w, List<String> options, int selected, IntConsumer onSelect, java.util.function.IntPredicate marked) {
            this.marked = marked;
            this.w = w;
            this.options = options;
            this.selected = selected;
            this.onSelect = onSelect;
            this.visibleRows = Math.max(1, Math.min(options.size(), (height - BOTTOM_HEIGHT - 4 - y) / LIST_ROW));
            this.x = x;
            this.y = y;
        }

        void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
            int listHeight = visibleRows * LIST_ROW + 2;
            graphics.fill(x - 1, y - 1, x + w + 1, y + listHeight + 1, 0xFF000000);
            graphics.fill(x, y, x + w, y + listHeight, COLOR_LIST);
            for (int row = 0; row < visibleRows && first + row < options.size(); row++) {
                int index = first + row;
                int rowY = y + 1 + row * LIST_ROW;
                boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= rowY && mouseY < rowY + LIST_ROW;
                if (index == selected || hovered) {
                    graphics.fill(x + 1, rowY, x + w - 1, rowY + LIST_ROW, index == selected ? COLOR_SELECTED : COLOR_BUTTON_HOVER);
                }
                int color = marked != null && marked.test(index) ? COLOR_ANIMATED : COLOR_TEXT;
                graphics.drawString(font, font.plainSubstrByWidth(options.get(index), w - 6), x + 4, rowY + 2, color, true);
            }
        }

        void click(double mouseX, double mouseY) {
            if (mouseX < x || mouseX >= x + w || mouseY < y) {
                return;
            }
            int row = (int) ((mouseY - y - 1) / LIST_ROW);
            if (row >= 0 && row < visibleRows && first + row < options.size()) {
                onSelect.accept(first + row);
            }
        }

        void scroll(double amount) {
            first = Math.max(0, Math.min(options.size() - visibleRows, first - (int) Math.signum(amount)));
        }
    }
}
