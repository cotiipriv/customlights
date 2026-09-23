package com.cotii.customlights.neoforge.fabric.loader;

import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/** NeoForge build: the loader queries the shared code makes (installed mods, config folder, this mod's version). */
public final class FabricLoader {
    private static final FabricLoader INSTANCE = new FabricLoader();
    /** The other names these mods go by on NeoForge (1.20.x has Embeddium and Oculus instead). */
    private static final Map<String, String> NEOFORGE_NAMES = Map.of("iris", "oculus", "sodium", "embeddium");

    private FabricLoader() {
    }

    public static FabricLoader getInstance() {
        return INSTANCE;
    }

    public boolean isModLoaded(String id) {
        // Also asked while mixins load, before the mod list exists
        var mods = FMLLoader.getLoadingModList();
        if (mods == null) {
            return false;
        }
        return mods.getModFileById(id) != null || (NEOFORGE_NAMES.containsKey(id) && mods.getModFileById(NEOFORGE_NAMES.get(id)) != null);
    }

    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    public Optional<ModContainer> getModContainer(String id) {
        var mods = FMLLoader.getLoadingModList();
        var file = mods == null ? null : mods.getModFileById(id);
        if (file == null) {
            return Optional.empty();
        }
        String version = file.getMods().stream().filter(mod -> mod.getModId().equals(id)).findFirst()
                .map(mod -> mod.getVersion().toString()).orElse("?");
        return Optional.of(new ModContainer(version));
    }

    public record ModContainer(String version) {
        public Metadata getMetadata() {
            return new Metadata(version);
        }
    }

    public record Metadata(String version) {
        public Version getVersion() {
            return new Version(version);
        }
    }

    public record Version(String version) {
        public String getFriendlyString() {
            return version;
        }
    }
}
