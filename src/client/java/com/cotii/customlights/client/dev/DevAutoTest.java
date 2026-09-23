package com.cotii.customlights.client.dev;

import com.cotii.customlights.Customlights;
import com.cotii.customlights.client.gui.EditorOpener;
import com.cotii.customlights.client.gui.LightEditor;
import com.cotii.customlights.client.command.LightPaste;
import com.cotii.customlights.client.light.Light;
import com.cotii.customlights.client.light.LightManager;
import com.cotii.customlights.client.light.LightShape;
import com.cotii.customlights.client.light.ObjectLights;
import com.cotii.customlights.client.render.LightRenderer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Development only, enabled with {@code -Dcustomlights.devtest=true}: opens (or creates) a superflat test world, builds a
 * small scene, places lights through the real commands and saves screenshots to {@code run/screenshots}.
 */
public final class DevAutoTest {
    private static final String WORLD = "CustomLightsTest";
    private static final boolean ENABLED = Boolean.getBoolean("customlights.devtest");
    private static final boolean QUIT = Boolean.getBoolean("customlights.devtest.quit");
    private static final String SCENE = System.getProperty("customlights.devtest.scene", "full");
    /** Server scene: the local Paper test server (CustomlightsPLUGIN/run-server). */
    private static final String SERVER = "localhost:25566";
    private static long reconnectAt;

    private record Step(int tick, Consumer<Minecraft> action) {
    }

    private static final List<Step> STEPS = new ArrayList<>();
    private static boolean worldRequested;
    private static int inWorldTicks;
    private static int stepIndex;
    private static boolean bobbing;

    private DevAutoTest() {
    }

    public static void register() {
        if (!ENABLED) {
            return;
        }
        if ("closeup".equals(SCENE)) {
            buildCloseup();
        } else if ("server".equals(SCENE)) {
            buildServer();
        } else if ("smoke".equals(SCENE)) {
            buildSmoke();
        } else if ("serverpig".equals(SCENE)) {
            buildServerPig();
        } else if ("hand".equals(SCENE)) {
            buildHand();
        } else if ("objects".equals(SCENE)) {
            buildObjects();
        } else if ("shapes".equals(SCENE)) {
            buildShapes();
        } else {
            buildScript();
        }
        ClientTickEvents.END_CLIENT_TICK.register(DevAutoTest::tick);
        com.cotii.customlights.client.render.GpuTimer.profileUntil = Long.MAX_VALUE;
    }

    private static void tick(Minecraft minecraft) {
        if (bobbing && minecraft.player != null) {
            com.cotii.customlights.client.compat.DevInput.bob(minecraft.player);
        }
        if (minecraft.level == null || minecraft.player == null) {
            inWorldTicks = 0;
            if (!worldRequested && minecraft.screen instanceof TitleScreen && System.currentTimeMillis() >= reconnectAt) {
                worldRequested = true;
                openWorld(minecraft);
            }
            return;
        }
        inWorldTicks++;
        while (stepIndex < STEPS.size() && minecraft.level != null && STEPS.get(stepIndex).tick() <= inWorldTicks) {
            try {
                STEPS.get(stepIndex).action().accept(minecraft);
            } catch (Exception exception) {
                Customlights.LOGGER.error("[devtest] step {} failed", stepIndex, exception);
            }
            stepIndex++;
        }
    }

    private static void openWorld(Minecraft minecraft) {
        if (SCENE.startsWith("server")) {
            com.cotii.customlights.client.compat.DevCompat.connectTo(minecraft, SERVER);
            return;
        }
        if (minecraft.getLevelSource().levelExists(WORLD)) {
            com.cotii.customlights.client.compat.DevCompat.openLevel(minecraft, WORLD);
            return;
        }
        com.cotii.customlights.client.compat.DevCompat.createFlatLevel(minecraft, WORLD);
    }

    private static void command(Minecraft minecraft, String command) {
        Customlights.LOGGER.info("[devtest] /{}", command);
        minecraft.player.connection.sendCommand(command);
    }

    private static void at(int tick, Consumer<Minecraft> action) {
        STEPS.add(new Step(tick, action));
    }

    private static void commands(int tick, String... commands) {
        at(tick, minecraft -> {
            for (String command : commands) {
                command(minecraft, command);
            }
        });
    }

