package com.cotii.customlights.client.gui;

import com.cotii.customlights.Customlights;
import com.cotii.customlights.client.light.ObjectLights;
import com.cotii.customlights.client.render.LightFrame;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The Objects editor's 3D preview: the chosen object floats and turns in front of the player, in the world, so its light is
 * drawn by the real light pipeline (and the player can walk around it).
 */
public final class ObjectPreview {
    private static final int PARTICLE_INTERVAL = 4;

    private static Vec3 stage;
    private static ObjectLights.Rule rule;
    private static String builtFor;
    private static ItemStack stack = ItemStack.EMPTY;
    private static BlockState blockState;
    private static Entity entity;
    private static SimpleParticleType particle;
    private static int age;
    private static float stageYaw;
    /** The display context items are previewed with (the one being edited). */
    public static ItemDisplayContext itemContext = ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;

    private ObjectPreview() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(ObjectPreview::render);
    }

    public static Vec3 stage() {
        return stage;
    }

    /** Shows {@code shown} at {@code position} (called every tick while the Objects page is open). */
    public static void show(ObjectLights.Rule shown, Vec3 position) {
        if (stage == null || !stage.equals(position)) {
            // Faces the player once, when placed, and then stays still
            stage = position;
            stageYaw = facingYaw(Minecraft.getInstance());
        }
        stage = position;
        rule = shown;
        String key = shown.key();
        if (!key.equals(builtFor)) {
            builtFor = key;
            build(shown);
        }
    }

    /** While the preview item is drawn: its model transform is kept for the gizmo. */
    public static boolean drawingItem;
    private static final Matrix4f ITEM_POSE = new Matrix4f();
    private static final Matrix4f ITEM_POSE_INVERSE = new Matrix4f();
    private static Vec3 itemPoseCamera;

    /** The preview item's model is being drawn with { pose} (camera-relative world space). */
    public static void recordItemPose(Matrix4f pose) {
        ITEM_POSE.set(pose);
        ITEM_POSE.invert(ITEM_POSE_INVERSE);
        itemPoseCamera = drawCamera;
    }

    public static boolean hasItemPose() {
        return itemPoseCamera != null && stage != null;
    }

    /** World position of a point of the preview item's model (in blocks, model space). */
    public static Vec3 itemPoint(double x, double y, double z) {
        Vector3f point = ITEM_POSE.transformPosition((float) x, (float) y, (float) z, new Vector3f());
        return itemPoseCamera.add(point.x, point.y, point.z);
    }

    /** Model space point of the preview item at a world position. */
    public static Vector3f itemLocal(Vec3 world) {
        Vec3 relative = world.subtract(itemPoseCamera);
        return ITEM_POSE_INVERSE.transformPosition((float) relative.x, (float) relative.y, (float) relative.z, new Vector3f());
    }

    private static Vec3 drawCamera = Vec3.ZERO;

    public static void hide() {
        itemPoseCamera = null;
        stage = null;
        rule = null;
        builtFor = null;
        entity = null;
    }

    private static void build(ObjectLights.Rule shown) {
        stack = ItemStack.EMPTY;
        blockState = null;
        entity = null;
        particle = null;
        ResourceLocation id = ResourceLocation.tryParse(shown.target);
        if (id == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        try {
            switch (shown.kind) {
                case ITEM -> {
                    if (BuiltInRegistries.ITEM.containsKey(id)) {
                        stack = new ItemStack(com.cotii.customlights.client.compat.Compat.item(id));
                        if (shown.customModelData >= 0) {
                            com.cotii.customlights.client.compat.Compat.setCustomModelData(stack, shown.customModelData);
                        }
                    }
                }
                case BLOCK -> {
                    if (BuiltInRegistries.BLOCK.containsKey(id)) {
                        blockState = com.cotii.customlights.client.compat.Compat.block(id).defaultBlockState();
                    }
                }
                case ENTITY -> {
                    if (BuiltInRegistries.ENTITY_TYPE.containsKey(id) && minecraft.level != null) {
                        EntityType<?> type = com.cotii.customlights.client.compat.Compat.entityType(id);
                        if (type != EntityType.PLAYER) {
                            entity = com.cotii.customlights.client.compat.Compat.createEntity(type, minecraft.level);
                        }
                    }
                }
                case PARTICLE -> {
                    if (BuiltInRegistries.PARTICLE_TYPE.containsKey(id)) {
                        ParticleType<?> type = com.cotii.customlights.client.compat.Compat.particleType(id);
                        if (type instanceof SimpleParticleType simple) {
                            particle = simple;
                        }
                    }
                }
                case SPECIAL -> {
                    blockState = switch (shown.target) {
                        case ObjectLights.BEACON_BEAM -> Blocks.BEACON.defaultBlockState();
                        case ObjectLights.END_GATEWAY_BEAM -> Blocks.END_GATEWAY.defaultBlockState();
                        case ObjectLights.CONDUIT -> Blocks.CONDUIT.defaultBlockState();
                        case ObjectLights.LIT_BLOCKS -> Blocks.FURNACE.defaultBlockState()
                                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, true);
                        default -> null;
                    };
                }
                case BONE -> {
                }
            }
        } catch (RuntimeException exception) {
            Customlights.LOGGER.warn("CustomLights could not preview {}", shown.target, exception);
            entity = null;
        }
    }

    /** Whether the preview can show the chosen object itself (not only its light). */
    public static boolean canShowObject() {
        if (rule == null) {
            return false;
        }
        return switch (rule.kind) {
            case ITEM -> !stack.isEmpty();
            case BLOCK -> blockState != null;
            case ENTITY -> entity != null;
            case PARTICLE -> particle != null;
            case SPECIAL -> blockState != null;
            case BONE -> false;
        };
    }

    public static void tick(Minecraft minecraft) {
        age++;
        ClientLevel level = minecraft.level;
        if (stage == null || level == null) {
            return;
        }
        if (particle != null && age % PARTICLE_INTERVAL == 0) {
            level.addParticle(particle, stage.x, stage.y, stage.z, 0.0d, 0.01d, 0.0d);
        }
    }

    /**
     * The preview object is not a real block, bone or held item, so its light is added here (entities and particles are real
     * and light themselves).
     */
    public static void collect(LightFrame frame, Vec3 camera, float partialTick) {
        if (stage == null || rule == null) {
            return;
        }
        // Items light themselves through their model (in the display contexts they are on for)
        if (rule.kind != ObjectLights.Kind.ENTITY && rule.kind != ObjectLights.Kind.PARTICLE && !(rule.kind == ObjectLights.Kind.ITEM && !stack.isEmpty())) {
            ObjectLights.emit(frame, rule, stage.x, stage.y, stage.z, 0.0f, camera, partialTick);
        }
    }

    private static float facingYaw(Minecraft minecraft) {
        if (minecraft.player == null || stage == null) {
            return 0.0f;
        }
        double dx = minecraft.player.getX() - stage.x;
        double dz = minecraft.player.getZ() - stage.z;
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private static void render(WorldRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (stage == null || rule == null || level == null || context.consumers() == null) {
            return;
        }
        Vec3 camera = context.camera().getPosition();
        float partialTick = com.cotii.customlights.client.compat.Compat.partialTick(context);
        PoseStack poseStack = context.matrixStack() != null ? context.matrixStack() : new PoseStack();
        MultiBufferSource buffers = context.consumers();
        int light = LevelRenderer.getLightColor(level, BlockPos.containing(stage));
        poseStack.pushPose();
        poseStack.translate(stage.x - camera.x, stage.y - camera.y, stage.z - camera.z);
        try {
            if (!stack.isEmpty()) {
                // Still and facing the player, drawn like the chosen display context
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - stageYaw));
                drawCamera = camera;
                drawingItem = true;
                try {
                    minecraft.getItemRenderer().renderStatic(stack, itemContext, light, OverlayTexture.NO_OVERLAY, poseStack, buffers, level, 0);
                } finally {
                    drawingItem = false;
                }
            } else if (blockState != null) {
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - stageYaw));
                poseStack.scale(0.6f, 0.6f, 0.6f);
                poseStack.translate(-0.5f, -0.5f, -0.5f);
                minecraft.getBlockRenderer().renderSingleBlock(blockState, poseStack, buffers, light, OverlayTexture.NO_OVERLAY);
            } else if (entity != null) {
                double bottom = stage.y - entity.getBbHeight() * 0.5d;
                entity.setPos(stage.x, bottom, stage.z);
                // Entities stand still, facing the player
                float facing = stageYaw;
                entity.setYRot(facing);
                entity.setYBodyRot(facing);
                entity.setYHeadRot(facing);
                entity.setOldPosAndRot();
                if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
                    living.yBodyRotO = facing;
                    living.yHeadRotO = facing;
                }
                poseStack.popPose();
                poseStack.pushPose();
                com.cotii.customlights.client.compat.Compat.renderEntity(minecraft, entity, stage.x - camera.x, bottom - camera.y, stage.z - camera.z,
                        facing, 0.0f, poseStack, buffers, light);
            }
        } catch (RuntimeException exception) {
            Customlights.LOGGER.warn("CustomLights preview failed for {}", rule.target, exception);
            stack = ItemStack.EMPTY;
            blockState = null;
            entity = null;
        } finally {
            poseStack.popPose();
        }
    }
}
