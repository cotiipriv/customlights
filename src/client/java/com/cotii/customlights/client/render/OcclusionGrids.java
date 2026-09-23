package com.cotii.customlights.client.render;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.util.Iterator;

/**
 * Which blocks around a light are solid, as a small 3D texture for lights that do not pass through blocks. One texel per
 * block holding which of its eight halves stop light (255 = the whole block), so slabs, stairs and the like cast the
 * shadow of their real shape.
 */
public final class OcclusionGrids {
    /** Largest half size in blocks (grids are at most 2 * 16 + 4 blocks wide). */
    private static final int MAX_HALF = 16;
    private static final int SNAP = 4;
    private static final int BUILDS_PER_FRAME = 2;
    private static final int UNUSED_FRAMES = 600;

    static final class Grid {
        int texture;
        int minX, minY, minZ, size;
        long lastUsed;
        boolean dirty = true;
        boolean built;

        boolean contains(int x, int y, int z) {
            return x >= minX && y >= minY && z >= minZ && x < minX + size && y < minY + size && z < minZ + size;
        }
    }

    private static final Long2ObjectOpenHashMap<Grid> GRIDS = new Long2ObjectOpenHashMap<>();
    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
    private static ByteBuffer buffer;
    private static long frame;
    private static int builds;
    private static ClientLevel level;
    private static int emptyTexture;

    private OcclusionGrids() {
    }

    static void beginFrame() {
        frame++;
        builds = 0;
        ClientLevel current = Minecraft.getInstance().level;
        if (current != level) {
            clear();
            level = current;
        }
        if (frame % 60 == 0) {
            Iterator<Grid> iterator = GRIDS.values().iterator();
            while (iterator.hasNext()) {
                Grid grid = iterator.next();
                if (frame - grid.lastUsed > UNUSED_FRAMES) {
                    Gl.deleteTexture(grid.texture);
                    iterator.remove();
                }
            }
        }
    }

    /** A 1x1x1 empty grid, bound when a light has none. */
    static int emptyTexture() {
        if (emptyTexture == 0) {
            emptyTexture = Gl.genTexture();
            upload(emptyTexture, 1, BufferUtils.createByteBuffer(1));
        }
        return emptyTexture;
    }

    /** The grid around a light (world position, reach in blocks), or null while it is not built yet. */
    static Grid gridFor(double x, double y, double z, float reach) {
        if (level == null) {
            return null;
        }
        int half = Math.min(Mth.ceil(reach) + 1, MAX_HALF);
        int minX = Math.floorDiv(Mth.floor(x) - half, SNAP) * SNAP;
        int minY = Math.floorDiv(Mth.floor(y) - half, SNAP) * SNAP;
        int minZ = Math.floorDiv(Mth.floor(z) - half, SNAP) * SNAP;
        int size = 2 * half + SNAP;
        long key = BlockPos.asLong(minX, minY, minZ) * 31L + size;
        Grid grid = GRIDS.get(key);
        if (grid == null) {
            grid = new Grid();
            grid.minX = minX;
            grid.minY = minY;
            grid.minZ = minZ;
            grid.size = size;
            GRIDS.put(key, grid);
        }
        grid.lastUsed = frame;
        if (grid.dirty && builds < BUILDS_PER_FRAME) {
            builds++;
            build(grid);
        }
        return grid.built ? grid : null;
    }

    private static void build(Grid grid) {
        int size = grid.size;
        int count = size * size * size;
        if (buffer == null || buffer.capacity() < count) {
            buffer = BufferUtils.createByteBuffer(count);
        }
        buffer.clear();
        for (int z = 0; z < size; z++) {
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    POS.set(grid.minX + x, grid.minY + y, grid.minZ + z);
                    buffer.put(occupancy(level.getBlockState(POS), POS));
                }
            }
        }
        buffer.flip();
        if (grid.texture == 0) {
            grid.texture = Gl.genTexture();
        }
        upload(grid.texture, size, buffer);
        grid.dirty = false;
        grid.built = true;
    }

    /** The eight halves of a block that stop light, one per bit (x + 2y + 4z). */
    private static byte occupancy(net.minecraft.world.level.block.state.BlockState state, BlockPos pos) {
        if (com.cotii.customlights.client.compat.Compat.isSolidRender(state, level, pos)) {
            return (byte) 255;
        }
        net.minecraft.world.phys.shapes.VoxelShape shape;
        try {
            shape = com.cotii.customlights.client.compat.Compat.lightShape(state, level, pos);
        } catch (RuntimeException ignored) {
            // Some modded blocks want a real world for their shape
            return 0;
        }
        if (shape == null || shape.isEmpty()) {
            return 0;
        }
        int mask = 0;
        for (net.minecraft.world.phys.AABB box : shape.toAabbs()) {
            for (int half = 0; half < 8; half++) {
                double x = ((half & 1) + 0.5d) * 0.5d;
                double y = (((half >> 1) & 1) + 0.5d) * 0.5d;
                double z = (((half >> 2) & 1) + 0.5d) * 0.5d;
                if (x > box.minX && x < box.maxX && y > box.minY && y < box.maxY && z > box.minZ && z < box.maxZ) {
                    mask |= 1 << half;
                }
            }
        }
        return (byte) mask;
    }

    private static void upload(int texture, int size, ByteBuffer data) {
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
        // The grid is tightly packed: whatever row length, skips or unpack buffer the game left set (26.x does) would
        // make the driver read past it, so they are cleared for the upload and put back afterwards
        int[] names = {GL11.GL_UNPACK_ALIGNMENT, GL11.GL_UNPACK_ROW_LENGTH, GL11.GL_UNPACK_SKIP_ROWS, GL11.GL_UNPACK_SKIP_PIXELS,
                GL12.GL_UNPACK_IMAGE_HEIGHT, GL12.GL_UNPACK_SKIP_IMAGES};
        int[] saved = new int[names.length];
        for (int i = 0; i < names.length; i++) {
            saved[i] = GL11.glGetInteger(names[i]);
            GL11.glPixelStorei(names[i], i == 0 ? 1 : 0);
        }
        int unpackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
        GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL30.GL_R8, size, size, size, 0, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, data);
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
        for (int i = 0; i < names.length; i++) {
            GL11.glPixelStorei(names[i], saved[i]);
        }
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, 0);
    }

    /** A block changed: grids holding it are rebuilt. */
    public static void blockChanged(int x, int y, int z) {
        for (Grid grid : GRIDS.values()) {
            if (grid.contains(x, y, z)) {
                grid.dirty = true;
            }
        }
    }

    /** A chunk was loaded: grids over it are rebuilt. */
    public static void chunkLoaded(int chunkX, int chunkZ) {
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        for (Grid grid : GRIDS.values()) {
            if (grid.minX < minX + 16 && grid.minX + grid.size > minX && grid.minZ < minZ + 16 && grid.minZ + grid.size > minZ) {
                grid.dirty = true;
            }
        }
    }

    static void clear() {
        for (Grid grid : GRIDS.values()) {
            if (grid.texture != 0) {
                Gl.deleteTexture(grid.texture);
            }
        }
        GRIDS.clear();
    }
}
