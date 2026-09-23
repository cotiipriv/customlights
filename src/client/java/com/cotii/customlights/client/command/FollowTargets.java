package com.cotii.customlights.client.command;

import com.cotii.customlights.client.light.LightManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

import java.util.ArrayList;
import java.util.List;

/** What a follow light can follow: online players, and the entity you look at (by UUID). */
public final class FollowTargets {
    /** A target value and how to show it. */
    public record Target(String value, String label) {
    }

    private FollowTargets() {
    }

    /** UUID of the entity the player looks at (empty when none). */
    public static List<String> entityUuids(Minecraft minecraft) {
        List<String> uuids = new ArrayList<>();
        for (Target target : entities(minecraft)) {
            uuids.add(target.value());
        }
        return uuids;
    }

    /** The entity under the crosshair, if any (players are listed by name instead). */
    public static List<Target> entities(Minecraft minecraft) {
        List<Target> targets = new ArrayList<>();
        if (minecraft.level == null || minecraft.player == null) {
            return targets;
        }
        if (minecraft.hitResult instanceof EntityHitResult hit && !(hit.getEntity() instanceof Player)) {
            Entity entity = hit.getEntity();
            String name = entity.hasCustomName() ? entity.getCustomName().getString() : BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
            targets.add(new Target(entity.getStringUUID(), "Looked at: " + name));
        }
        return targets;
    }

    /** Yourself, the online players, then the entity you look at. */
    public static List<Target> all(Minecraft minecraft) {
        List<Target> targets = new ArrayList<>();
        targets.add(new Target(LightManager.SELF_TARGET, "Me (" + LightManager.SELF_TARGET + ")"));
        if (minecraft.getConnection() != null) {
            for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
                String name = info.getProfile().getName();
                targets.add(new Target(name, name));
            }
        }
        targets.addAll(entities(minecraft));
        return targets;
    }
}
