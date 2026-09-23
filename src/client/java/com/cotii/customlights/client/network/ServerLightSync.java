package com.cotii.customlights.client.network;

import com.cotii.customlights.Customlights;
import com.cotii.customlights.client.light.Light;
import com.cotii.customlights.client.light.LightManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/** Applies the JSON operations the CustomLights server plugin sends on {@code customlights:sync}. */
public final class ServerLightSync {
    public static final int MAX_JSON_LENGTH = SyncNetwork.MAX_JSON_LENGTH;

    private ServerLightSync() {
    }

    public static void handle(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            String operation = string(root, "op", "full");
            switch (operation) {
                case "full" -> {
                    List<Light> lights = new ArrayList<>();
                    for (JsonElement element : array(root, "lights")) {
                        lights.add(readLight(element.getAsJsonObject()));
                    }
                    List<Light> templates = new ArrayList<>();
                    for (JsonElement element : array(root, "models")) {
                        templates.add(readLight(element.getAsJsonObject()));
                    }
                    LightManager.serverFull(lights, templates);
                    List<com.cotii.customlights.client.light.ObjectLights.Rule> rules = new ArrayList<>();
                    for (JsonElement element : array(root, "objects")) {
                        rules.add(com.cotii.customlights.client.light.ObjectLights.Rule.fromJson(element.getAsJsonObject()));
                    }
                    com.cotii.customlights.client.light.ObjectLights.serverFull(rules);
                    java.util.Map<String, JsonObject> presets = new java.util.LinkedHashMap<>();
                    for (JsonElement element : array(root, "presets")) {
                        JsonObject preset = element.getAsJsonObject();
                        presets.put(string(preset, "name", "preset"), preset.getAsJsonObject("preset"));
                    }
                    com.cotii.customlights.client.gui.ObjectPresets.serverFull(presets);
                }
                case "upsert" -> LightManager.putFromServer(readLight(root.getAsJsonObject("light")));
                case "remove" -> LightManager.removeFromServer(string(root, "id", ""), fadeOut(root));
                case "remove_all" -> LightManager.removeAllFromServer(fadeOut(root));
                case "move" -> move(root);
                case "animate" -> {
                    Light light = LightManager.serverLight(string(root, "id", ""));
                    if (light == null) {
                        light = LightManager.getTemplate(string(root, "id", "")).orElse(null);
                    }
                    if (light != null && root.has("animation")) {
                        light.animation.read(root.getAsJsonObject("animation"));
                    }
                }
                case "targets" -> {
                    Light light = LightManager.serverLight(string(root, "id", ""));
                    if (light != null) {
                        JsonObject holder = new JsonObject();
                        holder.add("targetIds", array(root, "targetIds"));
                        light.targetIds = Light.fromJson(holder).targetIds;
                    }
                }
                case "model" -> {
                    Light template = readLight(root.getAsJsonObject("model"));
                    LightManager.getTemplate(template.id).ifPresent(existing -> template.age = template.fadeIn);
                    LightManager.putTemplateFromServer(template);
                }
                case "model_remove" -> LightManager.removeTemplateFromServer(string(root, "id", ""), fadeOut(root));
                case "model_remove_all" -> LightManager.removeAllTemplatesFromServer(fadeOut(root));
                case "flashlight" -> flashlight(root);
                case "flashlight_target" -> flashlightTarget(root);
                case "hello" -> ServerEdits.onHello(root.has("editor") && root.get("editor").getAsBoolean(), string(root, "version", ""));
                case "object" -> com.cotii.customlights.client.light.ObjectLights.putFromServer(
                        com.cotii.customlights.client.light.ObjectLights.Rule.fromJson(root.getAsJsonObject("rule")));
                case "object_remove" -> com.cotii.customlights.client.light.ObjectLights.removeFromServer(string(root, "key", ""));
                case "preset" -> com.cotii.customlights.client.gui.ObjectPresets.putFromServer(string(root, "name", "preset"), root.getAsJsonObject("preset"));
                case "group" -> com.cotii.customlights.client.light.LightGroups.applyFromServer(string(root, "name", ""),
                        root.has("enabled") ? root.get("enabled").getAsBoolean() : null,
                        root.has("animations") ? root.get("animations").getAsBoolean() : null);
                case "preset_remove" -> com.cotii.customlights.client.gui.ObjectPresets.removeFromServer(string(root, "name", ""));
                default -> Customlights.LOGGER.warn("Unknown CustomLights sync operation: {}", operation);
            }
        } catch (Exception exception) {
            Customlights.LOGGER.warn("Failed to handle a CustomLights server payload", exception);
        }
    }

    private static Light readLight(JsonObject object) {
        Light light = Light.fromJson(object);
        light.server = true;
        return light;
    }

    private static void move(JsonObject root) {
        Light light = LightManager.serverLight(string(root, "id", ""));
        if (light == null) {
            return;
        }
        Integer color = null;
        if (root.has("color")) {
            color = com.cotii.customlights.client.light.LightColors.parse(root.get("color").getAsString());
        }
        light.startMove(
                root.has("x") ? root.get("x").getAsDouble() : null,
                root.has("y") ? root.get("y").getAsDouble() : null,
                root.has("z") ? root.get("z").getAsDouble() : null,
                color,
                root.has("radius") ? root.get("radius").getAsFloat() : null,
                root.has("distance") ? root.get("distance").getAsFloat() : null,
                root.has("intensity") ? root.get("intensity").getAsFloat() : null,
                root.has("atmosphere") ? root.get("atmosphere").getAsFloat() : null,
                root.has("time") ? root.get("time").getAsInt() : 0,
                Light.Easing.of(root.has("ease") ? root.get("ease").getAsString() : "smooth"));
    }

    private static void flashlight(JsonObject root) {
        if (!root.has("light")) {
            LightManager.turnOffFlashlight();
            return;
        }
        Light light = readLight(root.getAsJsonObject("light"));
        light.kind = Light.Kind.FOLLOW;
        // The server may hand it to a player or entity; otherwise each player holds their own
        if (light.target == null || light.target.isEmpty()) {
            light.target = LightManager.SELF_TARGET;
        }
        if (Minecraft.getInstance().player != null) {
            LightManager.setFlashlight(light);
        }
    }

    /** The server hands the flashlight that is on to someone else (or back with @self). */
    private static void flashlightTarget(JsonObject root) {
        Light flashlight = LightManager.flashlight();
        if (flashlight == null || flashlight.removing || !root.has("light")) {
            return;
        }
        Light holder = readLight(root.getAsJsonObject("light"));
        flashlight.target = holder.target == null || holder.target.isEmpty() ? LightManager.SELF_TARGET : holder.target;
        flashlight.targetIds = holder.targetIds;
    }

    private static Integer fadeOut(JsonObject root) {
        return root.has("fadeout") && !root.get("fadeout").isJsonNull() ? root.get("fadeout").getAsInt() : null;
    }

    private static JsonArray array(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray();
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback;
    }
}
