package com.cotii.customlights.client.render;

import java.util.ArrayList;
import java.util.List;

/** The lights of the frame being drawn. */
public final class LightFrame {
    private final List<RenderLight> pool = new ArrayList<>();
    private int size;

    public RenderLight add() {
        if (size == pool.size()) {
            pool.add(new RenderLight());
        }
        RenderLight light = pool.get(size++);
        light.reset();
        return light;
    }

    public void clear() {
        size = 0;
    }

    public int size() {
        return size;
    }

    public RenderLight get(int index) {
        return pool.get(index);
    }

    /** Removes an instance by moving the last one into its slot (order is not kept). */
    void removeAt(int index) {
        int last = size - 1;
        if (index != last) {
            RenderLight removed = pool.get(index);
            pool.set(index, pool.get(last));
            pool.set(last, removed);
        }
        size--;
    }

    void sortByDistance() {
        // Insertion sort on the used part: few lights, and mostly sorted from last frame
        for (int i = 1; i < size; i++) {
            RenderLight current = pool.get(i);
            int j = i - 1;
            while (j >= 0 && pool.get(j).sortDistance > current.sortDistance) {
                pool.set(j + 1, pool.get(j));
                j--;
            }
            pool.set(j + 1, current);
        }
    }
}
