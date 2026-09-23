package com.cotii.customlights.client.gui;

import com.cotii.customlights.Customlights;
import com.cotii.customlights.client.light.ObjectLights;
import com.cotii.customlights.client.storage.LightStorage;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.Util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Presets of object lights: named lists of items, blocks, entities... */
public final class ObjectPresets {
    /** A preset as listed: its name, how many objects it has and whether the server shares it. */
    record Summary(String name, int count, boolean server) {
    }

    static final String SERVER_PREFIX = "server/";
    private static final Map<String, JsonObject> SERVER = new LinkedHashMap<>();

    public static void serverFull(Map<String, JsonObject> presets) {
        SERVER.clear();
        SERVER.putAll(presets);
        changed();
    }

    public static void putFromServer(String name, JsonObject preset) {
        SERVER.put(name, preset);
        changed();
    }

    public static void removeFromServer(String name) {
        SERVER.remove(name);
        changed();
    }

    public static void clearServer() {
        SERVER.clear();
        changed();
    }

    static boolean isServer(String name) {
        return name.startsWith(SERVER_PREFIX);
    }

    /** A saved preset as JSON (to share it), or null. */
    public static JsonObject json(String name) {
        return isServer(name) ? SERVER.get(name.substring(SERVER_PREFIX.length())) : LightStorage.loadPreset(name);
    }

    private static final long REFRESH_MS = 2000L;
    private static List<Summary> cached = List.of();
    private static long cachedAt;

    private ObjectPresets() {
    }

    static List<Summary> list() {
        long now = System.currentTimeMillis();
        if (now - cachedAt > REFRESH_MS) {
            List<Summary> summaries = new ArrayList<>();
            for (String name : LightStorage.presetNames()) {
                summaries.add(new Summary(name, read(name).size(), false));
            }
            for (String name : SERVER.keySet()) {
                summaries.add(new Summary(SERVER_PREFIX + name, read(SERVER_PREFIX + name).size(), true));
            }
            cached = summaries;
            cachedAt = now;
        }
        return cached;
    }

    private static String contentsName;
    private static List<ObjectLights.Rule> contents = List.of();
    private static long contentsAt;

    /** The objects of a preset, re-read at most every two seconds. */
    static List<ObjectLights.Rule> contents(String name) {
        long now = System.currentTimeMillis();
        if (!name.equals(contentsName) || now - contentsAt > REFRESH_MS) {
            contents = new ArrayList<>(read(name).values());
            contentsName = name;
            contentsAt = now;
        }
        return contents;
    }

    private static void changed() {
        cachedAt = 0L;
        contentsAt = 0L;
    }

    /** The rules of a preset by key (empty when it does not exist). */
    static Map<String, ObjectLights.Rule> read(String name) {
        Map<String, ObjectLights.Rule> rules = new LinkedHashMap<>();
        JsonObject root = json(name);
        if (root == null || !root.has("objects")) {
            return rules;
        }
        for (JsonElement element : root.getAsJsonArray("objects")) {
            try {
                ObjectLights.Rule rule = ObjectLights.Rule.fromJson(element.getAsJsonObject());
                rules.put(rule.key(), rule);
            } catch (RuntimeException exception) {
                Customlights.LOGGER.warn("Skipping a broken object in preset {}", name, exception);
            }
        }
        return rules;
    }

    private static void write(String name, Iterable<ObjectLights.Rule> rules) {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("name", name);
        JsonArray array = new JsonArray();
        for (ObjectLights.Rule rule : rules) {
            array.add(rule.toJson());
        }
        root.add("objects", array);
        LightStorage.savePreset(name, root);
        changed();
    }

    /** Saves every saved object light as the preset (replacing it); returns how many. */
    static int saveAll(String name) {
        List<ObjectLights.Rule> rules = new ArrayList<>();
        for (ObjectLights.Rule rule : ObjectLights.rules()) {
            if (!rule.light.removing) {
                rules.add(rule);
            }
        }
        write(name, rules);
        return rules.size();
    }

    /** Adds (or updates) one object in the preset, creating it if needed; returns its new size. */
    static int add(String name, ObjectLights.Rule rule) {
        if (isServer(name)) {
            return read(name).size();
        }
        Map<String, ObjectLights.Rule> rules = read(name);
        rules.put(rule.key(), rule);
        write(name, rules.values());
        return rules.size();
    }

    /** Removes one object from the preset; false when it was not there. */
    static boolean removeObject(String name, String key) {
        if (isServer(name)) {
            return false;
        }
        Map<String, ObjectLights.Rule> rules = read(name);
        if (rules.remove(key) == null) {
            return false;
        }
        write(name, rules.values());
        return true;
    }

    /** Puts every object of the preset into the object lights; returns how many. */
    static int load(String name) {
        Map<String, ObjectLights.Rule> rules = read(name);
        for (ObjectLights.Rule rule : rules.values()) {
            ObjectLights.put(rule);
        }
        ObjectLights.saveIfDirty();
        return rules.size();
    }

    /** Takes the preset's objects out of the object lights; returns how many were there. */
    static int unload(String name) {
        int removed = 0;
        for (String key : read(name).keySet()) {
            if (ObjectLights.remove(key, null)) {
                removed++;
            }
        }
        ObjectLights.saveIfDirty();
        return removed;
    }

    static boolean delete(String name) {
        if (isServer(name)) {
            return false;
        }
        boolean deleted = LightStorage.deletePreset(name);
        changed();
        return deleted;
    }

    static void openFolder() {
        java.io.File folder = LightStorage.presetsFolder().toFile();
        folder.mkdirs();
        Util.getPlatform().openFile(folder);
    }
}
