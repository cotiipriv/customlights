package com.cotii.customlights.client.command;

import com.cotii.customlights.client.light.Light;
import com.cotii.customlights.client.light.LightManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Copy and paste of whole lights ({@link PasteFormat}). */
public final class LightPaste {
    public static final String COMMAND = "/customlight paste ";

    private LightPaste() {
    }

    public static String typeOf(Light light) {
        if (LightManager.isTemplate(light)) {
            return "model";
        }
        return light.isFollow() ? "follow" : "static";
    }

    /** {@code (...)} of one light. */
    public static String entry(Light light, String type) {
        return PasteFormat.write(light.toJson(), type);
    }

    /** The full {@code /customlight paste {...}} command for some lights. */
    public static String command(List<Light> lights) {
        List<String> entries = new ArrayList<>();
        for (Light light : lights) {
            entries.add(entry(light, typeOf(light)));
        }
        return COMMAND + PasteFormat.wrap(entries);
    }

    public static String command(Light light, String type) {
        return COMMAND + PasteFormat.wrap(List.of(entry(light, type)));
    }

    /** Creates or replaces the pasted lights; returns their ids. */
    public static List<String> apply(String text) {
        List<String> ids = new ArrayList<>();
        for (PasteFormat.Entry entry : PasteFormat.parse(text)) {
            Light light = Light.fromJson(entry.light());
            if (!entry.light().has("pitch")) {
                light.pitch = light.shape.defaultPitch;
            }
            apply(light, entry.type());
            ids.add(light.id);
        }
        return ids;
    }

    /** Puts a light as the given type (static, follow, model, flashlight), keeping it lit if it already was. */
    public static void apply(Light light, String type) {
        switch (type) {
            case "model" -> {
                light.setId(light.id.toLowerCase(Locale.ROOT));
                light.kind = Light.Kind.STATIC;
                light.age = light.fadeIn;
                LightManager.putTemplate(light);
            }
            case "flashlight" -> {
                if (light.id == null || light.id.isBlank()) {
                    light.setId("flashlight");
                }
                light.kind = Light.Kind.FOLLOW;
                // Whoever holds it: the player by default, or the player or entity it was given to
                if (light.target == null || light.target.isEmpty()) {
                    light.target = LightManager.SELF_TARGET;
                }
                light.fadeIn = 4;
                light.fadeOut = 4;
                LightManager.setFlashlight(light);
            }
            default -> {
                light.kind = type.equals("follow") ? Light.Kind.FOLLOW : Light.Kind.STATIC;
                LightManager.put(light);
            }
        }
        LightManager.markDirty();
    }
}
