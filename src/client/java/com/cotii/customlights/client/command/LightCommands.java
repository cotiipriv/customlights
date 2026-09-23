package com.cotii.customlights.client.command;

import com.cotii.customlights.client.gui.EditorOpener;
import com.cotii.customlights.client.light.Light;
import com.cotii.customlights.client.light.LightAnimation;
import com.cotii.customlights.client.light.LightColors;
import com.cotii.customlights.client.light.LightGroups;
import com.cotii.customlights.client.light.LightManager;
import com.cotii.customlights.client.light.LightShape;
import com.cotii.customlights.client.render.LightRenderer;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.Optional;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/** {@code /customlight}: every client command. */
public final class LightCommands {
    private static final int ID_MAX_LENGTH = 48;
    private static final float MAX_INTENSITY = 100.0f;
    private static final float MAX_STRETCH = 64.0f;
    private static final int CHAT_LIMIT = 256;

    private LightCommands() {
    }

    private static CommandDispatcher<FabricClientCommandSource> commands;

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        commands = dispatcher;
        dispatcher.register(literal("customlight")

                .executes(LightCommands::help)
                .then(literal("help").executes(LightCommands::help))
                .then(setNode())
                .then(followNode())
                .then(removeNode())
                .then(moveNode())
                .then(modelNode())
                .then(modelRemoveNode())
                .then(flashlightNode())
                .then(animateNode())
                .then(stretchNode())
                .then(pivotNode())
                .then(angleNode())
                .then(luminosityNode())
                .then(seeThroughNode())
                .then(groupNode())
                .then(literal("copy")
                        .executes(LightCommands::copyAll)
                        .then(argument("id", idArgument(true)).executes(LightCommands::copy)))
                .then(literal("paste")
                        .executes(context -> paste(context, Minecraft.getInstance().keyboardHandler.getClipboard()))
                        .then(argument("light", StringArgumentType.greedyString())
                                .executes(context -> paste(context, StringArgumentType.getString(context, "light")))))
                .then(literal("list").executes(LightCommands::list))
                .then(literal("editor")
                        .executes(context -> openEditor(null))
                        .then(argument("id", idArgument(true)).executes(context -> openEditor(TokenArgument.get(context, "id")))))
                .then(literal("toggle").executes(LightCommands::toggle))
                .then(literal("quality").then(argument("level", TokenArgument.token("low", "medium", "high", "ultra")).executes(LightCommands::quality)))
                .then(literal("stats").executes(LightCommands::stats)));
    }

    // ── Argument helpers
    // ───────────────────────────────────────────

    private static TokenArgument idArgument(boolean withTemplates) {
        return TokenArgument.token(() -> {
            List<String> ids = new ArrayList<>(LightManager.ids());
            if (withTemplates) {
                for (String id : LightManager.templateIds()) {
                    if (!ids.contains(id)) {
                        ids.add(id);
                    }
                }
                if (LightManager.flashlight() != null && !ids.contains("flashlight")) {
                    ids.add("flashlight");
                }
            }
            return ids;
        });
    }

    private static TokenArgument coordinateArgument(int axis) {
        return TokenArgument.token(() -> {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("~");
            HitResult hit = Minecraft.getInstance().hitResult;
            if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
                int value = switch (axis) {
                    case 0 -> block.getBlockPos().getX();
                    case 1 -> block.getBlockPos().getY() + 1;
                    default -> block.getBlockPos().getZ();
                };
                suggestions.add(String.valueOf(value));
            }
            return suggestions;
        });
    }

    private static TokenArgument colorArgument(boolean keep) {
        return TokenArgument.token(() -> {
            List<String> colors = new ArrayList<>();
            if (keep) {
                colors.add("~");
            }
            colors.addAll(LightColors.NAMES);
            colors.add("#FF8800");
            return colors;
        });
    }

    private static ArgumentBuilder<FabricClientCommandSource, ?> arg(String name, ArgumentType<?> type) {
        return argument(name, type);
    }

    /** Links nodes into a path; every node from index {@code minimum - 1} on runs {@code command}. */
    @SafeVarargs
    private static ArgumentBuilder<FabricClientCommandSource, ?> path(Command<FabricClientCommandSource> command, int minimum,
                                                                      ArgumentBuilder<FabricClientCommandSource, ?>... nodes) {
        for (int i = nodes.length - 1; i >= 0; i--) {
            if (i >= minimum - 1) {
                nodes[i].executes(command);
            }
            if (i < nodes.length - 1) {
                nodes[i].then(nodes[i + 1]);
            }
        }
        return nodes[0];
    }

    @SafeVarargs
    private static ArgumentBuilder<FabricClientCommandSource, ?>[] concat(ArgumentBuilder<FabricClientCommandSource, ?>[] tail,
                                                                         ArgumentBuilder<FabricClientCommandSource, ?>... head) {
        ArgumentBuilder<FabricClientCommandSource, ?>[] all = java.util.Arrays.copyOf(head, head.length + tail.length);
        System.arraycopy(tail, 0, all, head.length, tail.length);
        return all;
    }

    private static final SimpleCommandExceptionType NO_PLAYER = new SimpleCommandExceptionType(Component.literal("You need to be in a world"));

    private static CommandSyntaxException error(String message) {
        return new SimpleCommandExceptionType(Component.literal(message)).create();
    }

    private static String id(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
        String id = TokenArgument.get(context, "id");
        if (id.isEmpty() || id.length() > ID_MAX_LENGTH || !id.chars().allMatch(c -> c < 128 && (Character.isLetterOrDigit(c) || "_-.+:".indexOf(c) >= 0))) {
            throw error("Invalid id '" + id + "': use letters, numbers and _ - . + : (max " + ID_MAX_LENGTH + ")");
        }
        if (id.equalsIgnoreCase("all") || id.equalsIgnoreCase("flashlight")) {
            throw error("'" + id + "' is reserved");
        }
        return id;
    }

    private static int color(CommandContext<FabricClientCommandSource> context, String name) throws CommandSyntaxException {
        return parseColor(TokenArgument.get(context, name));
    }

    private static int parseColor(String text) throws CommandSyntaxException {
        Integer color = LightColors.parse(text);
        if (color == null) {
            throw error("Unknown color '" + text + "': use a name, party or #RRGGBB");
        }
        return color;
    }

    private static LightShape shape(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
        String text = TokenArgument.get(context, "shape");
        LightShape shape = LightShape.byName(text);
        if (shape == null) {
            throw error("Unknown shape '" + text + "': " + String.join(", ", LightShape.NAMES));
        }
        return shape;
    }

    /** "~", "~1.5" (relative to base) or an absolute number; integers on x/z are centred on the block. */
    static double coordinate(String text, double base, boolean centreIntegers) throws CommandSyntaxException {
        try {
            if (text.startsWith("~")) {
                return text.length() == 1 ? base : base + Double.parseDouble(text.substring(1));
            }
            double value = Double.parseDouble(text);
            if (centreIntegers && !text.contains(".")) {
                value += 0.5d;
            }
            return value;
        } catch (NumberFormatException exception) {
            throw error("Invalid coordinate '" + text + "'");
        }
    }

    /** "~" keeps the current value (null), otherwise a number. */
    private static Float keepOrFloat(CommandContext<FabricClientCommandSource> context, String name) throws CommandSyntaxException {
        String text = TokenArgument.get(context, name);
        if (text.equals("~")) {
            return null;
        }
        try {
            return Float.parseFloat(text);
        } catch (NumberFormatException exception) {
            throw error("Invalid number '" + text + "' for " + name + " (use ~ to keep it)");
        }
    }

    private static <T> T optional(CommandContext<FabricClientCommandSource> context, String name, Class<T> type) {
        try {
            return context.getArgument(name, type);
        } catch (IllegalArgumentException missing) {
            return null;
        }
    }

    private static Vec3 base(FabricClientCommandSource source) throws CommandSyntaxException {
        if (source.getPlayer() == null) {
            throw NO_PLAYER.create();
        }
        return source.getPosition();
    }

    // ── set / follow
    // ───────────────────────────────────────────────

    /** {@code <shape> <color> <radius> <distance> <intensity> <atmosphere> <fade_in> <fade_out> [yaw] [pitch]}. */
    @SuppressWarnings("unchecked")
    private static ArgumentBuilder<FabricClientCommandSource, ?>[] lightSettings() {
        return new ArgumentBuilder[]{
                arg("shape", TokenArgument.token(LightShape.NAMES.toArray(String[]::new))),
                arg("color", colorArgument(false)),
                arg("radius", FloatArgumentType.floatArg(0.0f, 512.0f)),
                arg("distance", FloatArgumentType.floatArg(0.0f, 512.0f)),
                arg("intensity", FloatArgumentType.floatArg(0.0f, MAX_INTENSITY)),
                arg("atmosphere", FloatArgumentType.floatArg(0.0f, Light.MAX_ATMOSPHERE)),
                arg("fade_in", IntegerArgumentType.integer(0)),
                arg("fade_out", IntegerArgumentType.integer(0)),
                arg("yaw", FloatArgumentType.floatArg(-360.0f, 360.0f)),
                arg("pitch", FloatArgumentType.floatArg(-90.0f, 90.0f))
        };
    }

    private static ArgumentBuilder<FabricClientCommandSource, ?> setNode() {
        ArgumentBuilder<FabricClientCommandSource, ?>[] nodes = concat(lightSettings(),
                arg("id", idArgument(false)), arg("x", coordinateArgument(0)), arg("y", coordinateArgument(1)), arg("z", coordinateArgument(2)));
        return literal("set").then(path(LightCommands::set, 12, nodes));
    }

    private static void applySettings(CommandContext<FabricClientCommandSource> context, Light light) throws CommandSyntaxException {
        light.color = color(context, "color");
        light.radius = FloatArgumentType.getFloat(context, "radius");
        light.distance = FloatArgumentType.getFloat(context, "distance");
        light.intensity = FloatArgumentType.getFloat(context, "intensity");
        light.atmosphere = FloatArgumentType.getFloat(context, "atmosphere");
        light.fadeIn = IntegerArgumentType.getInteger(context, "fade_in");
        light.fadeOut = IntegerArgumentType.getInteger(context, "fade_out");
        light.setShape(shape(context), optional(context, "yaw", Float.class), optional(context, "pitch", Float.class));
    }

    /** A new light that keeps the animations and stretch of the light it replaces. */
    private static Light replacing(String id) {
        Light light = new Light(id);
        LightManager.get(id).filter(existing -> !existing.server).ifPresent(existing -> {
            light.animation.read(existing.animation.write());
            light.group = existing.group;
            // The angle is kept while the shape stays the same
            light.shape = existing.shape;
            light.angle = existing.angle;
            light.stretchX = existing.stretchX;
            light.stretchY = existing.stretchY;
            light.stretchZ = existing.stretchZ;
            light.pivotX = existing.pivotX;
            light.pivotY = existing.pivotY;
            light.pivotZ = existing.pivotZ;
        });
        return light;
    }

    private static int set(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
        Vec3 base = base(context.getSource());
        Light light = replacing(id(context));
        light.x = coordinate(TokenArgument.get(context, "x"), base.x, true);
        light.y = coordinate(TokenArgument.get(context, "y"), base.y, false);
        light.z = coordinate(TokenArgument.get(context, "z"), base.z, true);
        applySettings(context, light);
        LightManager.put(light);
        feedback(context, "Light '" + light.id + "' set", light);
        return Command.SINGLE_SUCCESS;
    }

    private static ArgumentBuilder<FabricClientCommandSource, ?> followNode() {
        ArgumentBuilder<FabricClientCommandSource, ?>[] nodes = concat(lightSettings(),
                arg("id", idArgument(false)),
                arg("target", TokenArgument.token(LightCommands::targetSuggestions)),
                arg("offset_x", TokenArgument.token("0")), arg("offset_y", TokenArgument.token("1", "0")), arg("offset_z", TokenArgument.token("0")));
        // visible_firstperson right after fade_out or after pitch
        nodes[12].then(argument("visible_firstperson", BoolArgumentType.bool()).executes(LightCommands::follow));
        nodes[14].then(argument("visible_firstperson", BoolArgumentType.bool()).executes(LightCommands::follow));
        return literal("follow").then(path(LightCommands::follow, 13, nodes));
    }

    private static Collection<String> targetSuggestions() {
        List<String> targets = new ArrayList<>();
        targets.add(LightManager.SELF_TARGET);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null) {
            for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
                targets.add(info.getProfile().getName());
            }
        }
        targets.addAll(FollowTargets.entityUuids(minecraft));
        targets.add("tag:");
        return targets;
    }

    private static int follow(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
        base(context.getSource());
        Light light = replacing(id(context));
        light.kind = Light.Kind.FOLLOW;
        light.target = TokenArgument.get(context, "target");
        light.offsetX = coordinate(TokenArgument.get(context, "offset_x"), 0.0d, false);
        light.offsetY = coordinate(TokenArgument.get(context, "offset_y"), 0.0d, false);
        light.offsetZ = coordinate(TokenArgument.get(context, "offset_z"), 0.0d, false);
        applySettings(context, light);
        Boolean visible = optional(context, "visible_firstperson", Boolean.class);
        light.visibleFirstPerson = visible == null || visible;
        LightManager.put(light);
        String note = LightManager.targets(Minecraft.getInstance(), light).isEmpty() ? " (target not loaded yet: it shows when it is)" : "";
        feedback(context, "Light '" + light.id + "' follows " + light.target + note, light);
        return Command.SINGLE_SUCCESS;
    }

    // ── remove
    // ─────────────────────────────────────────────────────

    private static ArgumentBuilder<FabricClientCommandSource, ?> removeNode() {
        return literal("remove").then(path(LightCommands::remove, 1,
                arg("id", TokenArgument.token(() -> {
                    List<String> ids = new ArrayList<>(LightManager.ids());
                    ids.add(0, "all");
                    if (LightManager.flashlight() != null) {
                        ids.add("flashlight");
                    }
                    return ids;
                })),
                arg("fade_out", IntegerArgumentType.integer(0))));
    }

    private static int remove(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
        String id = TokenArgument.get(context, "id");
        Integer fadeOut = optional(context, "fade_out", Integer.class);
        if (id.equalsIgnoreCase("all")) {
            int count = LightManager.removeAll(fadeOut);
            context.getSource().sendFeedback(Component.literal("Removed " + count + " light(s)").withStyle(ChatFormatting.GOLD));
            return count;
        }
        if (id.equalsIgnoreCase("flashlight") && LightManager.flashlight() != null) {
            LightManager.turnOffFlashlight();
        } else if (!LightManager.remove(id, fadeOut)) {
            throw error("No light '" + id + "' here");
        }
        context.getSource().sendFeedback(Component.literal("Light '" + id + "' removed").withStyle(ChatFormatting.GOLD));
        return Command.SINGLE_SUCCESS;
    }

    // ── move
    // ───────────────────────────────────────────────────────

    private static ArgumentBuilder<FabricClientCommandSource, ?> moveNode() {
        return literal("move").then(path(LightCommands::move, 6,
                arg("id", idArgument(false)),
                arg("x", moveCoordinate(0)), arg("y", moveCoordinate(1)), arg("z", moveCoordinate(2)),
                arg("easing", TokenArgument.token(Light.Easing.SMOOTH.id, Light.Easing.LINEAR.id, Light.Easing.STEP.id, Light.Easing.BOUNCE.id)),
                arg("time", IntegerArgumentType.integer(0))));
    }

    /** Where a move can go: the player's own position, what they look at, and ~ for where the light already is. */
    private static TokenArgument moveCoordinate(int axis) {
        return TokenArgument.token(() -> {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("~");
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                Vec3 position = minecraft.player.position();
                double value = switch (axis) {
                    case 0 -> position.x;
                    case 1 -> position.y + 1.0d;
                    default -> position.z;
                };
                suggestions.add(CommandWriter.number(Math.round(value * 100.0d) / 100.0d));
            }
            HitResult hit = minecraft.hitResult;
            if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
                int value = switch (axis) {
                    case 0 -> block.getBlockPos().getX();
                    case 1 -> block.getBlockPos().getY() + 1;
                    default -> block.getBlockPos().getZ();
                };
                if (!suggestions.contains(String.valueOf(value))) {
                    suggestions.add(String.valueOf(value));
                }
            }
            return suggestions;
        });
    }

    private static int move(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
        String id = TokenArgument.get(context, "id");
        Light light = LightManager.get(id).orElseThrow(() -> error("No light '" + id + "' here"));
        // For moves, ~ is relative to where the light is (its offset for follow lights)
        double baseX = light.isFollow() ? light.offsetX : light.x;
        double baseY = light.isFollow() ? light.offsetY : light.y;
        double baseZ = light.isFollow() ? light.offsetZ : light.z;
        double x = coordinate(TokenArgument.get(context, "x"), baseX, !light.isFollow());
        double y = coordinate(TokenArgument.get(context, "y"), baseY, false);
        double z = coordinate(TokenArgument.get(context, "z"), baseZ, !light.isFollow());
        Light.Easing easing = Light.Easing.of(TokenArgument.get(context, "easing"));
        int time = IntegerArgumentType.getInteger(context, "time");
        light.startMove(x, y, z, null, null, null, null, null, time, easing);
        LightManager.markDirty();
        context.getSource().sendFeedback(Component.literal("Moving '" + light.id + "' " + easing.id + " over " + time + "t")
                .withStyle(ChatFormatting.GOLD));
        return Command.SINGLE_SUCCESS;
    }

    private static Float nonNegative(Float value) {
        return value == null ? null : Math.max(0.0f, value);
    }

    // ── model_id
    // ───────────────────────────────────────────────────

    private static ArgumentBuilder<FabricClientCommandSource, ?> modelNode() {
        return literal("model_id").then(path(LightCommands::model, 11,
                arg("id", TokenArgument.token(LightManager::templateIds)),
                arg("x", TokenArgument.token("0")), arg("y", TokenArgument.token("0")), arg("z", TokenArgument.token("0")),
                arg("shape", TokenArgument.token(LightShape.NAMES.toArray(String[]::new))),
                arg("color", colorArgument(false)),
                arg("radius", FloatArgumentType.floatArg(0.0f, 512.0f)),
                arg("opacity", FloatArgumentType.floatArg(0.0f, 1.0f)),
                arg("light", FloatArgumentType.floatArg(0.0f, MAX_INTENSITY)),
                arg("fade_in", IntegerArgumentType.integer(0)),
                arg("fade_out", IntegerArgumentType.integer(0)),
                arg("distance", FloatArgumentType.floatArg(0.0f, 512.0f)),
                arg("yaw", FloatArgumentType.floatArg(-360.0f, 360.0f)),
                arg("pitch", FloatArgumentType.floatArg(-90.0f, 90.0f))));
    }

    private static int model(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
        String id = id(context).toLowerCase(Locale.ROOT);
        Light template = new Light(id);
        LightManager.getTemplate(id).filter(existing -> !existing.server).ifPresent(existing -> {
            template.animation.read(existing.animation.write());
            template.shape = existing.shape;
            template.angle = existing.angle;
            template.stretchX = existing.stretchX;
            template.stretchY = existing.stretchY;
            template.stretchZ = existing.stretchZ;
            template.pivotX = existing.pivotX;
            template.pivotY = existing.pivotY;
            template.pivotZ = existing.pivotZ;
        });
        template.x = coordinate(TokenArgument.get(context, "x"), 0.0d, false);
        template.y = coordinate(TokenArgument.get(context, "y"), 0.0d, false);
        template.z = coordinate(TokenArgument.get(context, "z"), 0.0d, false);
        template.color = color(context, "color");
        template.radius = FloatArgumentType.getFloat(context, "radius");
        template.atmosphere = FloatArgumentType.getFloat(context, "opacity") * Light.MAX_ATMOSPHERE;
        template.intensity = FloatArgumentType.getFloat(context, "light");
        template.fadeIn = IntegerArgumentType.getInteger(context, "fade_in");
        template.fadeOut = IntegerArgumentType.getInteger(context, "fade_out");
        Float distance = optional(context, "distance", Float.class);
        template.distance = distance != null ? distance : Math.max(0.5f, template.radius);
        template.setShape(shape(context), optional(context, "yaw", Float.class), optional(context, "pitch", Float.class));
        // Parts already on screen keep shining; only new ones fade in
        template.age = template.fadeIn;
        LightManager.putTemplate(template);
        feedback(context, "Model light '" + id + "' set: name a bone or group 'customlights_" + id + "'", template);
        return Command.SINGLE_SUCCESS;
    }

    private static ArgumentBuilder<FabricClientCommandSource, ?> modelRemoveNode() {
        return literal("model_remove").then(path(LightCommands::modelRemove, 1,
                arg("id", TokenArgument.token(() -> {
                    List<String> ids = new ArrayList<>(LightManager.templateIds());
                    ids.add(0, "all");
                    return ids;
                })),
                arg("fade_out", IntegerArgumentType.integer(0))));
    }

    private static int modelRemove(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
        String id = TokenArgument.get(context, "id");
        Integer fadeOut = optional(context, "fade_out", Integer.class);
        if (id.equalsIgnoreCase("all")) {
            int count = 0;
            for (String templateId : LightManager.templateIds()) {
                if (LightManager.removeTemplate(templateId, fadeOut)) {
                    count++;
                }
            }
            context.getSource().sendFeedback(Component.literal("Removed " + count + " model light(s)").withStyle(ChatFormatting.GOLD));
            return count;
        }
        if (!LightManager.removeTemplate(id, fadeOut)) {
            throw error("No model light '" + id + "'");
        }
        context.getSource().sendFeedback(Component.literal("Model light '" + id + "' removed").withStyle(ChatFormatting.GOLD));
        return Command.SINGLE_SUCCESS;
    }

    // ── flashlight
    // ─────────────────────────────────────────────────

    /**
     * flashlight <id> <holder> <color> <scale> <distance> [intensity] [atmosphere] [camera_delay] [angle]: a light like any
     * other (its own id, group, world file and commands), shining from its holder's eyes where they look.
     */
    private static ArgumentBuilder<FabricClientCommandSource, ?> flashlightNode() {
        return literal("flashlight")
                .then(literal("off").executes(context -> {
                    LightManager.turnOffFlashlight();
                    context.getSource().sendFeedback(Component.literal("Flashlights off").withStyle(ChatFormatting.GOLD));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(path(LightCommands::flashlight, 5,
                        arg("id", idArgument(false)),
                        arg("holder", TokenArgument.token(LightCommands::targetSuggestions)),
                        arg("color", colorArgument(false)),
                        arg("scale", FloatArgumentType.floatArg(0.05f, 128.0f)),
                        arg("distance", FloatArgumentType.floatArg(0.0f, 512.0f)),
                        arg("intensity", FloatArgumentType.floatArg(0.0f, MAX_INTENSITY)),
                        arg("atmosphere", FloatArgumentType.floatArg(0.0f, Light.MAX_ATMOSPHERE)),
                        arg("camera_delay", FloatArgumentType.floatArg(0.0f, 200.0f)),
                        arg("angle", FloatArgumentType.floatArg(1.0f, LightShape.MAX_ANGLE))));
    }

    private static String holder(String target) {
        return target.equalsIgnoreCase("@s") ? LightManager.SELF_TARGET : target;
    }

    private static int flashlight(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
        base(context.getSource());
        String id = id(context);
        Light light = new Light(id);
        light.kind = Light.Kind.FOLLOW;
        light.shape = LightShape.FLASHLIGHT;
        light.target = holder(TokenArgument.get(context, "holder"));
        light.color = color(context, "color");
        light.radius = FloatArgumentType.getFloat(context, "scale");
        light.distance = FloatArgumentType.getFloat(context, "distance");
        Float intensity = optional(context, "intensity", Float.class);
        Float atmosphere = optional(context, "atmosphere", Float.class);
        Float delay = optional(context, "camera_delay", Float.class);
        Float angle = optional(context, "angle", Float.class);
        light.intensity = intensity == null ? LightShape.FLASHLIGHT.look.intensity() : intensity;
        light.atmosphere = atmosphere == null ? LightShape.FLASHLIGHT.look.atmosphere() : atmosphere;
        light.angle = angle == null ? LightShape.FLASHLIGHT.look.angle() : angle;
        light.cameraDelay = delay == null ? 0.0f : delay;
        light.fadeIn = 4;
        light.fadeOut = 4;
        // Keeps the stretch and the animations of a flashlight with the same id
        LightManager.get(id).filter(existing -> !existing.server).ifPresent(existing -> {
            light.stretchX = existing.stretchX;
            light.stretchY = existing.stretchY;
            light.stretchZ = existing.stretchZ;
            light.pivotX = existing.pivotX;
            light.pivotY = existing.pivotY;
            light.pivotZ = existing.pivotZ;
            light.animation.read(existing.animation.write());
        });
        LightManager.put(light);
        String note = LightManager.targets(Minecraft.getInstance(), light).isEmpty() ? " (holder not loaded yet: it shows when it is)" : "";
        feedback(context, "Flashlight '" + id + "' held by " + light.target + note, light);
        return Command.SINGLE_SUCCESS;
    }

    // ── animate
    // ────────────────────────────────────────────────────

    private static ArgumentBuilder<FabricClientCommandSource, ?> animateNode() {
        ArgumentBuilder<FabricClientCommandSource, ?> id = arg("id", TokenArgument.token(() -> {
            List<String> ids = new ArrayList<>(LightManager.ids());
            ids.addAll(LightManager.templateIds());
            return ids;
        }));
        id.then(literal("stop")
                .executes(context -> stopAnimation(context, "all"))
                .then(argument("part", TokenArgument.token("all", "preset", "color", "size", "radius"))
                        .executes(context -> stopAnimation(context, TokenArgument.get(context, "part")))));
        id.then(literal("color_transition").then(argument("colors_and_times", StringArgumentType.greedyString())
                .suggests((context, builder) -> {
                    String remaining = builder.getRemaining();
                    int space = remaining.lastIndexOf(' ');
                    var offset = builder.createOffset(builder.getStart() + space + 1);
                    String typed = remaining.substring(space + 1).toLowerCase(Locale.ROOT);
                    for (String color : LightColors.NAMES) {
                        if (color.startsWith(typed)) {
                            offset.suggest(color);
                        }
                    }
                    return offset.buildFuture();
                })
                .executes(LightCommands::animateColors)));
        id.then(literal("size").then(path(LightCommands::animateSize, 3,
                arg("radius", FloatArgumentType.floatArg(0.0f, 512.0f)),
                arg("distance", FloatArgumentType.floatArg(0.0f, 512.0f)),
                arg("time", IntegerArgumentType.integer(1)))));
        id.then(literal("animation_radius").then(path(LightCommands::animateRadius, 1,
                arg("radius", FloatArgumentType.floatArg(0.0f, 512.0f)),
                arg("time", IntegerArgumentType.integer(0)))));
        id.then(literal("add").then(path(context -> animatePreset(context, false), 1,
                arg("preset", TokenArgument.token(LightAnimation.Preset::names)),
                arg("time", IntegerArgumentType.integer(1)),
                arg("amount", FloatArgumentType.floatArg(0.0f, 720.0f)))));
        id.then(literal("remove").then(argument("preset", TokenArgument.token(LightAnimation.Preset::names))
                .executes(LightCommands::removePreset)));
        id.then(path(context -> animatePreset(context, true), 1,
                arg("preset", TokenArgument.token(LightAnimation.Preset::names)),
                arg("time", IntegerArgumentType.integer(1)),
                arg("amount", FloatArgumentType.floatArg(0.0f, 720.0f))));
        return literal("animate").then(id);
    }

    /** The light or model light an animate or stretch command is about. */
    private static Light target(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
        String id = TokenArgument.get(context, "id");
        Optional<Light> light = LightManager.get(id);
        if (light.isEmpty()) {
            light = LightManager.getTemplate(id);
        }
        return light.orElseThrow(() -> error("No light or model light '" + id + "'"));
    }

    /** {@code <preset[+preset]> [time] [amount]} replaces the presets; {@code add} keeps the others. */
    private static int animatePreset(CommandContext<FabricClientCommandSource> context, boolean replace) throws CommandSyntaxException {
        Light light = target(context);
        String text = TokenArgument.get(context, "preset");
        List<LightAnimation.Preset> presets = LightAnimation.parsePresets(text);
        if (presets == null) {
            throw error("Unknown preset '" + text + "': " + String.join(", ", LightAnimation.Preset.names()) + " (combine with +)");
        }
        Integer time = optional(context, "time", Integer.class);
        Float amount = optional(context, "amount", Float.class);
        LightAnimation animation = light.animation;
        if (replace) {
            // Presets that stay keep their settings unless new ones are given
            animation.presets.removeIf(entry -> !presets.contains(entry.preset));
        }
        for (LightAnimation.Preset preset : presets) {
            LightAnimation.Entry existing = animation.entry(preset);
            int entryTime = time != null ? time : existing != null ? existing.time : LightAnimation.DEFAULT_TIME;
            animation.put(preset, entryTime, amount);
        }
        LightManager.markDirty();
        feedback(context, "Animation updated on '" + light.id + "'", light);
        return Command.SINGLE_SUCCESS;
    }

    private static int removePreset(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
        Light light = target(context);
        String text = TokenArgument.get(context, "preset");
        LightAnimation.Preset preset = LightAnimation.Preset.byName(text);
        if (preset == null || !light.animation.remove(preset)) {
            throw error("'" + light.id + "' has no preset '" + text + "'");
        }
        LightManager.markDirty();
        feedback(context, "Animation updated on '" + light.id + "'", light);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code color_transition <color> <ticks> <color> <ticks> ...}: each color and the ticks it takes to blend into the next
     * one.
     */
    private static int animateColors(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
        Light light = target(context);
        String[] parts = StringArgumentType.getString(context, "colors_and_times").trim().split("\\s+");
        List<Integer> colors = new ArrayList<>();
        List<Integer> times = new ArrayList<>();
        boolean paired = parts.length >= 4 && parts.length % 2 == 0 && isInteger(parts[1]);
        if (paired) {
            for (int i = 0; i < parts.length; i += 2) {
                colors.add(parseColor(parts[i]));
                if (!isInteger(parts[i + 1])) {
                    throw error("Expected ticks after '" + parts[i] + "'");
                }
                times.add(Math.max(1, Integer.parseInt(parts[i + 1])));
            }
        } else {
            if (parts.length < 3 || !isInteger(parts[parts.length - 1])) {
                throw error("Use: color_transition <color1> <ticks1> <color2> <ticks2> [...]");
            }
            int loop = Math.max(1, Integer.parseInt(parts[parts.length - 1]));
            for (int i = 0; i < parts.length - 1; i++) {
                colors.add(parseColor(parts[i]));
                times.add(Math.max(1, loop / (parts.length - 1)));
            }
        }
        if (colors.size() < 2) {
            throw error("A transition needs at least two colors");
        }
        light.animation.setTransition(colors.stream().mapToInt(Integer::intValue).toArray(), times.stream().mapToInt(Integer::intValue).toArray());
        LightManager.markDirty();
        feedback(context, "Animation updated on '" + light.id + "'", light);
        return Command.SINGLE_SUCCESS;
    }

    private static boolean isInteger(String text) {
        return text.matches("\\d+");
    }

    private static int animateSize(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
        Light light = target(context);
        light.animation.sizeRadius = FloatArgumentType.getFloat(context, "radius");
        light.animation.sizeDistance = FloatArgumentType.getFloat(context, "distance");
        light.animation.sizeTime = IntegerArgumentType.getInteger(context, "time");
        LightManager.markDirty();
        feedback(context, "Animation updated on '" + light.id + "'", light);
        return Command.SINGLE_SUCCESS;
    }

    /** {@code time} 0 (or none) sets the radius (blocks) of every moving preset; otherwise their radius pulses to it. */
    private static int animateRadius(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
        Light light = target(context);
        float radius = FloatArgumentType.getFloat(context, "radius");
        Integer time = optional(context, "time", Integer.class);
        if (time == null || time == 0) {
            light.animation.setMovementRadius(radius);
            light.animation.radiusPulseTime = 0;
        } else {
            light.animation.radiusPulseTarget = radius;
            light.animation.radiusPulseTime = time;
        }
        LightManager.markDirty();
        feedback(context, "Animation updated on '" + light.id + "'", light);
        return Command.SINGLE_SUCCESS;
    }

    private static int stopAnimation(CommandContext<FabricClientCommandSource> context, String part) throws CommandSyntaxException {
        Light light = target(context);
        LightAnimation animation = light.animation;
        switch (part.toLowerCase(Locale.ROOT)) {
            case "preset" -> animation.presets.clear();
            case "color" -> animation.setTransition(new int[0], new int[0]);
            case "size" -> animation.sizeTime = 0;
            case "radius" -> animation.radiusPulseTime = 0;
            default -> animation.clear();
        }
        LightManager.markDirty();
        context.getSource().sendFeedback(Component.literal("Animation stopped on '" + light.id + "'").withStyle(ChatFormatting.GOLD));
        return Command.SINGLE_SUCCESS;
    }

    // ── stretch
    // ────────────────────────────────────────────────────

    private static ArgumentBuilder<FabricClientCommandSource, ?> stretchNode() {
        return literal("stretch").then(path(LightCommands::stretch, 4,
                arg("id", idArgument(true)),
                arg("x", FloatArgumentType.floatArg(-MAX_STRETCH, MAX_STRETCH)),
                arg("y", FloatArgumentType.floatArg(-MAX_STRETCH, MAX_STRETCH)),
                arg("z", FloatArgumentType.floatArg(-MAX_STRETCH, MAX_STRETCH))));
    }

    /** Stretches a light's shape on its own axes (Y = where it points); negative values flip it. */
    private static int stretch(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
        String id = TokenArgument.get(context, "id");
        Light light = id.equalsIgnoreCase("flashlight") && LightManager.flashlight() != null ? LightManager.flashlight() : target(context);
        light.stretchX = FloatArgumentType.getFloat(context, "x");
        light.stretchY = FloatArgumentType.getFloat(context, "y");
        light.stretchZ = FloatArgumentType.getFloat(context, "z");
        LightManager.markDirty();
        context.getSource().sendFeedback(Component.literal("Stretched '" + light.id + "'").withStyle(ChatFormatting.GOLD));
        return Command.SINGLE_SUCCESS;
    }

    private static ArgumentBuilder<FabricClientCommandSource, ?> pivotNode() {
        return literal("pivot").then(path(LightCommands::pivot, 4,
                arg("id", idArgument(true)),
                arg("x", FloatArgumentType.floatArg(-MAX_STRETCH, MAX_STRETCH)),
                arg("y", FloatArgumentType.floatArg(-MAX_STRETCH, MAX_STRETCH)),
                arg("z", FloatArgumentType.floatArg(-MAX_STRETCH, MAX_STRETCH))));
    }

    /** Where on the light's own axes the stretch grows from (0 0 0 = its middle). */
    private static int pivot(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
        String id = TokenArgument.get(context, "id");
        Light light = id.equalsIgnoreCase("flashlight") && LightManager.flashlight() != null ? LightManager.flashlight() : target(context);
        light.pivotX = FloatArgumentType.getFloat(context, "x");
        light.pivotY = FloatArgumentType.getFloat(context, "y");
        light.pivotZ = FloatArgumentType.getFloat(context, "z");
        LightManager.markDirty();
        context.getSource().sendFeedback(Component.literal("Pivot of '" + light.id + "' moved").withStyle(ChatFormatting.GOLD));
        return Command.SINGLE_SUCCESS;
    }

    // ── angle
    // ──────────────────────────────────────────────────────

    /** How much the light also makes what it reaches glow by itself. */
    private static ArgumentBuilder<FabricClientCommandSource, ?> luminosityNode() {
        return literal("luminosity").then(argument("id", idArgument(true))
                .then(argument("amount", FloatArgumentType.floatArg(0.0f, 10.0f)).executes(LightCommands::luminosity)));
    }

    private static int luminosity(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
        Light light = target(context);
        light.luminosity = FloatArgumentType.getFloat(context, "amount");
        LightManager.markDirty();
        context.getSource().sendFeedback(Component.literal("Luminosity of '" + light.id + "': " + CommandWriter.number(light.luminosity))
                .withStyle(ChatFormatting.GOLD));
        return Command.SINGLE_SUCCESS;
    }

    private static ArgumentBuilder<FabricClientCommandSource, ?> angleNode() {
        return literal("angle").then(argument("id", idArgument(true))
                .then(argument("degrees", FloatArgumentType.floatArg(1.0f, LightShape.MAX_ANGLE)).executes(LightCommands::angle)));
    }

    /** How wide cones, spotlights and flashlights open. */
    private static int angle(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
        String id = TokenArgument.get(context, "id");
        Light light = id.equalsIgnoreCase("flashlight") && LightManager.flashlight() != null ? LightManager.flashlight() : target(context);
        if (!light.shape.hasAngle()) {
            throw error("Only cone, inverted_cone, spotlight, flashlight and siren have an angle");
        }
        light.angle = FloatArgumentType.getFloat(context, "degrees");
        LightManager.markDirty();
        context.getSource().sendFeedback(Component.literal("Angle of '" + light.id + "': " + CommandWriter.number(light.angle) + "°").withStyle(ChatFormatting.GOLD));
        return Command.SINGLE_SUCCESS;
    }

    private static ArgumentBuilder<FabricClientCommandSource, ?> seeThroughNode() {
        return literal("seethrough").then(argument("id", idArgument(true))
                .then(argument("value", BoolArgumentType.bool()).executes(LightCommands::seeThrough)));
    }

    /** Whether the light passes through solid blocks. */
    private static int seeThrough(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
        String id = TokenArgument.get(context, "id");
        Light light = id.equalsIgnoreCase("flashlight") && LightManager.flashlight() != null ? LightManager.flashlight() : target(context);
        light.seeThrough = BoolArgumentType.getBool(context, "value");
        LightManager.markDirty();
        context.getSource().sendFeedback(Component.literal("'" + light.id + "' " + (light.seeThrough ? "passes through blocks" : "is stopped by solid blocks"))
                .withStyle(ChatFormatting.GOLD));
        return Command.SINGLE_SUCCESS;
    }

    // ── group
    // ──────────────────────────────────────────────────────

    private static TokenArgument groupArgument() {
        return TokenArgument.token(LightGroups::names);
    }

    /** Light ids separated by spaces; suggests the id being typed. */
    private static CompletableFuture<Suggestions> suggestLightIds(CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) {
        String typed = builder.getRemaining();
        int space = typed.lastIndexOf(' ');
        SuggestionsBuilder last = builder.createOffset(builder.getStart() + space + 1);
        String prefix = typed.substring(space + 1).toLowerCase(Locale.ROOT);
        for (String id : LightManager.ids()) {
            if (id.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                last.suggest(id);
            }
        }
        return last.buildFuture();
    }

    private static ArgumentBuilder<FabricClientCommandSource, ?> groupNode() {
        return literal("group")
                .then(literal("list").executes(LightCommands::groupList))
                .then(literal("add").then(argument("group", groupArgument())
                        .executes(context -> groupAdd(context, ""))
                        .then(argument("lights", StringArgumentType.greedyString()).suggests(LightCommands::suggestLightIds)
                                .executes(context -> groupAdd(context, StringArgumentType.getString(context, "lights"))))))
                .then(literal("remove").then(argument("group", groupArgument())
                        .executes(context -> groupRemove(context, ""))
                        .then(argument("lights", StringArgumentType.greedyString()).suggests(LightCommands::suggestLightIds)
                                .executes(context -> groupRemove(context, StringArgumentType.getString(context, "lights"))))))
                .then(literal("modify").then(argument("group", groupArgument())
                        .then(literal("on").executes(context -> groupSwitch(context, true)))
                        .then(literal("off").executes(context -> groupSwitch(context, false)))
                        .then(literal("animations")
                                .then(literal("on").executes(context -> groupAnimations(context, true)))
                                .then(literal("off").executes(context -> groupAnimations(context, false))))
                        .then(literal("rename").then(argument("name", TokenArgument.token()).executes(LightCommands::groupRename)))));
    }

    private static LightGroups.Group existingGroup(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
        String name = TokenArgument.get(context, "group");
        LightGroups.Group group = LightGroups.get(name);
        if (group == null) {
            throw error("No group '" + name + "' (groups: " + String.join(", ", LightGroups.names()) + ")");
        }
        return group;
    }

    /** Creates the group and puts the given lights in it. */
    private static int groupAdd(CommandContext<FabricClientCommandSource> context, String lights) throws CommandSyntaxException {
        base(context.getSource());
        String name = TokenArgument.get(context, "group");
        if (!name.matches("[A-Za-z0-9_.+:-]{1,48}")) {
            throw error("Group names use letters, numbers and _ - . + :");
        }
        LightGroups.Group group = LightGroups.add(name);
        int moved = 0;
        for (String id : lights.trim().split("\\s+")) {
            if (id.isEmpty()) {
                continue;
            }
            Light light = LightManager.get(id).orElseThrow(() -> error("No light '" + id + "' here"));
            LightGroups.assign(light, group.name);
            moved++;
        }
        context.getSource().sendFeedback(Component.literal(moved == 0 ? "Group '" + group.name + "' ready"
                : "Put " + moved + " light(s) in group '" + group.name + "'").withStyle(ChatFormatting.GOLD));
        return Command.SINGLE_SUCCESS;
    }

    /** Without lights deletes the group (its lights stay, ungrouped); with lights takes them out of it. */
    private static int groupRemove(CommandContext<FabricClientCommandSource> context, String lights) throws CommandSyntaxException {
        base(context.getSource());
        LightGroups.Group group = existingGroup(context);
        if (lights.isBlank()) {
            LightGroups.remove(group.name);
            context.getSource().sendFeedback(Component.literal("Deleted group '" + group.name + "' (its lights stay)").withStyle(ChatFormatting.GOLD));
            return Command.SINGLE_SUCCESS;
        }
        int moved = 0;
        for (String id : lights.trim().split("\\s+")) {
            Light light = LightManager.get(id).orElseThrow(() -> error("No light '" + id + "' here"));
            if (light.group.equalsIgnoreCase(group.name)) {
                LightGroups.assign(light, "");
                moved++;
            }
        }
        context.getSource().sendFeedback(Component.literal("Took " + moved + " light(s) out of '" + group.name + "'").withStyle(ChatFormatting.GOLD));
        return Command.SINGLE_SUCCESS;
    }

    private static int groupSwitch(CommandContext<FabricClientCommandSource> context, boolean on) throws CommandSyntaxException {
        LightGroups.Group group = existingGroup(context);
        LightGroups.setEnabled(group, on);
        context.getSource().sendFeedback(Component.literal("Group '" + group.name + "' " + (on ? "on" : "off")).withStyle(ChatFormatting.GOLD));
        return Command.SINGLE_SUCCESS;
    }

    private static int groupAnimations(CommandContext<FabricClientCommandSource> context, boolean on) throws CommandSyntaxException {
        LightGroups.Group group = existingGroup(context);
        LightGroups.setAnimations(group, on);
        context.getSource().sendFeedback(Component.literal("Animations of '" + group.name + "' " + (on ? "on" : "off")).withStyle(ChatFormatting.GOLD));
        return Command.SINGLE_SUCCESS;
    }

    private static int groupRename(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
        LightGroups.Group group = existingGroup(context);
        String name = TokenArgument.get(context, "name");
        String old = group.name;
        if (!name.matches("[A-Za-z0-9_.+:-]{1,48}") || !LightGroups.rename(old, name)) {
            throw error("Cannot rename '" + old + "' to '" + name + "'");
        }
        context.getSource().sendFeedback(Component.literal("Renamed group '" + old + "' to '" + name + "'").withStyle(ChatFormatting.GOLD));
        return Command.SINGLE_SUCCESS;
    }

    private static int groupList(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        List<LightGroups.Group> groups = LightGroups.groups();
        if (groups.isEmpty()) {
            source.sendFeedback(Component.literal("No groups yet: /customlight group add <name> [lights]").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        for (LightGroups.Group group : groups) {
            List<String> members = new ArrayList<>();
            for (Light light : LightManager.localLights()) {
                if (light.group.equalsIgnoreCase(group.name)) {
                    members.add(light.id);
                }
            }
            source.sendFeedback(Component.literal(group.name + (group.enabled ? "" : " [off]") + (group.animations ? "" : " [still]") + ": "
                    + (members.isEmpty() ? "-" : String.join(", ", members))).withStyle(group.enabled ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        }
        return groups.size();
    }

    // ── copy / paste
    // ───────────────────────────────────────────────

    private static int copy(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
        String id = TokenArgument.get(context, "id");
        Light light = id.equalsIgnoreCase("flashlight") && LightManager.flashlight() != null ? LightManager.flashlight() : target(context);
        Minecraft.getInstance().keyboardHandler.setClipboard(LightPaste.command(List.of(light)));
        context.getSource().sendFeedback(Component.literal("Copied '" + light.id + "': paste it with /customlight paste").withStyle(ChatFormatting.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private static int copyAll(CommandContext<FabricClientCommandSource> context) {
        List<Light> lights = new ArrayList<>(LightManager.visibleLights());
        for (String id : LightManager.templateIds()) {
            LightManager.getTemplate(id).ifPresent(lights::add);
        }
        Light flashlight = LightManager.flashlight();
        if (flashlight != null && !flashlight.removing) {
            lights.add(flashlight);
        }
        if (lights.isEmpty()) {
            context.getSource().sendError(Component.literal("No lights to copy"));
            return 0;
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(LightPaste.command(lights));
        context.getSource().sendFeedback(Component.literal("Copied " + lights.size() + " light(s): paste them with /customlight paste").withStyle(ChatFormatting.GREEN));
        return lights.size();
    }

    private static int paste(CommandContext<FabricClientCommandSource> context, String text) throws CommandSyntaxException {
        base(context.getSource());
        try {
            List<String> ids = LightPaste.apply(text);
            context.getSource().sendFeedback(Component.literal("Pasted: " + String.join(", ", ids)).withStyle(ChatFormatting.GREEN));
            return ids.size();
        } catch (IllegalArgumentException exception) {
            throw error("Paste failed: " + exception.getMessage());
        }
    }

    // ── list and misc
    // ──────────────────────────────────────────────

    private static int list(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        List<Light> lights = LightManager.visibleLights();
        source.sendFeedback(Component.literal("Lights here: " + lights.size()).withStyle(ChatFormatting.GOLD));
        for (Light light : lights) {
            String where = light.isFollow() ? "follows " + light.target
                    : CommandWriter.number(light.x) + " " + CommandWriter.number(light.y) + " " + CommandWriter.number(light.z);
            int shown = LightColors.resolve(light.color, LightManager.ticks());
            MutableComponent line = Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(light.id).withStyle(Style.EMPTY.withColor(shown == 0 ? 0x888888 : shown)))
                    .append(Component.literal(" " + light.shape.id + " " + where + (light.server ? " [server]" : "")).withStyle(ChatFormatting.GRAY));
            line.withStyle(style -> style
                    .withClickEvent(com.cotii.customlights.client.compat.Compat.runCommand("/customlight editor " + light.id))
                    .withHoverEvent(com.cotii.customlights.client.compat.Compat.showText(Component.literal("Click to edit"))));
            source.sendFeedback(line);
        }
        List<String> templates = LightManager.templateIds();
        if (!templates.isEmpty()) {
            source.sendFeedback(Component.literal("Model lights: " + String.join(", ", templates)).withStyle(ChatFormatting.GOLD));
        }
        Light flashlight = LightManager.flashlight();
        if (flashlight != null && !flashlight.removing) {
            source.sendFeedback(Component.literal("Flashlight: on").withStyle(ChatFormatting.GOLD));
        }
        return lights.size();
    }

    private static int openEditor(String id) {
        EditorOpener.request(id);
        return Command.SINGLE_SUCCESS;
    }

    private static int toggle(CommandContext<FabricClientCommandSource> context) {
        LightRenderer.enabled = !LightRenderer.enabled;
        context.getSource().sendFeedback(Component.literal("Custom lights " + (LightRenderer.enabled ? "on" : "off")).withStyle(ChatFormatting.GOLD));
        return Command.SINGLE_SUCCESS;
    }

    private static int quality(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
        String level = TokenArgument.get(context, "level").toLowerCase(Locale.ROOT);
        LightRenderer.setQuality(switch (level) {
            case "low" -> LightRenderer.Quality.LOW;
            case "medium" -> LightRenderer.Quality.MEDIUM;
            case "high" -> LightRenderer.Quality.HIGH;
            case "ultra" -> LightRenderer.Quality.ULTRA;
            default -> throw error("Use low, medium, high or ultra");
        });
        context.getSource().sendFeedback(Component.literal("Light quality: " + level).withStyle(ChatFormatting.GOLD));
        return Command.SINGLE_SUCCESS;
    }

    private static int stats(CommandContext<FabricClientCommandSource> context) {
        // Measure the GPU for a few seconds; running stats again shows the result
        String gpu = com.cotii.customlights.client.render.GpuTimer.report();
        com.cotii.customlights.client.render.GpuTimer.profileUntil = System.currentTimeMillis() + 3000L;
        context.getSource().sendFeedback(Component.literal(gpu == null ? "Measuring GPU time: run stats again in a few seconds" : "GPU: " + gpu)
                .withStyle(ChatFormatting.GRAY));
        context.getSource().sendFeedback(Component.literal("Lights drawn last frame: " + LightRenderer.lastDrawn + " of " + LightRenderer.lastCollected + " (with atmosphere: " + LightRenderer.lastHazy + ")"
                + " / known here: " + LightManager.ids().size() + " / model lights: " + LightManager.templateIds().size()).withStyle(ChatFormatting.GOLD));
        return Command.SINGLE_SUCCESS;
    }

    private static int help(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        String[] lines = {
                "set <id> <x> <y> <z> <shape> <color> <radius> <distance> <intensity> <atmosphere 0-10> <fade_in> <fade_out> [yaw] [pitch]",
                "follow <id> <player|uuid|tag:<tag>|@self> <offX> <offY> <offZ> <shape> ... <fade_out> [yaw pitch] [visible_firstperson]",
                "remove <id|all|flashlight> [fade_out]",
                "move <id> <x> <y> <z> <smooth|linear|step|bounce> <time>   (~ stays where it is)",
                "model_id <id> <x> <y> <z> <shape> <color> <radius> <opacity> <light> <fade_in> <fade_out> [distance] [yaw] [pitch]",
                "model_remove <id|all> [fade_out]",
                "flashlight <color> <scale> <distance> [intensity] [atmosphere] [camera_delay] [angle] [target] | flashlight target <player|uuid|tag:<tag>|@self> | flashlight off",
                "angle <id> <degrees>   (cones, spotlights, flashlights and sirens)",
                "seethrough <id> <true|false>   (false: solid blocks stop the light)",
                "group add <group> [lights...] | group remove <group> [lights...] | group modify <group> on|off|animations on|off|rename <name> | group list",
                "animate <id> <preset[+preset]> [time] [amount] | add ... | remove <preset> | color_transition <c1> <t1> <c2> <t2>... | size <r> <d> <t> | animation_radius <r> [t] | stop",
                "stretch <id> <x> <y> <z>   (-64 to 64)",
                "pivot <id> <x> <y> <z>   where the stretch grows from",
                "copy [id]   paste [text]   list   editor [id]   toggle   quality <low|medium|high|ultra>   stats",
                "radius = size of the lit shape, distance = how far its light blurs out (0 = hard edge)",
                "Dark colors make darkness; color 'party' cycles every color",
                "Shapes: " + String.join(", ", LightShape.NAMES),
                "Presets: " + String.join(", ", LightAnimation.Preset.names())
        };
        source.sendFeedback(Component.literal("CustomLights (/customlight)").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        for (String line : lines) {
            source.sendFeedback(Component.literal(" " + line).withStyle(ChatFormatting.GRAY));
        }
        return Command.SINGLE_SUCCESS;
    }

    /** Feedback with a [copy] button that copies the light as a paste command. */
    private static void feedback(CommandContext<FabricClientCommandSource> context, String message, Light light) {
        MutableComponent text = Component.literal(message).withStyle(ChatFormatting.GOLD);
        String command = LightPaste.command(light, LightPaste.typeOf(light));
        String hover = command.length() > CHAT_LIMIT ? "Too long for chat: after copying, run /customlight paste" : command;
        text.append(Component.literal(" [copy]").withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withClickEvent(com.cotii.customlights.client.compat.Compat.copyText(command))
                .withHoverEvent(com.cotii.customlights.client.compat.Compat.showText(Component.literal(hover)))));
        context.getSource().sendFeedback(text);
    }
}
