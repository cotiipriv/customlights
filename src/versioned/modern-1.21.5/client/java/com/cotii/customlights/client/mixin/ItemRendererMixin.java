package com.cotii.customlights.client.mixin;

import com.cotii.customlights.client.compat.ItemLayerTracker;
import com.cotii.customlights.client.light.MarkerLights;
import com.cotii.customlights.client.light.ModelMarkers;
import com.cotii.customlights.client.light.ObjectLights;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.5 and later: an item is drawn (from plain quad lists) one layer at a time, each already placed for its display
 * context.
 */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true)
    private static void customlights$markers(ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay,
                                             int[] tints, java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> model, RenderType renderType, ItemStackRenderState.FoilType foil,
                                             CallbackInfo ci) {
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
        if (entry == null) {
            return;
        }
        PoseStack.Pose pose = poseStack.last();
        for (ModelMarkers.Marker marker : entry.markers()) {
            MarkerLights.submit(marker.template(), pose.pose(), pose.normal(), marker.x(), marker.y(), marker.z(),
                    System.identityHashCode(model) * 31 + marker.key());
        }
        if (entry.hideModel()) {
            ci.cancel();
        }
    }
}
