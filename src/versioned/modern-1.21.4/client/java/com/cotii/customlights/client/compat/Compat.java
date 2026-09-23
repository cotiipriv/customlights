package com.cotii.customlights.client.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.joml.Matrix4f;

import java.util.List;

/**
 * 1.21.4: registries answer by value, custom model data holds lists, entities are created with a spawn reason and the game
 * applies its shader program every frame (nothing to invalidate).
 */
public final class Compat {
    private Compat() {
    }

    public static int customModelData(ItemStack stack) {
        CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        Float first = data == null ? null : data.getFloat(0);
        return first == null ? -1 : Math.round(first);
    }

    public static void setCustomModelData(ItemStack stack, int value) {
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of((float) value), List.of(), List.of(), List.of()));
    }

    public static float partialTick(Minecraft minecraft) {
        return minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
    }

    public static void vertex(VertexConsumer buffer, Matrix4f pose, float x, float y, float z, int color) {
        buffer.addVertex(pose, x, y, z).setColor(color);
    }

    public static ChunkAccess fullChunk(ClientLevel level, int chunkX, int chunkZ) {
        return level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
    }

    public static boolean isRealm(net.minecraft.client.multiplayer.ServerData data) {
        return data.isRealm();
    }

    public static float partialTick(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context) {
        return context.tickCounter().getGameTimeDeltaPartialTick(true);
    }

    public static int beaconColor(BeaconBlockEntity.BeaconBeamSection section) {
        return section.getColor() & 0xFFFFFF;
    }

    // ── Registries
    // ─────────────────────────────────────────────────

    public static Item item(ResourceLocation id) {
        return BuiltInRegistries.ITEM.getValue(id);
    }

    public static Block block(ResourceLocation id) {
        return BuiltInRegistries.BLOCK.getValue(id);
    }

    public static EntityType<?> entityType(ResourceLocation id) {
        return BuiltInRegistries.ENTITY_TYPE.getValue(id);
    }

    public static ParticleType<?> particleType(ResourceLocation id) {
        return BuiltInRegistries.PARTICLE_TYPE.getValue(id);
    }

    // ── Rendering
    // ──────────────────────────────────────────────────

    public static Entity createEntity(EntityType<?> type, ClientLevel level) {
        return type.create(level, EntitySpawnReason.COMMAND);
    }

    public static void renderEntity(Minecraft minecraft, Entity entity, double x, double y, double z, float yaw, float partialTick,
                                    PoseStack poseStack, MultiBufferSource buffers, int light) {
        // The entity carries its own rotation here
        entity.setYRot(yaw);
        entity.yRotO = yaw;
        minecraft.getEntityRenderDispatcher().render(entity, x, y, z, partialTick, poseStack, buffers, light);
    }

    public static void shaderSource(int shader, String source) {
        GlStateManager.glShaderSource(shader, source);
    }

    public static TextureTarget newTarget(int width, int height) {
        return new TextureTarget(width, height, true);
    }

    public static void resizeTarget(RenderTarget target, int width, int height) {
        target.resize(width, height);
    }

    public static boolean isSolidRender(BlockState state, BlockGetter level, BlockPos pos) {
        return state.isSolidRender();
    }

    /** What of the block stops light (empty when it does not): a slab gives its half, stairs their steps. */
    public static net.minecraft.world.phys.shapes.VoxelShape lightShape(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getOcclusionShape();
    }

    /** The buffer the editor draws its gizmo lines into. */
    public static com.mojang.blaze3d.vertex.VertexConsumer guiBuffer(net.minecraft.client.gui.GuiGraphics graphics) {
        // The screen keeps its buffer to itself now, but it is the game's own one
        return Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(net.minecraft.client.renderer.RenderType.gui());
    }

    /** The light map lives on the graphics card here: its brightness is worked out the way the game does. */
    public static boolean lightmapReady() {
        return true;
    }

    /** Brightness (0..1) the game gives a place with these block and sky light levels (its light map formula). */
    public static float lightmapBrightness(ClientLevel level, int block, int sky) {
        float skyDarken = level.getSkyDarken(1.0f);
        float skyLight = net.minecraft.client.renderer.LightTexture.getBrightness(level.dimensionType(), sky) * (skyDarken * 0.95f + 0.05f);
        float blockLight = net.minecraft.client.renderer.LightTexture.getBrightness(level.dimensionType(), block) * 1.5f;
        float light = Math.min(1.0f, Math.max(skyLight, blockLight * 0.8f) * 0.96f + 0.03f);
        // The gamma setting brightens dark places the same way the game does
        float gamma = Minecraft.getInstance().options.gamma().get().floatValue();
        float lifted = 1.0f - (float) Math.pow(1.0f - light, 4.0f);
        return Math.max(0.0f, Math.min(1.0f, light + (lifted - light) * gamma));
    }

    /** Nothing to do: this version applies its shader program every time. */
    public static void forgetBoundShader() {
    }

    // ── Chat, equipment, beacons, teleports ────────────────────────

    public static net.minecraft.network.chat.ClickEvent runCommand(String command) {
        return new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, command);
    }

    public static net.minecraft.network.chat.ClickEvent copyText(String text) {
        return new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.COPY_TO_CLIPBOARD, text);
    }

    public static net.minecraft.network.chat.HoverEvent showText(net.minecraft.network.chat.Component text) {
        return new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, text);
    }

    public static Iterable<ItemStack> equipment(net.minecraft.world.entity.LivingEntity living) {
        return living.getAllSlots();
    }

    /** Color (0xRRGGBB) of a beacon's beam at its top, or -1 without a beam. */
    public static int beaconTopColor(BeaconBlockEntity beacon) {
        java.util.List<BeaconBlockEntity.BeaconBeamSection> sections = beacon.getBeamSections();
        return sections.isEmpty() ? -1 : beaconColor(sections.get(sections.size() - 1));
    }

    public static void teleport(net.minecraft.world.entity.Entity entity, double x, double y, double z, float yaw, float pitch) {
        entity.moveTo(x, y, z, yaw, pitch);
    }
}
