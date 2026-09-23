package com.cotii.customlights.client.light;

import com.cotii.customlights.client.storage.LightStorage;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Groups of saved lights in a world: a name, whether its lights are on and whether they animate. */
public final class LightGroups {
    public static final class Group {
        public String name;
        public boolean enabled = true;
        public boolean animations = true;
        /** Editor only: whether its lights are listed. */
        public boolean collapsed;

        Group(String name) {
            this.name = name;
        }
    }

    private static final Map<String, Group> GROUPS = new LinkedHashMap<>();
    /**
     * Groups of the server's lights, chosen on this client (the server resends its lights, so their group is kept here by
     * light id; "" means no group).
     */
    private static final Map<String, String> SERVER_MEMBERS = new LinkedHashMap<>();
    private static boolean allEnabled = true;
    private static String worldKey;
    private static boolean dirty;

    private LightGroups() {
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public static List<Group> groups() {
        return new ArrayList<>(GROUPS.values());
    }

    public static Group get(String name) {
        return name == null || name.isEmpty() ? null : GROUPS.get(key(name));
    }

    public static List<String> names() {
        List<String> names = new ArrayList<>();
        for (Group group : GROUPS.values()) {
            names.add(group.name);
        }
        return names;
    }

    /** Creates the group if it does not exist yet; returns it. */
    public static Group add(String name) {
        Group group = GROUPS.computeIfAbsent(key(name), ignored -> new Group(name));
        changed();
        return group;
    }

    /** Deletes a group; its lights stay, without a group. */
    public static boolean remove(String name) {
        Group group = GROUPS.remove(key(name));
        if (group == null) {
            return false;
        }
        for (Light light : LightManager.groupableLights()) {
            if (key(light.group).equals(key(name))) {
                light.group = "";
            }
        }
        SERVER_MEMBERS.replaceAll((id, member) -> key(member).equals(key(name)) ? "" : member);
        LightManager.markDirty();
        changed();
        return true;
    }

    public static boolean rename(String name, String newName) {
        Group group = GROUPS.remove(key(name));
        if (group == null || newName.isBlank() || GROUPS.containsKey(key(newName))) {
            if (group != null) {
                GROUPS.put(key(name), group);
            }
            return false;
        }
        group.name = newName;
        GROUPS.put(key(newName), group);
        for (Light light : LightManager.groupableLights()) {
            if (key(light.group).equals(key(name))) {
                light.group = newName;
            }
        }
        SERVER_MEMBERS.replaceAll((id, member) -> key(member).equals(key(name)) ? newName : member);
        LightManager.markDirty();
        changed();
        return true;
    }

    /** Puts a light in a group ("" for none), creating the group if needed. */
    public static void assign(Light light, String name) {
        if (name == null || name.isBlank()) {
            light.group = "";
        } else {
            light.group = add(name).name;
        }
        if (light.server) {
            SERVER_MEMBERS.put(key(light.id), light.group);
            changed();
        }
        LightManager.markDirty();
    }

    public static void setEnabled(Group group, boolean enabled) {
        group.enabled = enabled;
        changed();
    }

    public static void setAnimations(Group group, boolean animations) {
        group.animations = animations;
        changed();
    }

    public static boolean allEnabled() {
        return allEnabled;
    }

    public static void setAllEnabled(boolean enabled) {
        allEnabled = enabled;
        changed();
    }

    /** Whether a saved light is drawn now (the world switch and its group). */
    public static boolean shows(Light light) {
        if (!allEnabled) {
            return false;
        }
        Group group = get(light.group);
        return group == null || group.enabled;
    }

    /** Whether a saved light plays its animations now. */
    public static boolean animates(Light light) {
        Group group = get(light.group);
        return group == null || group.animations;
    }

    /** The server turns a group on or off (or its animations): the group is made here if it does not exist yet. */
    public static void applyFromServer(String name, Boolean enabled, Boolean animations) {
        if (name == null || name.isBlank()) {
            return;
        }
        Group group = add(name);
        if (enabled != null) {
            group.enabled = enabled;
        }
        if (animations != null) {
            group.animations = animations;
        }
        changed();
    }

    /** A light arrived from the server: it goes back to the group chosen here for it, if any. */
    public static void applyServerGroup(Light light) {
        String member = SERVER_MEMBERS.get(key(light.id));
        if (member != null) {
            light.group = member;
        }
        if (!light.group.isEmpty()) {
            // The group the server named is listed here right away
            add(light.group);
        }
    }

    public static void markChanged() {
        changed();
    }

    private static void changed() {
        dirty = true;
        LightManager.markDirty();
    }

    // ── Storage
    // ────────────────────────────────────────────────────

    static void load(String key) {
        worldKey = key;
        GROUPS.clear();
        allEnabled = true;
        dirty = false;
        SERVER_MEMBERS.clear();
        JsonObject root = key == null ? null : LightStorage.loadGroups(key);
        if (root == null) {
            return;
        }
        allEnabled = !root.has("enabled") || root.get("enabled").getAsBoolean();
        if (root.has("groups")) {
            for (JsonElement element : root.getAsJsonArray("groups")) {
                JsonObject object = element.getAsJsonObject();
                Group group = new Group(object.get("name").getAsString());
                group.enabled = !object.has("enabled") || object.get("enabled").getAsBoolean();
                group.animations = !object.has("animations") || object.get("animations").getAsBoolean();
                group.collapsed = object.has("collapsed") && object.get("collapsed").getAsBoolean();
                GROUPS.put(key(group.name), group);
            }
        }
        if (root.has("server")) {
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("server").entrySet()) {
                SERVER_MEMBERS.put(key(entry.getKey()), entry.getValue().getAsString());
            }
        }
    }

    static void saveIfDirty() {
        if (!dirty || worldKey == null) {
            return;
        }
        dirty = false;
        JsonObject root = new JsonObject();
        root.addProperty("enabled", allEnabled);
        JsonArray array = new JsonArray();
        for (Group group : GROUPS.values()) {
            JsonObject object = new JsonObject();
            object.addProperty("name", group.name);
            object.addProperty("enabled", group.enabled);
            object.addProperty("animations", group.animations);
            if (group.collapsed) {
                object.addProperty("collapsed", true);
            }
            array.add(object);
        }
        root.add("groups", array);
        if (!SERVER_MEMBERS.isEmpty()) {
            JsonObject server = new JsonObject();
            SERVER_MEMBERS.forEach(server::addProperty);
            root.add("server", server);
        }
        LightStorage.saveGroups(worldKey, root);
    }

    static void clear() {
        saveIfDirty();
        worldKey = null;
        GROUPS.clear();
        SERVER_MEMBERS.clear();
        allEnabled = true;
    }
}