    private static void look(int tick, double x, double y, double z, float yaw, float pitch) {
        at(tick, minecraft -> {
            com.cotii.customlights.client.compat.Compat.teleport(minecraft.player, x, y, z, yaw, pitch);
            // Creative flight, so the camera stays where it was put
            minecraft.player.getAbilities().flying = true;
            minecraft.player.setYRot(yaw);
            minecraft.player.setXRot(pitch);
            minecraft.player.yHeadRot = yaw;
            minecraft.player.yBodyRot = yaw;
            command(minecraft, String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
        });
    }

    private static void screenshot(int tick, String name) {
        at(tick, minecraft -> {
            minecraft.gui.getChat().clearMessages(false);
        });
        at(tick + 3, minecraft -> com.cotii.customlights.client.compat.DevCompat.screenshot(minecraft, "customlights_" + name + ".png",
                message -> Customlights.LOGGER.info("[devtest] {}", message.getString())));
    }

    /** Opens the chat with a half typed command, so the server's suggestions show, and takes a picture of them. */
    private static void suggest(int tick, String text, String name) {
        at(tick, minecraft -> {
            com.cotii.customlights.client.compat.DevInput.openChat(minecraft);
            // Typed for real, so the client asks the server for its suggestions
            for (int i = 0; i < text.length(); i++) {
                com.cotii.customlights.client.compat.DevInput.typeChar(minecraft, text.charAt(i));
            }
        });
        screenshot(tick + 14, "server_tab_" + name);
        at(tick + 20, minecraft -> minecraft.setScreen(null));
    }

    private static void logFps(Minecraft minecraft, String label) {
        Customlights.LOGGER.info("[devtest] fps {}: {} (lights drawn {} of {}, with atmosphere {})", label, minecraft.getFps(),
                LightRenderer.lastDrawn, LightRenderer.lastCollected, LightRenderer.lastHazy);
        Customlights.LOGGER.info("[devtest] gpu {}: {}", label, com.cotii.customlights.client.render.GpuTimer.report());
    }

    private static ObjectLights.Rule rule(ObjectLights.Kind kind, String target, LightShape shape, int color, float radius, float distance,
                                          float intensity, float atmosphere) {
        Light light = new Light(target);
        shape.applyLook(light);
        light.color = color;
        light.radius = radius;
        light.distance = distance;
        light.intensity = intensity;
        light.atmosphere = atmosphere;
        return new ObjectLights.Rule(kind, target, -1, light);
    }

    private static void check(String what, boolean ok) {
        Customlights.LOGGER.info("[devtest] check {}: {}", what, ok ? "OK" : "FAILED");
    }

    private static com.google.gson.JsonObject sharedLight(String id, double x, double y, double z, int color) {
        Light light = new Light(id);
        LightShape.SPHERE.applyLook(light);
        light.x = x;
        light.y = y;
        light.z = z;
        light.color = color;
        light.radius = 1.0f;
        light.distance = 2.5f;
        light.intensity = 3.0f;
        light.atmosphere = 1.0f;
        light.dimension = LightManager.currentDimension();
        return light.toJson();
    }

    private static double moveFrom;

    /** Where a server light is right now, its move included. */
    private static double serverX(String id) {
        Light light = LightManager.serverLight(id);
        return light == null ? 0.0d : light.snapshot(0.0f, new com.cotii.customlights.client.light.LightAnimation.Result()).x;
    }

    /** Where the moving light is right now (a move interpolates in the snapshot, not in the saved light). */
    private static double movingX() {
        return LightManager.get("mover").map(light -> light.snapshot(0.0f, new com.cotii.customlights.client.light.LightAnimation.Result()).x).orElse(0.0d);
    }

    private static final double[] BASE = new double[3];

    /**
     * With the Paper test server and the plugin: the player (an operator) shares lights, an object light and a preset,
     * leaves, the server removes one light and adds another while they are away, and on joining again the player must see
     * exactly the server's current list.
     */
    private static void buildServer() {
        at(40, minecraft -> {
            check("plugin found", com.cotii.customlights.client.network.ServerEdits.pluginPresent());
            check("may edit (op)", com.cotii.customlights.client.network.ServerEdits.canEdit());
        });
        commands(42, "gamemode creative", "time set midnight", "fill ~-6 ~-1 ~-2 ~6 ~3 ~10 air", "fill ~-6 ~-2 ~-2 ~6 ~-2 ~10 grass_block",
                "setblock ~ ~-1 ~6 glowstone", "setblock ~3 ~-1 ~6 stone_bricks", "setblock ~3 ~ ~6 stone_bricks", "setblock ~3 ~1 ~6 stone_bricks");
        at(50, minecraft -> {
            BASE[0] = Math.floor(minecraft.player.getX()) + 0.5d;
            BASE[1] = Math.floor(minecraft.player.getY());
            BASE[2] = Math.floor(minecraft.player.getZ()) + 0.5d;
            com.cotii.customlights.client.network.ServerEdits.setEveryone(true);
            check("set to everyone on", com.cotii.customlights.client.network.ServerEdits.everyone());
            com.cotii.customlights.client.network.ServerEdits.putLight(sharedLight("shared1", BASE[0] - 2.0d, BASE[1] + 0.5d, BASE[2] + 5.0d, 0xFF8030), "static");
            com.cotii.customlights.client.network.ServerEdits.putLight(sharedLight("shared2", BASE[0] + 2.0d, BASE[1] + 0.5d, BASE[2] + 5.0d, 0x30C0FF), "static");
            ObjectLights.Rule glow = rule(ObjectLights.Kind.BLOCK, "minecraft:glowstone", LightShape.SPHERE, 0xB040FF, 0.8f, 2.0f, 3.0f, 1.0f);
            com.cotii.customlights.client.network.ServerEdits.putObject(glow);
            com.google.gson.JsonObject preset = new com.google.gson.JsonObject();
            com.google.gson.JsonArray objects = new com.google.gson.JsonArray();
            objects.add(rule(ObjectLights.Kind.ITEM, "minecraft:torch", LightShape.SPHERE, 0xFFA040, 0.3f, 2.0f, 2.0f, 1.0f).toJson());
            preset.add("objects", objects);
            com.cotii.customlights.client.network.ServerEdits.putPreset("shared_pack", preset);
        });
        at(70, minecraft -> {
            check("shared1 arrived", LightManager.serverLight("shared1") != null);
            check("shared2 arrived", LightManager.serverLight("shared2") != null);
            check("object light arrived", ObjectLights.serverRule("block:minecraft:glowstone") != null);
            check("preset arrived", com.cotii.customlights.client.gui.ObjectPresets.json("server/shared_pack") != null);
        });
        at(72, minecraft -> {
            minecraft.player.getAbilities().flying = true;
            com.cotii.customlights.client.compat.Compat.teleport(minecraft.player, BASE[0], BASE[1] + 1.5d, BASE[2] - 1.0d, 0.0f, 20.0f);
            command(minecraft, String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f 0 20", BASE[0], BASE[1] + 1.5d, BASE[2] - 1.0d));
        });
        at(94, minecraft -> Customlights.LOGGER.info("[devtest] lights before leaving: {} collected", LightRenderer.lastCollected));
        screenshot(95, "server_shared");
        at(100, minecraft -> {
            LightEditor.showFollow("@self");
            EditorOpener.request(null);
        });
        screenshot(115, "server_editor");
        at(125, minecraft -> {
            minecraft.setScreen(null);
            Customlights.LOGGER.info("[devtest] offline at {} {} {}", BASE[0], BASE[1], BASE[2]);
            com.cotii.customlights.client.compat.DevCompat.leaveServer(minecraft);
            minecraft.setScreen(new TitleScreen());
            reconnectAt = System.currentTimeMillis() + 12_000L;
            worldRequested = false;
        });
        // Joined again (ticks count from the new join)
        at(60, minecraft -> {
            check("rejoin: plugin found", com.cotii.customlights.client.network.ServerEdits.pluginPresent());
            check("rejoin: shared1 kept", LightManager.serverLight("shared1") != null);
            check("rejoin: shared2 removed while away", LightManager.serverLight("shared2") == null);
            check("rejoin: late1 added while away", LightManager.serverLight("late1") != null);
            check("rejoin: object light kept", ObjectLights.serverRule("block:minecraft:glowstone") != null);
            check("rejoin: preset kept", com.cotii.customlights.client.gui.ObjectPresets.json("server/shared_pack") != null);
            check("rejoin: set to everyone starts off", !com.cotii.customlights.client.network.ServerEdits.everyone());
        });
        at(62, minecraft -> {
            minecraft.player.getAbilities().flying = true;
            com.cotii.customlights.client.compat.Compat.teleport(minecraft.player, BASE[0], BASE[1] + 1.5d, BASE[2] - 1.0d, 0.0f, 20.0f);
            command(minecraft, String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f 0 20", BASE[0], BASE[1] + 1.5d, BASE[2] - 1.0d));
        });
        at(84, minecraft -> Customlights.LOGGER.info("[devtest] lights after joining again: {} collected", LightRenderer.lastCollected));
        screenshot(85, "server_rejoin");
        // A server light moved to a group keeps it when the server sends it again
        at(88, minecraft -> {
            com.cotii.customlights.client.light.LightGroups.assign(LightManager.serverLight("shared1"), "srv");
            com.cotii.customlights.client.network.ServerEdits.putLight(sharedLight("shared1", BASE[0] - 2.0d, BASE[1] + 0.5d, BASE[2] + 5.0d, 0xFF3030), "static");
        });
        commands(89, "lpl set @s own1 ~ ~ ~1 sphere red 1 2 3 1 0 0", "lpl set @a g1 ~ ~ ~2 sphere blue 1 2 3 1 0 0");
        at(96, minecraft -> {
            Light shared = LightManager.serverLight("shared1");
            check("group kept after the server resends", shared != null && shared.group.equals("srv") && shared.color == 0xFF3030);
            check("own light (@s) arrived", LightManager.serverLight("own1") != null);
            check("global light (@a) arrived", LightManager.serverLight("g1") != null);
            LightEditor.showSavedLights();
            EditorOpener.request(null);
        });
        screenshot(100, "server_groups");
        at(106, minecraft -> minecraft.setScreen(null));
        commands(108, "lpl remove all");
        at(114, minecraft -> {
            for (String id : new String[]{"shared1", "late1", "own1", "g1"}) {
                Light light = LightManager.serverLight(id);
                check("remove all took " + id, light == null || light.removing);
            }
        });
        // Groups from the server: a light joins one, then the group is turned off and made still
        commands(116, "lpl set @a grp1 ~ ~1 ~3 sphere gold 1 2 3 1 0 0", "lpl group @a add night grp1");
        at(124, minecraft -> {
            Light light = LightManager.serverLight("grp1");
            check("server put grp1 in the group", light != null && light.group.equals("night"));
            check("group made on this client", com.cotii.customlights.client.light.LightGroups.get("night") != null);
        });
        commands(126, "lpl group @a modify night off");
        at(132, minecraft -> {
            com.cotii.customlights.client.light.LightGroups.Group group = com.cotii.customlights.client.light.LightGroups.get("night");
            check("server turned the group off", group != null && !group.enabled);
        });
        commands(134, "lpl group @a modify night animations off");
        at(140, minecraft -> {
            com.cotii.customlights.client.light.LightGroups.Group group = com.cotii.customlights.client.light.LightGroups.get("night");
            check("server stopped the group animations", group != null && !group.animations);
            com.cotii.customlights.client.light.LightGroups.applyFromServer("night", true, true);
        });
        // "remove @a all" again, now with nothing left: it must not complain about a light called "all"
        commands(142, "lpl remove @a all 20");
        at(150, minecraft -> {
            Light light = LightManager.serverLight("grp1");
            check("remove @a all took grp1", light == null || light.removing);
            com.cotii.customlights.client.network.ServerEdits.removeObject("block:minecraft:glowstone");
            com.cotii.customlights.client.network.ServerEdits.removePreset("shared_pack");
        });
        // The server's tab completion, as the client sees it
        commands(152, "lpl set @a tabtest ~ ~1 ~3 sphere aqua 1 2 3 1 0 0", "lpl group @a add night tabtest");
        suggest(158, "/lpl set @a ", "ids");
        suggest(182, "/lpl group @a add ", "groups");
        suggest(206, "/lpl remove @a ", "remove");
        suggest(230, "/lpl set @a tabtest ~ ~1 ~3 sphere ", "colors");
        // The plugin's move, with an easing, on a light everyone has
        at(256, minecraft -> moveFrom = serverX("tabtest"));
        commands(258, "lpl move @a tabtest ~4 ~ ~ bounce 20");
        at(272, minecraft -> {
            double now = serverX("tabtest");
            check("the server's move is under way", now > moveFrom + 0.5d && now < moveFrom + 3.9d);
        });
        at(300, minecraft -> {
            double now = serverX("tabtest");
            Customlights.LOGGER.info("[devtest] the server moved the light from x {} to {}", moveFrom, now);
            check("the server's move landed on B", Math.abs(now - (moveFrom + 4.0d)) < 0.01d);
        });
        // The plugin hands a flashlight to a tagged entity, then back to the player
        commands(302, "summon armor_stand ~ ~ ~3 {Tags:[\"srvholder\"],NoGravity:1b}");
        commands(306, "lpl flashlight CotiiDev srvtorch tag:srvholder white 1 8");
        at(312, minecraft -> {
            Light flashlight = LightManager.flashlight();
            List<net.minecraft.world.entity.Entity> holders = flashlight == null ? List.of() : LightManager.targets(minecraft, flashlight);
            check("the server gave the flashlight to an entity", !holders.isEmpty() && holders.get(0) != minecraft.player);
        });
        commands(314, "lpl flashlight CotiiDev srvtorch @self white 1 8");
        at(320, minecraft -> {
            Light flashlight = LightManager.flashlight();
            check("the server gave the flashlight back", flashlight != null && LightManager.SELF_TARGET.equals(flashlight.target));
        });
        commands(322, "lpl flashlight CotiiDev off", "kill @e[tag=srvholder]", "lpl remove all");
        at(330, minecraft -> {
            check("cleanup: object light gone", ObjectLights.serverRule("block:minecraft:glowstone") == null);
            check("cleanup: preset gone", com.cotii.customlights.client.gui.ObjectPresets.json("server/shared_pack") == null);
            Customlights.LOGGER.info("[devtest] finished");
            if (QUIT) {
                minecraft.stop();
            }
        });
    }

    /**
     * A quick check for every game version (about 20 seconds): a few shapes, a block light, the atmosphere, the editor and a
     * moving light, then it says whether the lights really reached the screen.
     */
    private static void buildSmoke() {
        int t = 20;
        commands(t, "gamemode creative", "time set midnight", "kill @e[type=!player]", "customlight remove all 0",
                "fill -8 -61 -8 8 -50 8 air", "fill -8 -61 -8 8 -61 8 grass_block", "setblock 3 -60 3 glowstone",
                "setblock -3 -60 3 stone_bricks", "setblock -3 -59 3 stone_bricks");
        commands(t + 6,
                "customlight set ball -2.5 -59.5 2.5 sphere orange 1.2 3 3 2 0 0",
                "customlight set cone 2.5 -57 2.5 cone cyan 1.5 1 3 3 0 0",
                "customlight set spot 0.5 -56 -1.5 spotlight magenta 1 1 4 4 0 0",
                "customlight set beam -5.5 -60 -3.5 beam lime 0.6 1 3 2 0 0",
                "customlight seethrough beam false", "item replace entity @s weapon.mainhand with minecraft:torch");
        at(t + 10, minecraft -> {
            ObjectLights.put(rule(ObjectLights.Kind.BLOCK, "minecraft:glowstone", LightShape.SPHERE, 0xB040FF, 0.8f, 2.0f, 3.0f, 1.0f));
            // Item light: the torch held in first person
            ObjectLights.put(rule(ObjectLights.Kind.ITEM, "minecraft:torch", LightShape.SPHERE, 0xFF9030, 0.2f, 4.0f, 2.0f, 1.5f));
            ObjectLights.saveIfDirty();
        });
        look(t + 12, 0.5, -59.0, -5.5, 0.0f, 10.0f);
        screenshot(t + 40, "smoke_lights");
        // A flashlight handed to an entity (tag target), shining where it looks
        commands(t + 30, "summon armor_stand 0.5 -60 1.5 {Tags:[\"holder\"],Rotation:[180f,0f]}");
        commands(t + 44, "customlight move ball 2.5 -59.5 2.5 bounce 30", "customlight flashlight holdertorch tag:holder white 1 8");
        screenshot(t + 60, "smoke_moving");
        at(t + 70, minecraft -> {
            LightEditor.showObjects("block", "minecraft:glowstone");
            EditorOpener.request(null);
        });
        screenshot(t + 85, "smoke_editor");
        at(t + 92, minecraft -> minecraft.setScreen(null));
        at(t + 100, minecraft -> {
            logFps(minecraft, "smoke");
            check("lights reach the screen", LightRenderer.lastDrawn > 0 && LightRenderer.lastCollected >= 5);
            check("the atmosphere is drawn", LightRenderer.lastHazy > 0);
            Customlights.LOGGER.info("[devtest] markers: {} eye {}", com.cotii.customlights.client.light.MarkerLights.describe(), minecraft.player.getEyePosition());
            check("item light in hand", com.cotii.customlights.client.light.MarkerLights.lastCollected > 0
                    && com.cotii.customlights.client.light.MarkerLights.handLightInFront(minecraft.player.getEyePosition(), minecraft.player.getLookAngle()));
            com.cotii.customlights.client.light.Light flashlight = com.cotii.customlights.client.light.LightManager.flashlight();
            check("flashlight held by an entity", flashlight != null && !com.cotii.customlights.client.light.LightManager.targets(minecraft, flashlight).isEmpty()
                    && com.cotii.customlights.client.light.LightManager.targets(minecraft, flashlight).get(0) != minecraft.player);
            check("shaders of the mod built", !LightRenderer.shaderFailed());
            Customlights.LOGGER.info("[devtest] finished");
            if (QUIT) {
                minecraft.stop();
            }
        });
    }

    /**
     * Every kind of object light at once, each checked where it should shine: an item in every place it can be (first
     * person, custom model data in the off hand, third person, head, ground, item frame), a block, an entity, a particle and
     * two special targets.
     */
    /** The shapes that move by themselves, side by side. */
    private static void buildShapes() {
        int t = 20;
        commands(t, "gamemode creative", "time set midnight", "kill @e[type=!player]", "customlight remove all 0",
                "fill -12 -61 -12 12 -48 12 air", "fill -12 -61 -12 12 -61 12 grass_block");
        commands(t + 6,
                "customlight set flame -6.5 -60 2.5 fire orange 1.2 0.35 3 2 0 0",
                "customlight set gate -2.5 -58 2.5 portal purple 1.8 0.8 2.2 4 0 0",
                "customlight set bolt 2.5 -60 2.5 lightning white 0.35 0.7 3 3.5 0 0",
                "customlight set sky 7.5 -60 2.5 aurora aqua 2.5 1.2 1.8 5 0 0",
                "customlight set pool -10.5 -60 6.5 ripple cyan 3 0.8 2 2.5 0 0",
                "customlight set orb -1.5 -58 8.5 plasma aqua 1.2 1 2.4 4 0 0",
                "customlight set twister 4.5 -60 8.5 tornado white 0.9 1 2 4.5 0 0",
                "customlight set disc -10.5 -57 14.5 singularity purple 2.6 0.6 2.6 3 0 0",
                "customlight set circle -4.5 -60 14.5 rune aqua 2.4 0.35 2.8 1.5 0 0",
                "customlight set spark 0.5 -58 14.5 flare gold 1.6 0.8 3 3 0 0",
                "customlight set fog 5.5 -60 14.5 mist white 4 1.4 1.2 6 0 0",
                "customlight set star 10.5 -57 14.5 comet cyan 0.7 0.6 3.2 4 0 0",
                "customlight set door 14.5 -60 8.5 laser_door lime 1.4 0.05 3.6 3 0 0");

        look(t + 10, -2.5, -56.0, -14.5, 0.0f, 3.0f);
        screenshot(t + 30, "shapes_far");
        look(t + 34, 14.5, -58.5, 2.5, 0.0f, 2.0f);
        screenshot(t + 40, "shapes_a");
        // Close to the flame, where its tip has to hold up
        look(t + 44, 14.5, -58.6, 5.5, 0.0f, -6.0f);
        screenshot(t + 48, "shapes_fire_a");
        screenshot(t + 52, "shapes_fire_b");
        at(t + 56, minecraft -> logFps(minecraft, "shapes"));

        screenshot(t + 55, "shapes_b");
        at(t + 31, minecraft -> {
            check("fire light drawn", com.cotii.customlights.client.light.LightManager.get("flame").isPresent());
            check("portal light drawn", com.cotii.customlights.client.light.LightManager.get("gate").isPresent());
            check("lightning light drawn", com.cotii.customlights.client.light.LightManager.get("bolt").isPresent());
            check("aurora light drawn", com.cotii.customlights.client.light.LightManager.get("sky").isPresent());
            check("wave light drawn", com.cotii.customlights.client.light.LightManager.get("pool").isPresent());
            check("plasma light drawn", com.cotii.customlights.client.light.LightManager.get("orb").isPresent());
            check("tornado light drawn", com.cotii.customlights.client.light.LightManager.get("twister").isPresent());
            check("singularity light drawn", com.cotii.customlights.client.light.LightManager.get("disc").isPresent());
            check("rune light drawn", com.cotii.customlights.client.light.LightManager.get("circle").isPresent());
            check("flare light drawn", com.cotii.customlights.client.light.LightManager.get("spark").isPresent());
            check("mist light drawn", com.cotii.customlights.client.light.LightManager.get("fog").isPresent());
            check("comet light drawn", com.cotii.customlights.client.light.LightManager.get("star").isPresent());
            check("laser door light drawn", com.cotii.customlights.client.light.LightManager.get("door").isPresent());
            check("lights on screen", com.cotii.customlights.client.render.LightRenderer.lastDrawn >= 6);

        });
        at(t + 70, minecraft -> minecraft.stop());
    }

    private static void buildObjects() {

        int t = 20;
        String torch = "{id:\"minecraft:torch\",Count:1b,count:1}";
        commands(t, "gamemode creative", "time set midnight", "kill @e[type=!player]", "customlight remove all 0",
                "fill -8 -61 -8 8 -50 8 air", "fill -8 -61 -8 8 -61 8 grass_block",
                "setblock -5 -60 0 lantern", "setblock -2 -60 6 campfire[lit=true]", "setblock 3 -59 4 stone",
                "item replace entity @s weapon.mainhand with minecraft:torch");
        at(t + 2, minecraft -> {
            // Only this scene's rules (other scenes save theirs)
            for (ObjectLights.Rule old : new ArrayList<>(ObjectLights.rules())) {
                ObjectLights.remove(old.key(), 0);
            }
            ObjectLights.Rule torchRule = rule(ObjectLights.Kind.ITEM, "minecraft:torch", LightShape.SPHERE, 0xFF9030, 0.2f, 3.0f, 2.0f, 1.0f);
            torchRule.displays.values().forEach(display -> display.on = true);
            ObjectLights.put(torchRule);
            ObjectLights.Rule stick = rule(ObjectLights.Kind.ITEM, "minecraft:stick", LightShape.SPHERE, 0x30A0FF, 0.2f, 3.0f, 2.0f, 1.0f);
            stick.customModelData = 7;
            stick.displays.values().forEach(display -> display.on = true);
            ObjectLights.put(stick);
            ObjectLights.put(rule(ObjectLights.Kind.BLOCK, "minecraft:lantern", LightShape.SPHERE, 0xFFB050, 0.3f, 3.0f, 1.8f, 1.0f));
            ObjectLights.put(rule(ObjectLights.Kind.ENTITY, "minecraft:pig", LightShape.SPHERE, 0xFF60C0, 0.3f, 3.0f, 1.8f, 1.0f));
            ObjectLights.put(rule(ObjectLights.Kind.PARTICLE, "minecraft:flame", LightShape.SPHERE, 0xFF6010, 0.1f, 1.5f, 2.0f, 1.0f));
            ObjectLights.put(rule(ObjectLights.Kind.SPECIAL, ObjectLights.LIT_BLOCKS, LightShape.SPHERE, 0xFFE080, 0.3f, 2.0f, 1.2f, 0.5f));
            ObjectLights.put(rule(ObjectLights.Kind.SPECIAL, ObjectLights.GLOWING_ENTITY, LightShape.SPHERE, 0x80FF80, 0.3f, 2.0f, 1.5f, 0.5f));
            // A stick with custom model data 7 in the off hand (set on the server side of the test world)
            var server = minecraft.getSingleplayerServer();
            if (server != null) {
                server.execute(() -> {
                    net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
                    com.cotii.customlights.client.compat.Compat.setCustomModelData(stack, 7);
                    server.getPlayerList().getPlayers().get(0).setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, stack);
                });
            }
        });
        commands(t + 4,
                "summon item -3.5 -60 2.5 {Item:" + torch + ",NoGravity:1b,PickupDelay:32767,Age:-32768}",
                "summon item_frame 3.5 -58.5 3.5 {Facing:2b,Fixed:1b,Item:" + torch + "}",
                "summon armor_stand -0.5 -60 3.5 {Tags:[\"stand\"],ShowArms:1b,NoGravity:1b,Rotation:[180f,0f]}",
                "summon armor_stand 2.5 -60 6.5 {Glowing:1b,NoGravity:1b,Invisible:1b}",
                "summon pig 5.5 -60 1.5 {NoAI:1b,Tags:[\"piggy\"]}");
        commands(t + 8, "item replace entity @e[tag=stand] weapon.mainhand with minecraft:torch",
                "item replace entity @e[tag=stand] armor.head with minecraft:torch", "customlight flashlight standtorch tag:stand white 1 8");
        // A saved flashlight: a follow light with the flashlight shape, on the pig
        commands(t + 9, "customlight flashlight pigtorch tag:piggy white 1 8", "customlight flashlight mytorch @self white 1 8");
        look(t + 12, 0.5, -59.0, -6.0, 0.0f, 12.0f);
        commands(t + 56, "particle minecraft:flame 0.5 -59 1.5 0 0 0 0 5 force");
        screenshot(t + 50, "objects");
        at(t + 60, minecraft -> {
            logFps(minecraft, "objects");
            Customlights.LOGGER.info("[devtest] markers: {}", com.cotii.customlights.client.light.MarkerLights.describe());
            List<String> markers = com.cotii.customlights.client.light.MarkerLights.describe();
            check("item: first person hand", com.cotii.customlights.client.light.MarkerLights.handLightInFront(minecraft.player.getEyePosition(),
                    minecraft.player.getLookAngle()));
            check("item: custom model data (off hand)", markers.stream().anyMatch(marker -> marker.contains("stick#7@firstperson_lefthand")));
            check("item: ground", LightRenderer.lightNear(-3.5, -59.8, 2.5, 1.0));
            check("item: item frame", LightRenderer.lightNear(3.5, -58.5, 3.9, 1.0));
            check("item: third person hand", LightRenderer.lightNear(-0.5, -59.3, 3.4, 0.8));
            check("item: head", LightRenderer.lightNear(-0.5, -58.1, 3.5, 0.6));
            check("block: lantern", LightRenderer.lightNear(-4.5, -59.6, 0.5, 1.0));
            check("entity: pig", LightRenderer.lightNear(5.5, -59.5, 1.5, 1.5));
            check("particle: flame", LightRenderer.lightNear(0.5, -59.0, 1.5, 1.0));
            check("special: lit blocks", LightRenderer.lightNear(-1.5, -59.5, 6.5, 1.0));
            check("special: glowing entity", LightRenderer.lightNear(2.5, -59.0, 6.5, 1.5));
            Light flashlight = LightManager.flashlight();
            List<net.minecraft.world.entity.Entity> holders = flashlight == null ? List.of() : LightManager.targets(minecraft, flashlight);
            check("two flashlights with ids", LightManager.get("mytorch").isPresent() && LightManager.get("pigtorch").isPresent());
            check("saved flashlight (follow) on the pig", LightManager.get("pigtorch").map(saved -> !LightManager.targets(minecraft, saved).isEmpty()).orElse(false));
            check("flashlight held by an entity", !holders.isEmpty() && holders.get(0) != minecraft.player
                    && LightRenderer.lightNear(holders.get(0).getX(), holders.get(0).getEyeY(), holders.get(0).getZ(), 1.0));
            check("shaders of the mod built", !LightRenderer.shaderFailed());
            Customlights.LOGGER.info("[devtest] finished");
            if (QUIT) {
                minecraft.stop();
            }
        });
    }

    /**
     * On the test server: the plugin gives a pig a flashlight for everyone, by command and then the way the editor does it
     * with Set to everyone.
     */
    private static void buildServerPig() {
        commands(40, "gamemode creative", "time set midnight", "kill @e[type=pig]",
                "summon pig ~ ~0.5 ~3 {NoAI:1b,Invulnerable:1b,NoGravity:1b,Tags:[\"srvpig\"],Rotation:[180f,20f]}");
        at(44, minecraft -> look(0, minecraft));
        commands(50, "lpl flashlight @a pigtorch tag:srvpig white 1 8");
        at(60, minecraft -> check("plugin command: the pig holds the flashlight", pigHolds(minecraft)));
        screenshot(62, "server_pig_command");
        commands(70, "lpl flashlight @a off");
        at(76, minecraft -> {
            Light flashlight = new Light("flashlight");
            LightShape.FLASHLIGHT.applyLook(flashlight);
            flashlight.color = 0xFF40FF;
            flashlight.radius = 1.0f;
            flashlight.distance = 8.0f;
            flashlight.target = "tag:srvpig";
            com.cotii.customlights.client.network.ServerEdits.putLight(flashlight.toJson(), "flashlight");
        });
        at(86, minecraft -> check("editor (Set to everyone): the pig holds the flashlight", pigHolds(minecraft)));
        screenshot(88, "server_pig_editor");
        // Saved on the server: it is in the store and comes back when players join
        at(94, minecraft -> check("the server keeps the flashlight (persistent)", LightManager.get("flashlight").map(light -> light.server).orElse(false)));
        commands(96, "lpl flashlight @a off", "kill @e[type=pig]");
        at(100, minecraft -> {
            Customlights.LOGGER.info("[devtest] finished");
            if (QUIT) {
                minecraft.stop();
            }
        });
    }

    private static void look(int unused, Minecraft minecraft) {
        minecraft.player.setXRot(20.0f);
        minecraft.player.setYRot(0.0f);
    }

    private static boolean pigHolds(Minecraft minecraft) {
        Light flashlight = LightManager.flashlight();
        List<net.minecraft.world.entity.Entity> holders = flashlight == null ? List.of() : LightManager.targets(minecraft, flashlight);
        Customlights.LOGGER.info("[devtest] flashlight {} ids {} held by {}", flashlight == null ? "off" : flashlight.target, flashlight == null ? "" : flashlight.targetIds, holders);
        return !holders.isEmpty() && holders.get(0).getType().toString().endsWith("pig");
    }

    /** The light of a held item, easy to see: a bright cyan torch light, then the same view without it. */
    private static void buildHand() {
        int t = 20;
        commands(t, "gamemode creative", "time set midnight", "kill @e[type=!player]", "customlight remove all 0",
                "fill -8 -61 -8 8 -50 8 air", "fill -8 -61 -8 8 -61 8 stone", "item replace entity @s weapon.mainhand with minecraft:torch");
        at(t + 2, minecraft -> {
            for (ObjectLights.Rule old : new ArrayList<>(ObjectLights.rules())) {
                ObjectLights.remove(old.key(), 0);
            }
            ObjectLights.put(rule(ObjectLights.Kind.ITEM, "minecraft:torch", LightShape.SPHERE, 0x00FFFF, 0.5f, 6.0f, 5.0f, 1.0f));
        });
        look(t + 4, 0.5, -59.0, 0.5, 0.0f, 50.0f);
        screenshot(t + 40, "hand_on");
        at(t + 46, minecraft -> {
            Customlights.LOGGER.info("[devtest] markers: {} collected {}", com.cotii.customlights.client.light.MarkerLights.describe(),
                    com.cotii.customlights.client.light.MarkerLights.lastCollected);
            check("item light in hand", com.cotii.customlights.client.light.MarkerLights.handLightInFront(minecraft.player.getEyePosition(),
                    minecraft.player.getLookAngle()));
            ObjectLights.remove("item:minecraft:torch", 0);
        });
        screenshot(t + 60, "hand_off");
        // The flashlight in the editor, with who holds it
        commands(t + 66, "customlight flashlight flashlight @self white 1 8");
        look(t + 67, 0.5, -59.0, 0.5, 0.0f, 30.0f);
        screenshot(t + 68, "hand_flashlight");
        at(t + 74, minecraft -> EditorOpener.request("flashlight"));
        screenshot(t + 85, "hand_editor");
        at(t + 95, minecraft -> {
            Customlights.LOGGER.info("[devtest] finished");
            if (QUIT) {
                minecraft.stop();
            }
        });
    }

    /** A short scene: textured blocks and a zombie head right in front of the camera under a strong light. */
    private static void buildCloseup() {
        int t = 60;
        commands(t, "gamemode creative", "time set midnight", "kill @e[type=!player]", "customlight remove all 0",
                "fill -3 -61 -3 3 -50 6 air", "fill -3 -61 -3 3 -61 6 grass_block", "setblock 0 -60 2 carved_pumpkin[facing=north]",
                "setblock -1 -60 2 oak_leaves", "setblock 1 -60 2 bookshelf", "setblock 0 -59 2 zombie_head[rotation=8]",
                "item replace entity @s weapon.mainhand with minecraft:diamond_sword", "item replace entity @s weapon.offhand with minecraft:lantern");
        look(t + 5, 0.5, -59.2, -0.2, 0.0f, 25.0f);
        commands(t + 20, "customlight set strong 0.5 -58.5 0.5 sphere orange 1 6 3 1 0 0");
        at(t + 45, minecraft -> com.cotii.customlights.client.render.LightRenderer.probeDepth = com.cotii.customlights.client.render.LightRenderer.probeHand = true);
        screenshot(t + 50, "closeup_light");
        commands(t + 70, "customlight toggle");
        screenshot(t + 90, "closeup_vanilla");
        commands(t + 100, "customlight toggle");
        // View bobbing: a hard edged pad and a cone must stay glued to the blocks while the view bobs
        commands(t + 105, "customlight remove strong 0", "customlight set pad -1.5 -59.95 1.5 pad cyan 1.2 0 3 0 0 0",
                "customlight set cone 1.5 -57 1.5 cone lime 1 0 3 0 0 0",
                // A grid of thin beams shows any wobble at once
                "customlight set door 0.5 -60 4.5 laser_door lime 1.4 0.05 3.6 3 0 0");
        screenshot(t + 120, "bob_still");
        at(t + 125, minecraft -> bobbing = true);
        at(t + 118, minecraft -> LightRenderer.debugDump = true);
        at(t + 138, minecraft -> LightRenderer.debugDump = true);
        screenshot(t + 140, "bob_moving");
        at(t + 150, minecraft -> bobbing = false);
        // Very close to a textured block under a strong light (texture pixels fill the screen)
        commands(t + 151, "setblock 0 -60 1 moss_block", "setblock 1 -60 1 lime_wool", "setblock -1 -60 1 grass_block", "customlight remove all 0",
                "customlight set macro 0.5 -58.2 1.5 sphere lime 1.5 3 4 0 0 0");
        look(t + 151, 0.5, -60.0, 0.3, 0.0f, 65.0f);
        screenshot(t + 158, "closeup_macro_light");
        commands(t + 159, "customlight toggle");
        screenshot(t + 161, "closeup_macro_vanilla");
        commands(t + 162, "customlight toggle");
        // Campfires under a warm light (texture detail), then the editor previewing a torch light (no flicker)
        commands(t + 172, "customlight remove all 0", "setblock -1 -60 0 campfire", "setblock 1 -60 0 campfire",
                "customlight set warm 0.5 -58.6 0.5 sphere orange 0.8 5 3 1 0 0");
        look(t + 173, 0.5, -58.6, -2.0, 0.0f, 35.0f);
        screenshot(t + 195, "closeup_campfire");
        at(t + 200, minecraft -> {
            ObjectLights.Rule torch = rule(ObjectLights.Kind.ITEM, "minecraft:torch", LightShape.SPHERE, 0x40A0FF, 0.3f, 2.0f, 3.0f, 1.0f);
            torch.light.fadeIn = 10;
            ObjectLights.put(torch);
            LightEditor.showObjects("item", "minecraft:torch");
            EditorOpener.request(null);
        });
        at(t + 216, minecraft -> com.cotii.customlights.client.render.LightRenderer.traceFrames = 40);
        screenshot(t + 220, "closeup_editor_a");
        screenshot(t + 226, "closeup_editor_b");
        at(t + 232, minecraft -> minecraft.setScreen(null));
        // A light right behind a wall: through blocks it lights the front of the wall, stopped it does not
        commands(t + 242, "customlight remove all 0", "fill -3 -60 4 3 -57 4 stone_bricks", "fill -3 -60 5 3 -57 7 air",
                "customlight set wall 0.5 -59 5.5 sphere cyan 1 3 3 2 0 0");
        look(t + 243, 0.5, -60.0, 0.5, 0.0f, 15.0f);
        screenshot(t + 260, "closeup_wall_through");
        commands(t + 262, "customlight seethrough wall false");
        screenshot(t + 270, "closeup_wall_blocked");
        look(t + 272, 0.5, -56.0, 9.5, 180.0f, 25.0f);
        screenshot(t + 285, "closeup_wall_behind");
        // The move easings: their curve, and a light really travelling from A to B in the given ticks
        at(t + 290, minecraft -> {
            for (Light.Easing easing : Light.Easing.values()) {
                StringBuilder curve = new StringBuilder();
                for (int step = 0; step <= 10; step++) {
                    curve.append(String.format(java.util.Locale.ROOT, " %.3f", easing.ease(step / 10.0f)));
                }
                Customlights.LOGGER.info("[devtest] easing {}:{}", easing.id, curve);
                boolean ends = Math.abs(easing.ease(0.0f)) < 1.0e-4f && Math.abs(easing.ease(1.0f) - 1.0f) < 1.0e-4f;
                check("easing " + easing.id + " starts at A and ends at B", ends);
            }
        });
        commands(t + 292, "customlight remove all 0", "customlight set mover 0.5 -59 0.5 sphere gold 1 2 3 1 0 0");
        look(t + 294, 3.5, -57.0, -4.0, 0.0f, 20.0f);
        commands(t + 300, "customlight move mover 6.5 -59 0.5 bounce 40");
        at(t + 302, minecraft -> moveFrom = movingX());
        screenshot(t + 315, "closeup_move_bounce");
        at(t + 322, minecraft -> {
            double now = movingX();
            Customlights.LOGGER.info("[devtest] the moving light is at x {} (started at {})", now, moveFrom);
            check("the move is under way", now > moveFrom + 0.5d && now < 6.4d);
        });
        at(t + 345, minecraft -> {
            double now = movingX();
            check("the move lands on B after its ticks", Math.abs(now - 6.5d) < 0.01d);
        });
        at(t + 350, minecraft -> {
            Customlights.LOGGER.info("[devtest] finished");
            if (QUIT) {
                minecraft.stop();
            }
        });
    }

    private static void buildScript() {
        int t = 60;
        commands(t,
                "gamemode creative",
                "time set midnight",
                "weather clear",
                "gamerule doDaylightCycle false",
                "kill @e[type=!player]",
                "fill -14 -60 -10 14 -45 24 air",
                "fill -14 -61 -10 14 -61 24 grass_block",
                "fill -14 -60 22 14 -50 22 stone_bricks",
                "fill -14 -60 -10 -14 -50 22 polished_andesite",
                "fill 14 -60 -10 14 -50 22 polished_andesite",
                "fill 3 -60 12 4 -54 13 quartz_block",
                "fill -6 -60 6 -5 -57 7 oak_planks",
                "setblock 8 -60 6 white_wool",
                "setblock 9 -60 6 red_wool",
                "setblock 10 -60 6 lime_wool",
                "summon minecraft:villager 0.5 -60 10 {NoAI:1b,Silent:1b,Tags:[\"lit\"],Rotation:[180f,0f]}",
                "summon minecraft:armor_stand -2.5 -60 8 {ShowArms:1b,Rotation:[160f,0f],HandItems:[{id:\"minecraft:blaze_rod\",count:1},{}]}",
                "summon minecraft:pig 6.5 -60 8 {NoAI:1b,Silent:1b,Rotation:[200f,0f]}",
                "item replace entity @s weapon.mainhand with minecraft:diamond_sword",
                "item replace entity @s weapon.offhand with minecraft:lantern",
                "customlight remove all 0",
                "customlight model_id rodtip 0 0 0 sphere magenta 0.15 0.5 3 5 5 2"
        );
        look(t + 5, 0.5, -60, -7.5, 0.0f, 8.0f);
        t += 40;
        commands(t,
                "customlight set red_orb -4.5 -58 3 sphere red 0.5 5 2.2 3 10 10",
                "customlight set lamp 0.5 -54 8 cone warm 3 1.5 2.2 4 10 10",
                "customlight set search 8.5 -60 14 spotlight aqua 1.5 1 3 6 10 10 20 -65",
                "customlight set pillar -9.5 -60 16 beam lava 0.6 2 2 8 10 10",
                "customlight stretch pillar 1 2.5 1",
                "customlight set floor 6.5 -59.9 2 pad purple 2.2 0.5 1.8 1 10 10",
                "customlight set halo 0.5 -57 16 ring party 2 1.5 2.2 3 10 10",
                "customlight set lift -2.5 -60 14 inverted_cone blue 2 1 2 5 10 10",
                "customlight follow villager_glow tag:lit 0 2.6 0 sphere gold 0.3 3 2 2 10 10",
                "customlight set star_floor -8.5 -59.95 3 star yellow 2 0.3 2 2 10 10",
                "customlight set crate -9.5 -58 8 box cyan 0.5 1 1.5 3 10 10",
                "customlight stretch crate 1 3 1",
                "customlight set shade 3.5 -59 4 sphere black 1.5 1.5 1 0 10 10",
                "customlight paste {(type:static,id:pasted,pos:[-6.5,-57,12],shape:cross,color:#FF00AA,radius:1.2,distance:0.8,intensity:2,atmosphere:4,pitch:0,yaw:0)}"
        );
        screenshot(t + 40, "01_shapes_night");
        commands(t + 60, "customlight flashlight flashlight @self white 2.5 18 2 2");
        screenshot(t + 80, "02_flashlight");
        commands(t + 100,
                "customlight flashlight off",
                "customlight animate red_orb circle+pulse 60",
                "customlight animate red_orb animation_radius 3",
                "customlight animate halo rainbow 80",
                "customlight animate search sweep 120",
                "customlight animate lamp color_transition warm 20 magenta 40 aqua 20");
        screenshot(t + 130, "03_animated_a");
        screenshot(t + 160, "04_animated_b");
        // Close-up: hand, items and entities under a light next to the player
        commands(t + 180, "customlight set hand_light 1.5 -58.4 -5.8 sphere orange 0.2 3 3 0 0 0",
                "item replace entity @s weapon.mainhand with minecraft:blaze_rod");
        look(t + 181, 0.5, -60, -7.5, -20.0f, 25.0f);
        screenshot(t + 200, "05_hand");
        look(t + 220, 0.5, -60, 4.5, 0.0f, -5.0f);
        screenshot(t + 240, "06_close_entities");
        commands(t + 260, "customlight copy search");
        at(t + 262, minecraft -> Customlights.LOGGER.info("[devtest] clipboard: {}", minecraft.keyboardHandler.getClipboard()));
        look(t + 270, 0.5, -60, -7.5, 0.0f, 8.0f);
        at(t + 275, minecraft -> EditorOpener.request("search"));
        screenshot(t + 300, "07_editor");
        at(t + 330, minecraft -> minecraft.setScreen(null));
        // Object lights: lanterns (block), torches in hand (item) and flames (particle)
        at(t + 335, minecraft -> {
            ObjectLights.put(rule(ObjectLights.Kind.BLOCK, "minecraft:lantern", LightShape.SPHERE, 0xFFB050, 0.3f, 4.0f, 1.8f, 2.0f));
            ObjectLights.put(rule(ObjectLights.Kind.ITEM, "minecraft:torch", LightShape.SPHERE, 0xFF9030, 0.2f, 4.0f, 2.0f, 1.5f));
            ObjectLights.put(rule(ObjectLights.Kind.PARTICLE, "minecraft:flame", LightShape.SPHERE, 0xFF6010, 0.1f, 1.5f, 2.0f, 1.0f));
        });
        commands(t + 336, "setblock -3 -58 -2 lantern", "setblock 4 -58 -2 lantern", "item replace entity @s weapon.mainhand with minecraft:torch",
                "customlight remove all 0", "particle minecraft:flame 0.5 -59 -2 0.3 0.1 0.3 0.01 30 force");
        look(t + 338, 0.5, -60, -7.5, 0.0f, 20.0f);
        screenshot(t + 360, "08_objects");
        at(t + 380, minecraft -> {
            LightEditor.showObjects("item", "minecraft:torch");
            EditorOpener.request(null);
        });
        screenshot(t + 410, "09_objects_editor");
        at(t + 440, minecraft -> minecraft.setScreen(null));
        // Entity preview stands still
        at(t + 445, minecraft -> {
            LightEditor.showObjects("entity", "minecraft:pig");
            EditorOpener.request(null);
        });
        screenshot(t + 470, "10_entity_preview");
        at(t + 490, minecraft -> minecraft.setScreen(null));

        // Siren, flashlight and beacon beam
        t += 500;
        commands(t,
                "customlight set siren1 0.5 -58 12 siren red 0.5 0.3 2.6 2.5 10 10",
                "customlight animate siren1 rotation 40 360",
                "fill 9 -60 -1 11 -60 1 iron_block",
                "setblock 10 -59 0 beacon",
                "item replace entity @s weapon.mainhand with minecraft:diamond_sword");
        at(t + 1, minecraft -> ObjectLights.put(new ObjectLights.Rule(ObjectLights.Kind.SPECIAL, ObjectLights.BEACON_BEAM, -1,
                ObjectLights.defaultLight(ObjectLights.Kind.SPECIAL, ObjectLights.BEACON_BEAM))));
        look(t + 2, 0.5, -60, -3.5, 0.0f, 10.0f);
        screenshot(t + 30, "11_siren_a");
        screenshot(t + 40, "12_siren_b");
        commands(t + 60, "customlight flashlight flashlight @self white 2 0.5");
        look(t + 61, 0.5, -60, 2.5, 0.0f, 25.0f);
        screenshot(t + 80, "13_flashlight");
        commands(t + 100, "customlight flashlight off");
        look(t + 101, 4.5, -60, -8.5, 20.0f, -25.0f);
        screenshot(t + 200, "14_beacon");
        at(t + 205, minecraft -> LightRenderer.debugDump = true);

        // Gizmo on a static light
        look(t + 220, 3.5, -59, 5.5, -25.0f, 15.0f);
        at(t + 225, minecraft -> EditorOpener.request("siren1"));
        screenshot(t + 250, "15_gizmo");
        at(t + 270, minecraft -> {
            // Ctrl+Z / Ctrl+Y must not break anything
            com.cotii.customlights.client.compat.DevInput.pressKey(minecraft, 90, 2);
            com.cotii.customlights.client.compat.DevInput.pressKey(minecraft, 89, 2);
            minecraft.setScreen(null);
        });

        // The flashlight from the editor and the same one pasted must look the same
        look(t + 280, 0.5, -59, -8.5, 0.0f, 30.0f);
        commands(t + 282, "customlight remove all 0", "customlight flashlight flashlight @self white 2 0.5");
        at(t + 290, minecraft -> EditorOpener.request("flashlight"));
        screenshot(t + 310, "17_flashlight_editor");
        at(t + 320, minecraft -> {
            String command = LightEditor.draftCommand();
            Customlights.LOGGER.info("[devtest] editor flashlight: {}", command);
            minecraft.setScreen(null);
            LightManager.turnOffFlashlight();
            LightPaste.apply(command.substring(LightPaste.COMMAND.length()));
            Customlights.LOGGER.info("[devtest] pasted flashlight: {}", LightManager.flashlight().toJson());
        });
        screenshot(t + 340, "18_flashlight_pasted");
        // A dark light around the player with the flashlight on must not flicker
        commands(t + 350, "customlight set dark 0.5 -59 -6.5 sphere black 6 2 1 0 0 0");
        screenshot(t + 360, "19_dark_flashlight_a");
        screenshot(t + 370, "19_dark_flashlight_b");
        commands(t + 380, "customlight flashlight off", "customlight remove dark 0");

        // Follow light on the player, seen in third person
        at(t + 390, minecraft -> {
            LightEditor.showFollow("@self");
            EditorOpener.request(null);
            minecraft.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
        });
        screenshot(t + 420, "20_follow_self");
        at(t + 440, minecraft -> {
            minecraft.setScreen(null);
            minecraft.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        });

        // Blocks next to each other: a lit ring of end portal frames and scattered amethyst
        commands(t + 450,
                "fill -12 -60 -9 -8 -60 -5 end_portal_frame",
                "fill -11 -60 -8 -9 -60 -6 air",
                "setblock 8 -60 -8 amethyst_cluster",
                "setblock 10 -60 -7 amethyst_cluster",
                "setblock 9 -60 -5 amethyst_cluster",
                "setblock 11 -60 -5 amethyst_cluster");
        at(t + 452, minecraft -> {
            ObjectLights.put(rule(ObjectLights.Kind.BLOCK, "minecraft:end_portal_frame", LightShape.SPHERE, 0x40FF40, 0.25f, 1.5f, 1.4f, 0.6f));
            ObjectLights.put(rule(ObjectLights.Kind.BLOCK, "minecraft:amethyst_cluster", LightShape.SPHERE, 0xC060FF, 0.2f, 1.5f, 1.4f, 0.6f));
        });
        look(t + 455, -1.5, -55, -1.5, 90.0f, 45.0f);
        screenshot(t + 500, "21_blocks_frames");
        look(t + 510, 5.5, -56, -3.5, -90.0f, 40.0f);
        screenshot(t + 540, "22_blocks_amethyst");

        // A torch held in first and third person carries its light with the model
        at(t + 560, minecraft -> {
            ObjectLights.Rule torch = rule(ObjectLights.Kind.ITEM, "minecraft:torch", LightShape.SPHERE, 0xFF9030, 0.15f, 2.5f, 2.0f, 0.8f);
            torch.displays.get(net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND).on = true;
            torch.displays.get(net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND).on = true;
            ObjectLights.put(torch);
        });
        commands(t + 562, "item replace entity @s weapon.mainhand with minecraft:torch", "setblock -3 -58 -2 air", "setblock 4 -58 -2 air");
        look(t + 564, -4.5, -60, -8.5, 180.0f, 30.0f);
        screenshot(t + 580, "23_torch_first_person");
        at(t + 590, minecraft -> minecraft.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
        screenshot(t + 610, "24_torch_third_person");
        at(t + 620, minecraft -> minecraft.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));

        // Lit blocks (campfires) through the special kind
        commands(t + 625, "setblock -6 -60 -3 campfire", "setblock -4 -60 -4 campfire", "setblock -6 -60 -5 campfire[lit=false]");
        at(t + 626, minecraft -> ObjectLights.put(new ObjectLights.Rule(ObjectLights.Kind.SPECIAL, ObjectLights.LIT_BLOCKS, -1,
                ObjectLights.defaultLight(ObjectLights.Kind.SPECIAL, ObjectLights.LIT_BLOCKS))));
        look(t + 628, -5.0, -57, -8.5, 0.0f, 40.0f);
        screenshot(t + 670, "25_lit_blocks");

        // Item display settings and presets in the editor
        at(t + 680, minecraft -> {
            LightEditor.showObjects("item", "minecraft:torch");
            EditorOpener.request(null);
        });
        screenshot(t + 700, "26_item_display");
        at(t + 715, minecraft -> LightEditor.showPresets("devtest"));
        screenshot(t + 740, "27_presets");
        at(t + 760, minecraft -> {
            LightEditor.hidePresets();
            minecraft.setScreen(null);
        });
        // Groups: made with the command, listed in the saved lights tab, switched off and on
        commands(t + 765, "customlight set g1 -2.5 -59 -6.5 sphere cyan 0.4 2 2 1 0 0", "customlight set g2 -0.5 -59 -6.5 sphere magenta 0.4 2 2 1 0 0",
                "customlight animate g1 pulse 20", "customlight group add stage g1 g2", "customlight group add empty_group", "customlight group list");
        look(t + 766, -1.5, -58, -10.0, 0.0f, 35.0f);
        at(t + 770, minecraft -> {
            LightEditor.showSavedLights();
            EditorOpener.request(null);
        });
        screenshot(t + 790, "28_groups");
        at(t + 800, minecraft -> minecraft.setScreen(null));
        commands(t + 805, "customlight group modify stage off");
        screenshot(t + 815, "29_group_off");
        commands(t + 830, "customlight group modify stage on", "customlight group modify stage animations off");

        // Stress: every grass block, a glowstone field, a nether portal and its particles are lights
        t += 850;
        at(t, minecraft -> logFps(minecraft, "before stress"));
        commands(t + 5,
                "fill -14 -61 30 14 -61 60 glowstone",
                "fill -2 -60 26 3 -60 26 obsidian",
                "fill -2 -55 26 3 -55 26 obsidian",
                "fill -2 -59 26 -2 -56 26 obsidian",
                "fill 3 -59 26 3 -56 26 obsidian",
                "setblock -1 -59 26 fire");
        at(t + 6, minecraft -> {
            ObjectLights.put(rule(ObjectLights.Kind.BLOCK, "minecraft:grass_block", LightShape.SPHERE, 0x40FF60, 0.2f, 1.2f, 0.6f, 0.3f));
            ObjectLights.put(rule(ObjectLights.Kind.BLOCK, "minecraft:glowstone", LightShape.SPHERE, 0xFFD070, 0.3f, 2.0f, 1.2f, 0.8f));
            ObjectLights.put(rule(ObjectLights.Kind.BLOCK, "minecraft:nether_portal", LightShape.SPHERE, 0xA040FF, 0.4f, 3.0f, 1.8f, 2.0f));
            ObjectLights.put(rule(ObjectLights.Kind.PARTICLE, "minecraft:portal", LightShape.SPHERE, 0xC060FF, 0.1f, 1.0f, 1.5f, 0.5f));
        });
        commands(t + 10, "setblock 0 -59 26 fire");
        look(t + 20, 0.5, -40, 17.5, 0.0f, 35.0f);
        at(t + 100, minecraft -> logFps(minecraft, "stress"));
        screenshot(t + 110, "16_stress");
        at(t + 140, minecraft -> logFps(minecraft, "stress 2"));
        commands(t + 145, "customlight quality low");
        at(t + 190, minecraft -> logFps(minecraft, "stress low quality"));
        commands(t + 195, "customlight toggle");
        at(t + 240, minecraft -> logFps(minecraft, "lights off"));
        commands(t + 245, "customlight toggle", "customlight quality high");
        at(t + 250, minecraft -> {
            ObjectLights.remove("block:minecraft:grass_block", 0);
            ObjectLights.remove("block:minecraft:glowstone", 0);
        });
        // Benchmark: which pass costs what, at the main scene view
        look(t + 260, 0.5, -60, -7.5, 0.0f, 8.0f);
        int[] skips = {0, 1, 2, 4, 8, 15, -1};
        for (int i = 0; i < skips.length; i++) {
            int skip = skips[i];
            int start = t + 280 + i * 70;
            at(start, minecraft -> {
                LightRenderer.debugSkip = Math.max(0, skip);
                LightRenderer.enabled = skip >= 0;
            });
            at(start + 65, minecraft -> logFps(minecraft, "skip " + skip));
        }
        t += 280 + skips.length * 70;
        at(t, minecraft -> {
            LightRenderer.debugSkip = 0;
            LightRenderer.enabled = true;
        });
        at(t + 10, minecraft -> {
            Customlights.LOGGER.info("[devtest] finished");
            if (QUIT) {
                minecraft.stop();
            }
        });
    }
}
