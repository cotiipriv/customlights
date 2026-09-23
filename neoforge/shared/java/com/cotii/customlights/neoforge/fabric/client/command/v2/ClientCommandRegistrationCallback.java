package com.cotii.customlights.neoforge.fabric.client.command.v2;

import com.cotii.customlights.neoforge.fabric.event.Event;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;

/**
 * Forge build: the client commands are built on a dispatcher of their own, which the Forge glue exposes to Forge's client
 * command dispatcher (input and Tab suggestions are passed through).
 */
@FunctionalInterface
public interface ClientCommandRegistrationCallback {
    Event<ClientCommandRegistrationCallback> EVENT = new Event<>();

    void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext registryAccess);
}
