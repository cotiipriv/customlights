package com.cotii.customlights.client.mixin.geckolib;

import com.cotii.customlights.client.light.MarkerLights;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtil;

/**
 * GeckoLib bones named {@code customlights_<model_id>} are not drawn (nor their children); the light of that template is
 * placed at the animated bone instead.
 */
@Mixin(targets = {
        "software.bernie.geckolib.renderer.GeoEntityRenderer",
        "software.bernie.geckolib.renderer.GeoItemRenderer",
        "software.bernie.geckolib.renderer.GeoBlockRenderer",
        "software.bernie.geckolib.renderer.GeoArmorRenderer",
        "software.bernie.geckolib.renderer.GeoObjectRenderer",
        "software.bernie.geckolib.renderer.GeoReplacedEntityRenderer"
}, remap = false)
public abstract class GeoRendererMixin {
    @Inject(method = "renderRecursively", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void customlights$lightBone(CallbackInfo ci, @Local(argsOnly = true) PoseStack poseStack, @Local(argsOnly = true) GeoBone bone) {
        String template = MarkerLights.templateOf(bone.getName());
        if (template == null) {
            return;
        }
        ci.cancel();
        if (bone.isHidden()) {
            return;
        }
        poseStack.pushPose();
        RenderUtil.prepMatrixForBone(poseStack, bone);
        PoseStack.Pose pose = poseStack.last();
        MarkerLights.submit(template, pose.pose(), pose.normal(), bone.getPivotX() / 16.0f, bone.getPivotY() / 16.0f, bone.getPivotZ() / 16.0f,
                System.identityHashCode(bone));
        poseStack.popPose();
    }
}
