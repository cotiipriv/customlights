package com.cotii.customlights.client.light;


import java.util.List;

/** Implemented by {@link net.minecraft.client.renderer.block.model.BlockModel} through the CustomLights BlockModel mixin. */
public interface ModelMarkerHolder {
    List<ModelMarkers.Marker> customlights$markers();

    void customlights$setMarkers(List<ModelMarkers.Marker> markers);
}
