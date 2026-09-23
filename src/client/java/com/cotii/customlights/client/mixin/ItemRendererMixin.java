package com.cotii.customlights.client.mixin;

import com.cotii.customlights.client.light.MarkerLights;
import com.cotii.customlights.client.light.ModelMarkers;
import com.cotii.customlights.client.light.ObjectLights;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Item models with light markers (Blockbench groups, ModelEngine bones) report them and hide marker-only models. */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;renderModelLists(Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/item/ItemStack;IILcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;)V"))
    private void customlights$markers(ItemRenderer renderer, BakedModel model, ItemStack stack, int light, int overlay, PoseStack poseStack,
                                      VertexConsumer consumer, Operation<Void> original, @Local(argsOnly = true) ItemDisplayContext context) {
        // Item lights shine from the item model in the display contexts they are turned on for (first person by
        // moving with it like a part of the model
        if (ObjectLights.hasItemRules() && MarkerLights.phase() != MarkerLights.Phase.NONE) {
            ObjectLights.Rule rule = ObjectLights.itemRule(stack);
            ObjectLights.Display display = rule == null ? null : rule.display(context);
            if (com.cotii.customlights.client.gui.ObjectPreview.drawingItem) {
                com.cotii.customlights.client.gui.ObjectPreview.recordItemPose(poseStack.last().pose());
            }
            if (display != null && display.on) {
                PoseStack.Pose pose = poseStack.last();
                MarkerLights.submit(ObjectLights.markerKey(rule, context), pose.pose(), pose.normal(), 0.5f + (float) display.x / 16.0f,
                        0.5f + (float) display.y / 16.0f, 0.5f + (float) display.z / 16.0f, System.identityHashCode(model) * 31 + context.ordinal());
            }
        }
        ModelMarkers.Entry entry = ModelMarkers.get(model);
        if (entry == null) {
            original.call(renderer, model, stack, light, overlay, poseStack, consumer);
            return;
        }
        PoseStack.Pose pose = poseStack.last();
        for (ModelMarkers.Marker marker : entry.markers()) {
            MarkerLights.submit(marker.template(), pose.pose(), pose.normal(), marker.x(), marker.y(), marker.z(),
                    System.identityHashCode(model) * 31 + marker.key());
        }
        if (!entry.hideModel()) {
            original.call(renderer, model, stack, light, overlay, poseStack, consumer);
        }
    }
}
