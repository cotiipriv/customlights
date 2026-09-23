package com.cotii.customlights.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** 1.20.1 and 1.20.4: joining and loading a world take slightly different calls. */
public final class DevCompat {
    private DevCompat() {
    }

    public static void connectTo(Minecraft minecraft, String address) {
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                new ServerData("CustomLights test", address, ServerData.Type.OTHER), false);
    }

    public static void openLevel(Minecraft minecraft, String name) {
        minecraft.createWorldOpenFlows().checkForBackupAndLoad(name, () -> minecraft.setScreen(new TitleScreen()));
    }

    /** Saves a screenshot with this name in the game folder. */
    public static void screenshot(Minecraft minecraft, String fileName, java.util.function.Consumer<net.minecraft.network.chat.Component> done) {
        net.minecraft.client.Screenshot.grab(minecraft.gameDirectory, fileName, minecraft.getMainRenderTarget(), done);
    }

    public static void leaveServer(Minecraft minecraft) {
        minecraft.disconnect();
    }

    public static void createFlatLevel(Minecraft minecraft, String name) {
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT);
        minecraft.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(1234L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                new TitleScreen());
    }
}
