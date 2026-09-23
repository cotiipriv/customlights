package com.cotii.customlights.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** Opening worlds and joining servers, for the development test bot only (see DevAutoTest). */
public final class DevCompat {
    private DevCompat() {
    }

    public static void connectTo(Minecraft minecraft, String address) {
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                new ServerData("CustomLights test", address, ServerData.Type.OTHER), false, null);
    }

    public static void openLevel(Minecraft minecraft, String name) {
        minecraft.createWorldOpenFlows().openWorld(name, () -> minecraft.setScreen(new TitleScreen()));
    }

    /** Saves a screenshot with this name in the game folder. */
    public static void screenshot(Minecraft minecraft, String fileName, java.util.function.Consumer<net.minecraft.network.chat.Component> done) {
        net.minecraft.client.Screenshot.grab(minecraft.gameDirectory, fileName, minecraft.getMainRenderTarget(), 1, done);
    }

    public static void leaveServer(Minecraft minecraft) {
        minecraft.disconnect(new TitleScreen(), false);
    }

    /** A fresh superflat creative world, always night and still. */
    public static void createFlatLevel(Minecraft minecraft, String name) {
        GameRules rules = new GameRules(net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS);
        rules.set(GameRules.ADVANCE_TIME, false, null);
        rules.set(GameRules.ADVANCE_WEATHER, false, null);
        rules.set(GameRules.SPAWN_MOBS, false, null);
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT);
        minecraft.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(1234L, false, false),
                registries -> registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                new TitleScreen());
    }
}
