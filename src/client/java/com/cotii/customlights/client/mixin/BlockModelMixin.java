package com.cotii.customlights.client.mixin;

import com.cotii.customlights.client.light.ModelMarkerHolder;
import com.cotii.customlights.client.light.ModelMarkers;
import net.minecraft.client.renderer.block.model.BlockModel;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;

/** Keeps the light markers read from a model's Blockbench groups. */
@Mixin(BlockModel.class)
public abstract class BlockModelMixin implements ModelMarkerHolder {
    @Shadow
    @Nullable
    protected BlockModel parent;

    @Unique
    private List<ModelMarkers.Marker> customlights$markers = List.of();

    @Override
    public List<ModelMarkers.Marker> customlights$markers() {
        // Like elements, a model without its own markers uses its parent's
        if (customlights$markers.isEmpty() && parent instanceof ModelMarkerHolder holder && parent != (Object) this) {
            return holder.customlights$markers();
        }
        return customlights$markers;
    }

    @Override
    public void customlights$setMarkers(List<ModelMarkers.Marker> markers) {
        customlights$markers = List.copyOf(markers);
    }
}
