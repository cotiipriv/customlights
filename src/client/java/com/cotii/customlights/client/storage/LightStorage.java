package com.cotii.customlights.client.storage;

import com.cotii.customlights.Customlights;
import com.cotii.customlights.client.light.Light;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Client lights are saved per world ({@code config/customlights/worlds/<world>.json}); object lights are shared by every
 * world ({@code config/customlights/objects.json}) because they belong to items, blocks and resource packs.
 */
public final class LightStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve(Customlights.MOD_ID);

    private LightStorage() {
    }

    /** File name of the world the client is in, or null when not in a world. */
    public static String worldKey(Minecraft minecraft) {
        if (minecraft.level == null) {
            return null;
        }
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server != null) {
            Path folder = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            Path name = folder.getFileName();
            return "sp_" + sanitize(name == null ? server.getWorldData().getLevelName() : name.toString());
        }
        ServerData data = minecraft.getCurrentServer();
        if (data != null) {
            return (com.cotii.customlights.client.compat.Compat.isRealm(data) ? "realm_" : "mp_") + sanitize(data.ip);
        }
        return "unknown";
    }

    private static String sanitize(String text) {
        String cleaned = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        return cleaned.isEmpty() ? "world" : cleaned;
    }

    public static List<Light> loadWorld(String key) {
        return readLights(ROOT.resolve("worlds").resolve(key + ".json"), "lights");
    }

    public static void saveWorld(String key, Collection<Light> lights) {
        writeLights(ROOT.resolve("worlds").resolve(key + ".json"), "lights", lights);
    }

    /** The groups of a world (names, on/off, animations), or null. */
    public static JsonObject loadGroups(String key) {
        return readObject(ROOT.resolve("worlds").resolve(key + ".groups.json"));
    }

    public static void saveGroups(String key, JsonObject root) {
        write(ROOT.resolve("worlds").resolve(key + ".groups.json"), root);
    }

    public static List<Light> loadModels() {
        return readLights(ROOT.resolve("models.json"), "models");
    }

    /** {@code config/customlights/objects.json}, or null when there is none. */
    public static JsonObject loadObjects() {
        return readObject(ROOT.resolve("objects.json"));
    }

    /** {@code config/customlights/settings.json}, or null when there is none. */
    public static JsonObject loadSettings() {
        return readObject(ROOT.resolve("settings.json"));
    }

    public static void saveSettings(JsonObject root) {
        write(ROOT.resolve("settings.json"), root);
    }

    /** The folder presets are kept in: {@code config/customlights/presets}. */
    public static Path presetsFolder() {
        return ROOT.resolve("presets");
    }

    /** Names of the saved presets, sorted. */
    public static List<String> presetNames() {
        List<String> names = new ArrayList<>();
        Path folder = presetsFolder();
        if (!Files.isDirectory(folder)) {
            return names;
        }
        try (var files = Files.list(folder)) {
            files.map(path -> path.getFileName().toString()).filter(name -> name.endsWith(".json"))
                    .forEach(name -> names.add(name.substring(0, name.length() - 5)));
        } catch (IOException exception) {
            Customlights.LOGGER.warn("Failed to list {}", folder, exception);
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public static String presetFileName(String name) {
        return sanitize(name);
    }

    public static JsonObject loadPreset(String name) {
        return readObject(presetsFolder().resolve(sanitize(name) + ".json"));
    }

    public static void savePreset(String name, JsonObject root) {
        write(presetsFolder().resolve(sanitize(name) + ".json"), root);
    }

    public static boolean deletePreset(String name) {
        try {
            return Files.deleteIfExists(presetsFolder().resolve(sanitize(name) + ".json"));
        } catch (IOException exception) {
            Customlights.LOGGER.warn("Failed to delete preset {}", name, exception);
            return false;
        }
    }

    /** {@code config/customlights/history.json}, or null when there is none. */
    public static JsonObject loadHistory() {
        return readObject(ROOT.resolve("history.json"));
    }

    public static void saveHistory(JsonObject root) {
        write(ROOT.resolve("history.json"), root);
    }

    private static JsonObject readObject(Path path) {
        if (!Files.exists(path)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, JsonObject.class);
        } catch (Exception exception) {
            Customlights.LOGGER.warn("Failed to read {}", path, exception);
            return null;
        }
    }

    public static void saveObjects(JsonObject root) {
        write(ROOT.resolve("objects.json"), root);
    }

    /** Model lights now live in objects.json: the old file is kept renamed. */
    public static void retireModels() {
        Path path = ROOT.resolve("models.json");
        if (Files.exists(path)) {
            try {
                Files.move(path, path.resolveSibling("models.json.migrated"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException exception) {
                Customlights.LOGGER.warn("Failed to rename {}", path, exception);
            }
        }
    }

    private static List<Light> readLights(Path path, String field) {
        List<Light> lights = new ArrayList<>();
        if (!Files.exists(path)) {
            return lights;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root != null && root.has(field)) {
                for (JsonElement element : root.getAsJsonArray(field)) {
                    try {
                        lights.add(Light.fromJson(element.getAsJsonObject()));
                    } catch (RuntimeException exception) {
                        Customlights.LOGGER.warn("Skipping a broken light in {}", path, exception);
                    }
                }
            }
        } catch (Exception exception) {
            Customlights.LOGGER.warn("Failed to read {}", path, exception);
        }
        return lights;
    }

    private static void writeLights(Path path, String field, Collection<Light> lights) {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonArray array = new JsonArray();
        for (Light light : lights) {
            if (!light.server && !light.removing) {
                array.add(light.toSavedJson());
            }
        }
        root.add(field, array);
        write(path, root);
    }

    private static void write(Path path, JsonObject root) {
        try {
            Files.createDirectories(path.getParent());
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                // Some file systems (or a file held open by another program) refuse the atomic swap
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            Customlights.LOGGER.warn("Failed to save {}", path, exception);
        }
    }
}
