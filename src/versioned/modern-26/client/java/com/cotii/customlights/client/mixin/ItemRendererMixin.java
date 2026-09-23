package com.cotii.customlights.client.mixin;

import com.cotii.customlights.client.compat.ItemLayerTracker;
import com.cotii.customlights.client.light.MarkerLights;
import com.cotii.customlights.client.light.ModelMarkers;
import com.cotii.customlights.client.light.ObjectLights;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * 26.x (as 1.21.9, the render type now comes with the quads): an item layer is handed over (already placed for its display
 * context) to be drawn later, so its light is placed as it is handed over.
 */
@Mixin(targets = "net.minecraft.client.renderer.item.ItemStackRenderState$LayerRenderState")
public abstract class ItemRendererMixin {
    @WrapOperation(method = "submit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitItem(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;III[ILjava/util/List;Lnet/minecraft/client/renderer/item/ItemStackRenderState$FoilType;)V"))
    private void customlights$markers(net.minecraft.client.renderer.SubmitNodeCollector collector, PoseStack poseStack, ItemDisplayContext context,
                                      int light, int overlay, int outline, int[] tints, List<BakedQuad> model,
                                      ItemStackRenderState.FoilType foil, Operation<Void> original) {
        ItemStack stack = ItemLayerTracker.current();
        if (stack != null && ItemLayerTracker.claim() && MarkerLights.phase() != MarkerLights.Phase.NONE) {
            if (com.cotii.customlights.client.gui.ObjectPreview.drawingItem) {
                com.cotii.customlights.client.gui.ObjectPreview.recordItemPose(poseStack.last().pose());
            }
            if (ObjectLights.hasItemRules()) {
                ObjectLights.Rule rule = ObjectLights.itemRule(stack);
                ObjectLights.Display display = rule == null ? null : rule.display(context);
                if (display != null && display.on) {
                    PoseStack.Pose pose = poseStack.last();
                    MarkerLights.submit(ObjectLights.markerKey(rule, context), pose.pose(), pose.normal(), 0.5f + (float) display.x / 16.0f,
                            0.5f + (float) display.y / 16.0f, 0.5f + (float) display.z / 16.0f,
                            System.identityHashCode(model) * 31 + context.ordinal());
                }
            }
        }
        ModelMarkers.Entry entry = ModelMarkers.get(model);
        if (entry != null) {
            PoseStack.Pose pose = poseStack.last();
            for (ModelMarkers.Marker marker : entry.markers()) {
                MarkerLights.submit(marker.template(), pose.pose(), pose.normal(), marker.x(), marker.y(), marker.z(),
                        System.identityHashCode(model) * 31 + marker.key());
            }
            if (entry.hideModel()) {
                return;
            }
        }
        original.call(collector, poseStack, context, light, overlay, outline, tints, model, foil);
    }
}
