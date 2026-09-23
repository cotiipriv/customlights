package com.cotii.customlights.client.compat;

import com.cotii.customlights.client.mixin.LevelRendererAccessor;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

/** The chunk sections the game is drawing this frame, so lights hidden behind terrain can be skipped. */
public final class VisibleSections {
    private VisibleSections() {
    }

    /** Fills {@code out} with the sections being drawn; false when the game does not tell (nothing is culled then). */
    public static boolean fill(LongSet out) {
        var levelRenderer = Minecraft.getInstance().levelRenderer;
        for (var section : ((LevelRendererAccessor) levelRenderer).customlights$visibleSections()) {
            BlockPos origin = section.getOrigin();
            out.add(SectionPos.asLong(origin.getX() >> 4, origin.getY() >> 4, origin.getZ() >> 4));
        }
        return !out.isEmpty();
    }
}
