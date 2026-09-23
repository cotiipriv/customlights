package com.cotii.customlights.client.mixin;

import com.cotii.customlights.client.light.ModelMarkerHolder;
import com.cotii.customlights.client.light.ModelMarkers;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;

/**
 * Removes the cubes of {@code customlights_<id>} Blockbench groups from JSON models and records the group pivots as light
 * markers.
 */
@Mixin(BlockModel.Deserializer.class)
public abstract class BlockModelDeserializerMixin {
    @Unique
    private static final ThreadLocal<List<ModelMarkers.Marker>> CUSTOMLIGHTS$PENDING = new ThreadLocal<>();

    @ModifyReturnValue(method = "getElements", at = @At("RETURN"))
    private List<BlockElement> customlights$removeMarkerCubes(List<BlockElement> elements, JsonDeserializationContext context, JsonObject json) {
        Set<Integer> removed = ModelMarkers.newIndexSet();
        List<ModelMarkers.Marker> markers = ModelMarkers.readGroups(json, removed);
        if (markers.isEmpty()) {
            CUSTOMLIGHTS$PENDING.remove();
            return elements;
        }
        CUSTOMLIGHTS$PENDING.set(markers);
        return ModelMarkers.without(elements, removed);
    }

    @ModifyReturnValue(method = "deserialize(Lcom/google/gson/JsonElement;Ljava/lang/reflect/Type;Lcom/google/gson/JsonDeserializationContext;)Lnet/minecraft/client/renderer/block/model/BlockModel;", at = @At("RETURN"))
    private BlockModel customlights$attachMarkers(BlockModel model, JsonElement json, Type type, JsonDeserializationContext context) {
        List<ModelMarkers.Marker> markers = CUSTOMLIGHTS$PENDING.get();
        if (markers != null) {
            CUSTOMLIGHTS$PENDING.remove();
            ((ModelMarkerHolder) model).customlights$setMarkers(markers);
        }
        return model;
    }
}
