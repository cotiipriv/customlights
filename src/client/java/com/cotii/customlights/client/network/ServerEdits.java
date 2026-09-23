package com.cotii.customlights.client.network;

import com.cotii.customlights.Customlights;
import com.cotii.customlights.client.gui.ObjectPresets;
import com.cotii.customlights.client.light.ObjectLights;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * Talking to the CustomLights server plugin ({@code customlights:edit}): the client says hello when the server accepts the
 * channel, the plugin answers whether this player may edit the server's lights (operators), and editors can then send
 * lights, object lights and presets for everyone.
 */
public final class ServerEdits {
    private static final Gson GSON = new Gson();
    private static boolean pluginPresent;
    private static boolean editor;
    private static String pluginVersion = "";
    /** Editor toggle: saving sends to everyone on the server instead of keeping it on this client. */
    private static boolean everyone;

    private ServerEdits() {
    }

    public static void register() {
        SyncNetwork.registerEdits(ServerEdits::hello);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
            if (SyncNetwork.canSendEdits()) {
                hello();
            }
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(ServerEdits::reset));
    }

    private static void hello() {
        JsonObject root = new JsonObject();
        root.addProperty("op", "hello");
        root.addProperty("version", net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(Customlights.MOD_ID)
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("?"));
        send(root);
    }

    static void onHello(boolean canEdit, String version) {
        pluginPresent = true;
        editor = canEdit;
        pluginVersion = version;
        if (!canEdit) {
            everyone = false;
        }
    }

    private static void reset() {
        pluginPresent = false;
        editor = false;
        everyone = false;
        pluginVersion = "";
        ObjectLights.clearServer();
        ObjectPresets.clearServer();
    }

    /** The server runs the CustomLights plugin. */
    public static boolean pluginPresent() {
        return pluginPresent;
    }

    /** This player may change the server's lights (operator). */
    public static boolean canEdit() {
        return pluginPresent && editor && SyncNetwork.canSendEdits();
    }

    public static String pluginVersion() {
        return pluginVersion;
    }

    /** "Set to everyone" is on (and allowed). */
    public static boolean everyone() {
        return everyone && canEdit();
    }

    public static void setEveryone(boolean value) {
        everyone = value && canEdit();
    }

    // ── Edits
    // ──────────────────────────────────────────────────────

    /** A light for everyone ({@code type} static, follow or flashlight). */
    public static void putLight(JsonObject light, String type) {
        JsonObject root = new JsonObject();
        root.addProperty("op", "put_light");
        root.addProperty("type", type);
        root.add("light", light);
        send(root);
    }

    public static void removeLight(String id, Integer fadeOut) {
        JsonObject root = new JsonObject();
        root.addProperty("op", "remove_light");
        root.addProperty("id", id);
        if (fadeOut != null) {
            root.addProperty("fadeout", fadeOut);
        }
        send(root);
    }

    public static void putObject(ObjectLights.Rule rule) {
        JsonObject root = new JsonObject();
        root.addProperty("op", "put_object");
        root.add("rule", rule.toJson());
        send(root);
    }

    public static void removeObject(String key) {
        JsonObject root = new JsonObject();
        root.addProperty("op", "remove_object");
        root.addProperty("key", key);
        send(root);
    }

    public static void putPreset(String name, JsonObject preset) {
        JsonObject root = new JsonObject();
        root.addProperty("op", "put_preset");
        root.addProperty("name", name);
        root.add("preset", preset);
        send(root);
    }

    public static void removePreset(String name) {
        JsonObject root = new JsonObject();
        root.addProperty("op", "remove_preset");
        root.addProperty("name", name);
        send(root);
    }

    private static void send(JsonObject root) {
        if (!SyncNetwork.canSendEdits()) {
            return;
        }
        String json = GSON.toJson(root);
        lastTooLarge = !SyncNetwork.sendEdit(json);
        if (lastTooLarge) {
            Customlights.LOGGER.warn("CustomLights edit too large to send to the server ({} characters)", json.length());
        }
    }

    private static boolean lastTooLarge;

    /** The last edit was too big for a plugin message and was not sent. */
    public static boolean lastTooLarge() {
        return lastTooLarge;
    }

}
