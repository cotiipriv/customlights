package com.cotii.customlights.client.compat;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.joml.Matrix4f;

/**
 * 1.20.1 and 1.20.4: items still keep their custom model data in NBT, vertices are built one call at a time and beacon beams
 * give their color as floats.
 */
public final class Compat {
    private static final String CUSTOM_MODEL_DATA = "CustomModelData";

    private Compat() {
    }

    public static int customModelData(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(CUSTOM_MODEL_DATA) ? tag.getInt(CUSTOM_MODEL_DATA) : -1;
    }

    public static void setCustomModelData(ItemStack stack, int value) {
        stack.getOrCreateTag().putInt(CUSTOM_MODEL_DATA, value);
    }

    public static float partialTick(Minecraft minecraft) {
        return minecraft.getFrameTime();
    }

    public static void vertex(VertexConsumer buffer, Matrix4f pose, float x, float y, float z, int color) {
        buffer.vertex(pose, x, y, z)
                .color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >>> 24) & 0xFF)
                .endVertex();
    }

    public static ChunkAccess fullChunk(ClientLevel level, int chunkX, int chunkZ) {
        return level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
    }

    /** How far into the current tick a world render pass is. */
    public static float partialTick(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context) {
        return context.tickDelta();
    }

    /** 1.20.1 does not tell Realms apart here; their lights go with the other servers. */
    public static boolean isRealm(net.minecraft.client.multiplayer.ServerData data) {
        return false;
    }

    public static int beaconColor(BeaconBlockEntity.BeaconBeamSection section) {
        float[] color = section.getColor();
        int red = Math.round(Math.min(1.0f, color[0]) * 255.0f);
        int green = Math.round(Math.min(1.0f, color[1]) * 255.0f);
        int blue = Math.round(Math.min(1.0f, color[2]) * 255.0f);
        return red << 16 | green << 8 | blue;
    }

    // ── Registries
    // ─────────────────────────────────────────────────

    public static Item item(ResourceLocation id) {
        return BuiltInRegistries.ITEM.get(id);
    }

    public static Block block(ResourceLocation id) {
        return BuiltInRegistries.BLOCK.get(id);
    }

    public static EntityType<?> entityType(ResourceLocation id) {
        return BuiltInRegistries.ENTITY_TYPE.get(id);
    }

    public static ParticleType<?> particleType(ResourceLocation id) {
        return BuiltInRegistries.PARTICLE_TYPE.get(id);
    }

    // ── Rendering
    // ──────────────────────────────────────────────────

    public static Entity createEntity(EntityType<?> type, ClientLevel level) {
        return type.create(level);
    }

    public static void renderEntity(Minecraft minecraft, Entity entity, double x, double y, double z, float yaw, float partialTick,
                                    PoseStack poseStack, MultiBufferSource buffers, int light) {
        minecraft.getEntityRenderDispatcher().render(entity, x, y, z, yaw, partialTick, poseStack, buffers, light);
    }

    public static void shaderSource(int shader, String source) {
        GlStateManager.glShaderSource(shader, java.util.List.of(source));
    }

    public static TextureTarget newTarget(int width, int height) {
        return new TextureTarget(width, height, true, Minecraft.ON_OSX);
    }

    public static void resizeTarget(RenderTarget target, int width, int height) {
        target.resize(width, height, Minecraft.ON_OSX);
    }

    public static boolean isSolidRender(BlockState state, BlockGetter level, BlockPos pos) {
        return state.isSolidRender(level, pos);
    }

    /** What of the block stops light (empty when it does not): a slab gives its half, stairs their steps. */
    public static net.minecraft.world.phys.shapes.VoxelShape lightShape(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getOcclusionShape(level, pos);
    }

    /** The buffer the editor draws its gizmo lines into. */
    public static com.mojang.blaze3d.vertex.VertexConsumer guiBuffer(net.minecraft.client.gui.GuiGraphics graphics) {
        return graphics.bufferSource().getBuffer(net.minecraft.client.renderer.RenderType.gui());
    }

    /** The game's light map can be read (it lives in memory on this version). */
    public static boolean lightmapReady() {
        return lightPixels() != null;
    }

    /** Brightness (0..1) the game gives a place with these block and sky light levels, read from its light map. */
    public static float lightmapBrightness(ClientLevel level, int block, int sky) {
        com.mojang.blaze3d.platform.NativeImage pixels = lightPixels();
        if (pixels == null) {
            return 1.0f;
        }
        int color = pixels.getPixelRGBA(block, sky);
        int peak = Math.max(color & 0xFF, Math.max((color >> 8) & 0xFF, (color >> 16) & 0xFF));
        return peak / 255.0f;
    }

    private static com.mojang.blaze3d.platform.NativeImage lightPixels() {
        return ((com.cotii.customlights.client.mixin.LightTextureAccessor) Minecraft.getInstance().gameRenderer.lightTexture()).customlights$lightPixels();
    }

    /** Tells the game its cached shader program is no longer the one bound. */
    public static void forgetBoundShader() {
        com.cotii.customlights.client.mixin.ShaderInstanceAccessor.customlights$setLastProgramId(-1);
        com.cotii.customlights.client.mixin.ShaderInstanceAccessor.customlights$setLastAppliedShader(null);
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
