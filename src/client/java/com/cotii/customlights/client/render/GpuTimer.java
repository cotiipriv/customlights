package com.cotii.customlights.client.render;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/** GPU time of the light passes while profiling is on ({@code /customlight stats} turns it on for a few seconds). */
public final class GpuTimer {
    public enum Pass {
        COPY, GBUFFER, SURFACES, ATMOSPHERE, COMPOSITE
    }

    private record Mark(int pass, int query) {
    }

    private static final Pass[] PASSES = Pass.values();
    private static final Deque<List<Mark>> PENDING = new ArrayDeque<>();
    private static final double[] TOTAL_NANOS = new double[PASSES.length];
    private static List<Mark> current;
    private static int samples;

    public static long profileUntil;

    private GpuTimer() {
    }

    static void beginFrame() {
        collect();
        current = System.currentTimeMillis() < profileUntil ? new ArrayList<>(PASSES.length + 1) : null;
    }

    static void begin(Pass pass) {
        mark(pass.ordinal());
    }

    private static void mark(int pass) {
        if (current == null) {
            return;
        }
        int query = GL15.glGenQueries();
        GL33.glQueryCounter(query, GL33.GL_TIMESTAMP);
        current.add(new Mark(pass, query));
    }

    static void endFrame() {
        if (current == null) {
            return;
        }
        mark(-1);
        PENDING.add(current);
        current = null;
        // Never let results pile up if the driver is slow to answer
        while (PENDING.size() > 30) {
            discard(PENDING.poll());
        }
    }

    private static void collect() {
        while (!PENDING.isEmpty()) {
            List<Mark> marks = PENDING.peek();
            if (GL15.glGetQueryObjecti(marks.get(marks.size() - 1).query(), GL15.GL_QUERY_RESULT_AVAILABLE) == 0) {
                return;
            }
            PENDING.poll();
            long previous = 0L;
            for (int i = 0; i < marks.size(); i++) {
                long time = GL33.glGetQueryObjecti64(marks.get(i).query(), GL15.GL_QUERY_RESULT);
                if (i > 0) {
                    TOTAL_NANOS[marks.get(i - 1).pass()] += time - previous;
                }
                previous = time;
            }
            discard(marks);
            samples++;
        }
    }

    private static void discard(List<Mark> marks) {
        for (Mark mark : marks) {
            GL15.glDeleteQueries(mark.query());
        }
    }

    /** Average milliseconds per pass since the last call, or null when nothing was measured. */
    public static String report() {
        if (samples == 0) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        double sum = 0.0d;
        for (int i = 0; i < PASSES.length; i++) {
            double ms = TOTAL_NANOS[i] / samples / 1.0e6d;
            sum += ms;
            builder.append(PASSES[i].name().toLowerCase(Locale.ROOT)).append(' ').append(String.format(Locale.ROOT, "%.2f", ms)).append("ms  ");
            TOTAL_NANOS[i] = 0.0d;
        }
        builder.append("total ").append(String.format(Locale.ROOT, "%.2f", sum)).append("ms over ").append(samples).append(" frames");
        samples = 0;
        return builder.toString();
    }
}
