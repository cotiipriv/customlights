package com.cotii.customlights.client.mixin;

import net.minecraft.client.entity.ClientAvatarState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 1.21.9 and later: the walking sway of a player (the development test bot makes the view bob). */
@Mixin(ClientAvatarState.class)
public interface ClientAvatarStateAccessor {
    @Accessor("walkDist")
    void customlights$setWalkDist(float value);

    @Accessor("walkDistO")
    void customlights$setWalkDistO(float value);

    @Accessor("bob")
    void customlights$setBob(float value);

    @Accessor("bobO")
    void customlights$setBobO(float value);
}
