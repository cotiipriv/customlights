package com.cotii.customlights.client.gui;

import com.cotii.customlights.Customlights;
import com.cotii.customlights.client.command.LightPaste;
import com.cotii.customlights.client.light.Light;
import com.cotii.customlights.client.light.ObjectLights;
import com.cotii.customlights.client.storage.LightStorage;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lights and object lights the editor added, overwrote or deleted, newest first, so a mistake can still be undone after
 * closing the editor or the game (like Custom Waypoints).
 */
final class EditHistory {
    static final int LIMIT = 8;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM").withZone(ZoneId.systemDefault());

    enum Action {
        ADDED,
        UPDATED,
        DELETED
    }

    enum Target {
        LIGHT,
        OBJECT
    }

    /** One change. */
    record Entry(Action action, String name, long time, Target target, String type, JsonObject saved) {
        Light light() {
            Light light = Light.fromJson(saved.deepCopy());
            light.age = light.fadeIn;
            return light;
        }

        ObjectLights.Rule rule() {
            return ObjectLights.Rule.fromJson(saved.deepCopy());
        }
    }

    private static List<Entry> entries;

    private EditHistory() {
    }

    static List<Entry> entries() {
        load();
        return Collections.unmodifiableList(entries);
    }

    static void light(Action action, String type, Light light) {
        String name = type.equals("flashlight") ? "flashlight" : light.id;
        add(new Entry(action, name, System.currentTimeMillis(), Target.LIGHT, type, light.toJson()));
    }

    static void object(Action action, String name, ObjectLights.Rule rule) {
        add(new Entry(action, name, System.currentTimeMillis(), Target.OBJECT, rule.key(), rule.toJson()));
    }

    private static void add(Entry entry) {
        load();
        entries.add(0, entry);
        while (entries.size() > LIMIT) {
            entries.remove(entries.size() - 1);
        }
        save();
    }

    /** Saves the entry's light (or object) back. */
    static void restore(Entry entry) {
        if (entry.target() == Target.LIGHT) {
            LightPaste.apply(entry.light(), entry.type());
        } else {
            ObjectLights.put(entry.rule());
            ObjectLights.saveIfDirty();
        }
    }

    /** The time for entries of today, the day for older ones. */
    static String time(long time) {
        Instant instant = Instant.ofEpochMilli(time);
        boolean today = instant.atZone(ZoneId.systemDefault()).toLocalDate().equals(LocalDate.now());
        return (today ? CLOCK : DAY).format(instant);
    }

    private static void load() {
        if (entries != null) {
            return;
        }
        entries = new ArrayList<>();
        JsonObject root = LightStorage.loadHistory();
        if (root == null || !root.has("entries")) {
            return;
        }
        for (JsonElement element : root.getAsJsonArray("entries")) {
            try {
                JsonObject object = element.getAsJsonObject();
                if (!object.has("saved")) {
                    // Older format without the saved light: nothing to restore
                    continue;
                }
                entries.add(new Entry(Action.valueOf(object.get("action").getAsString()), object.get("name").getAsString(),
                        object.get("time").getAsLong(), Target.valueOf(object.get("target").getAsString()), object.get("type").getAsString(),
                        object.getAsJsonObject("saved")));
            } catch (RuntimeException exception) {
                Customlights.LOGGER.warn("Skipping a broken history entry", exception);
            }
        }
        while (entries.size() > LIMIT) {
            entries.remove(entries.size() - 1);
        }
    }

    private static void save() {
        JsonArray array = new JsonArray();
        for (Entry entry : entries) {
            JsonObject object = new JsonObject();
            object.addProperty("action", entry.action().name());
            object.addProperty("name", entry.name());
            object.addProperty("time", entry.time());
            object.addProperty("target", entry.target().name());
            object.addProperty("type", entry.type());
            object.add("saved", entry.saved());
            array.add(object);
        }
        JsonObject root = new JsonObject();
        root.add("entries", array);
        LightStorage.saveHistory(root);
    }
}
