package com.cotii.customlights.client.light;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/** Light markers inside JSON models (item models, ModelEngine bone items). */
public final class ModelMarkers {
    /** A marker in model space (blocks, 0..1 is the model box). */
    public record Marker(String template, float x, float y, float z, int key) {
    }

    /** Markers of a baked model; {@code hideModel} when the whole model stands for the light. */
    public record Entry(List<Marker> markers, boolean hideModel) {
    }

    private static final Map<Object, Entry> BAKED = new ConcurrentHashMap<>();

    private ModelMarkers() {
    }

    public static Entry get(Object model) {
        return BAKED.isEmpty() ? null : BAKED.get(model);
    }

    public static void put(Object model, Entry entry) {
        BAKED.put(model, entry);
    }

    /** Before models are baked again (resource reload). */
    public static void clear() {
        BAKED.clear();
    }

    /** Reads the Blockbench {@code groups} of a model JSON. */
    public static List<Marker> readGroups(JsonObject model, Set<Integer> removedElements) {
        List<Marker> markers = new ArrayList<>();
        if (model == null || !model.has("groups") || !model.get("groups").isJsonArray()) {
            return markers;
        }
        readChildren(model.getAsJsonArray("groups"), null, markers, removedElements);
        return markers;
    }

    private static void readChildren(JsonArray children, String insideTemplate, List<Marker> markers, Set<Integer> removed) {
        for (JsonElement child : children) {
            if (child.isJsonPrimitive() && child.getAsJsonPrimitive().isNumber()) {
                if (insideTemplate != null) {
                    removed.add(child.getAsInt());
                }
                continue;
            }
            if (!child.isJsonObject()) {
                continue;
            }
            JsonObject group = child.getAsJsonObject();
            String name = group.has("name") ? group.get("name").getAsString() : "";
            String template = MarkerLights.templateOf(name);
            String inside = insideTemplate;
            if (template != null && insideTemplate == null) {
                float x = 8.0f, y = 8.0f, z = 8.0f;
                if (group.has("origin") && group.get("origin").isJsonArray() && group.getAsJsonArray("origin").size() == 3) {
                    JsonArray origin = group.getAsJsonArray("origin");
                    x = origin.get(0).getAsFloat();
                    y = origin.get(1).getAsFloat();
                    z = origin.get(2).getAsFloat();
                }
                markers.add(new Marker(template, x / 16.0f, y / 16.0f, z / 16.0f, markers.size()));
                inside = template;
            }
            if (group.has("children") && group.get("children").isJsonArray()) {
                readChildren(group.getAsJsonArray("children"), inside, markers, removed);
            }
        }
    }

    /** Keeps the elements whose index is not in {@code removed}. */
    public static <T> List<T> without(List<T> elements, Set<Integer> removed) {
        if (removed.isEmpty()) {
            return elements;
        }
        List<T> kept = new ArrayList<>(elements.size());
        for (int i = 0; i < elements.size(); i++) {
            if (!removed.contains(i)) {
                kept.add(elements.get(i));
            }
        }
        return kept;
    }

    public static Set<Integer> newIndexSet() {
        return new TreeSet<>();
    }
}
