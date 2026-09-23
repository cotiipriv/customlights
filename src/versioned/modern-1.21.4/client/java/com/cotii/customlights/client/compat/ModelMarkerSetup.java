package com.cotii.customlights.client.compat;

import com.cotii.customlights.client.light.MarkerLights;
import com.cotii.customlights.client.light.ModelMarkerHolder;
import com.cotii.customlights.client.light.ModelMarkers;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** 1.21.4: the baked model reports its own id. */
public final class ModelMarkerSetup {
    private ModelMarkerSetup() {
    }

    public static void register() {
        ModelLoadingPlugin.register(context -> {
            ModelMarkers.clear();
            context.modifyModelAfterBake().register((model, bakeContext) -> {
                if (model == null) {
                    return null;
                }
                ResourceLocation id = bakeContext.id();
                String whole = id == null ? null : MarkerLights.templateOf(id.getPath().substring(id.getPath().lastIndexOf('/') + 1));
                if (whole != null) {
                    ModelMarkers.put(model, new ModelMarkers.Entry(List.of(new ModelMarkers.Marker(whole, 0.5f, 0.5f, 0.5f, 0)), true));
                } else if (bakeContext.sourceModel() instanceof ModelMarkerHolder holder && !holder.customlights$markers().isEmpty()) {
                    ModelMarkers.put(model, new ModelMarkers.Entry(holder.customlights$markers(), false));
                }
                return model;
            });
        });
    }
}
