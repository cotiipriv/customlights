package com.cotii.customlights.neoforge.fabric.client.command.v2;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/** Forge build: the source of a client command, answering in the player's chat. */
public interface FabricClientCommandSource {
    void sendFeedback(Component message);

    void sendError(Component message);

    Minecraft getClient();

    LocalPlayer getPlayer();

    Vec3 getPosition();

    /** The local player's source. */
    static FabricClientCommandSource local() {
        return new FabricClientCommandSource() {
            @Override
            public void sendFeedback(Component message) {
                LocalPlayer player = Minecraft.getInstance().player;
                if (player != null) {
                    player.sendSystemMessage(message);
                }
            }

            @Override
            public void sendError(Component message) {
                LocalPlayer player = Minecraft.getInstance().player;
                if (player != null) {
                    player.sendSystemMessage(Component.empty().append(message).withStyle(ChatFormatting.RED));
                }
            }

            @Override
            public Minecraft getClient() {
                return Minecraft.getInstance();
            }

            @Override
            public LocalPlayer getPlayer() {
                return Minecraft.getInstance().player;
            }

            @Override
            public Vec3 getPosition() {
                LocalPlayer player = getPlayer();
                return player == null ? Vec3.ZERO : player.position();
            }
        };
    }
}
