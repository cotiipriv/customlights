package com.cotii.customlights.forge;

import com.cotii.customlights.Customlights;
import com.cotii.customlights.client.ClientSetup;
import com.cotii.customlights.forge.fabric.client.command.v2.ClientCommandRegistrationCallback;
import com.cotii.customlights.forge.fabric.client.command.v2.FabricClientCommandSource;
import com.cotii.customlights.forge.fabric.client.event.lifecycle.v1.ClientChunkEvents;
import com.cotii.customlights.forge.fabric.client.event.lifecycle.v1.ClientLifecycleEvents;
import com.cotii.customlights.forge.fabric.client.event.lifecycle.v1.ClientTickEvents;
import com.cotii.customlights.forge.fabric.client.keybinding.v1.KeyBindingHelper;
import com.cotii.customlights.forge.fabric.client.networking.v1.ClientPlayConnectionEvents;
import com.cotii.customlights.forge.fabric.client.rendering.v1.WorldRenderContext;
import com.cotii.customlights.forge.fabric.client.rendering.v1.WorldRenderEvents;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Forge entry point. */
@Mod(Customlights.MOD_ID)
public final class CustomlightsForge {
    private static final CommandDispatcher<FabricClientCommandSource> COMMANDS = new CommandDispatcher<>();
    private static boolean commandsBuilt;

    public CustomlightsForge() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        ClientSetup.initialize();
        FMLJavaModLoadingContext.get().getModEventBus().addListener(CustomlightsForge::onRegisterKeys);
        MinecraftForge.EVENT_BUS.addListener(CustomlightsForge::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(CustomlightsForge::onRenderStage);
        MinecraftForge.EVENT_BUS.addListener(CustomlightsForge::onChunkLoad);
        MinecraftForge.EVENT_BUS.addListener(CustomlightsForge::onChunkUnload);
        MinecraftForge.EVENT_BUS.addListener(CustomlightsForge::onLoggingIn);
        MinecraftForge.EVENT_BUS.addListener(CustomlightsForge::onLoggingOut);
        MinecraftForge.EVENT_BUS.addListener(CustomlightsForge::onShutdown);
        MinecraftForge.EVENT_BUS.addListener(CustomlightsForge::onRegisterCommands);
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        KeyBindingHelper.MAPPINGS.forEach(event::register);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Minecraft minecraft = Minecraft.getInstance();
            ClientTickEvents.END_CLIENT_TICK.fire(listener -> listener.onEndTick(minecraft));
        }
    }

    private static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        WorldRenderContext context = new WorldRenderContext() {
            @Override
            public Camera camera() {
                return event.getCamera();
            }

            @Override
            public PoseStack matrixStack() {
                return event.getPoseStack();
            }

            @Override
            public MultiBufferSource consumers() {
                return Minecraft.getInstance().renderBuffers().bufferSource();
            }

            @Override
            public float tickDelta() {
                return event.getPartialTick();
            }
        };
        WorldRenderEvents.AFTER_ENTITIES.fire(listener -> listener.afterEntities(context));
    }

    private static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ClientLevel level && event.getChunk() instanceof LevelChunk chunk) {
            ClientChunkEvents.CHUNK_LOAD.fire(listener -> listener.onChunkLoad(level, chunk));
        }
    }

    private static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel level && event.getChunk() instanceof LevelChunk chunk) {
            ClientChunkEvents.CHUNK_UNLOAD.fire(listener -> listener.onChunkUnload(level, chunk));
        }
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientPlayConnectionEvents.JOIN.fire(listener -> listener.onPlayReady(minecraft.getConnection(), null, minecraft));
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientPlayConnectionEvents.DISCONNECT.fire(listener -> listener.onPlayDisconnect(minecraft.getConnection(), minecraft));
    }

    private static void onShutdown(GameShuttingDownEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLifecycleEvents.CLIENT_STOPPING.fire(listener -> listener.onClientStopping(minecraft));
    }

    // ── Client commands
    // ────────────────────────────────────────────

    private static void onRegisterCommands(RegisterClientCommandsEvent event) {
        if (!commandsBuilt) {
            commandsBuilt = true;
            ClientCommandRegistrationCallback.EVENT.fire(callback -> callback.register(COMMANDS, event.getBuildContext()));
        }
        for (CommandNode<FabricClientCommandSource> root : COMMANDS.getRoot().getChildren()) {
            event.getDispatcher().register(Commands.literal(root.getName())
                    .executes(context -> run(context.getInput()))
                    .then(Commands.argument("arguments", StringArgumentType.greedyString())
                            .suggests((context, builder) -> suggest(builder))
                            .executes(context -> run(context.getInput()))));
        }
    }

    private static int run(String input) {
        FabricClientCommandSource source = FabricClientCommandSource.local();
        String command = input.startsWith("/") ? input.substring(1) : input;
        try {
            return COMMANDS.execute(command, source);
        } catch (CommandSyntaxException exception) {
            source.sendError(Component.literal(exception.getMessage()));
            return 0;
        } catch (RuntimeException exception) {
            Customlights.LOGGER.warn("CustomLights command failed: {}", command, exception);
            source.sendError(Component.literal("Command failed: " + exception.getMessage()));
            return 0;
        }
    }

    /** Suggestions of the mod's own tree for what was typed so far, placed where the typed text is. */
    private static CompletableFuture<Suggestions> suggest(SuggestionsBuilder builder) {
        String input = builder.getInput();
        int skip = input.startsWith("/") ? 1 : 0;
        ParseResults<FabricClientCommandSource> parse = COMMANDS.parse(input.substring(skip), FabricClientCommandSource.local());
        return COMMANDS.getCompletionSuggestions(parse).thenApply(found -> {
            if (skip == 0) {
                return found;
            }
            List<Suggestion> shifted = new ArrayList<>();
            for (Suggestion suggestion : found.getList()) {
                shifted.add(new Suggestion(new com.mojang.brigadier.context.StringRange(suggestion.getRange().getStart() + skip,
                        suggestion.getRange().getEnd() + skip), suggestion.getText(), suggestion.getTooltip()));
            }
            return Suggestions.create(input, shifted);
        });
    }
}
