package com.cotii.customlights.client.light;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Color names and hex parsing shared by commands, storage and the editor. */
public final class LightColors {
    /** Cycles through every hue. */
    public static final int PARTY = 0x1000000;
    public static final int PARTY_TICKS = 60;

    private static final Map<String, Integer> NAMED = new LinkedHashMap<>();

    static {
        NAMED.put("white", 0xFFFFFF);
        NAMED.put("warm", 0xFFC98A);
        NAMED.put("cold", 0xBFD8FF);
        NAMED.put("red", 0xFF2A2A);
        NAMED.put("crimson", 0xC0122E);
        NAMED.put("orange", 0xFF8A1E);
        NAMED.put("lava", 0xFF5A00);
        NAMED.put("gold", 0xFFC21E);
        NAMED.put("yellow", 0xFFF03C);
        NAMED.put("lime", 0x8CFF32);
        NAMED.put("green", 0x2EE85A);
        NAMED.put("teal", 0x20D8B8);
        NAMED.put("aqua", 0x3CF0FF);
        NAMED.put("cyan", 0x00C8E8);
        NAMED.put("blue", 0x2A5CFF);
        NAMED.put("navy", 0x1A2A9A);
        NAMED.put("purple", 0x9A3CFF);
        NAMED.put("magenta", 0xFF32E0);
        NAMED.put("pink", 0xFF7AC0);
        NAMED.put("soul", 0x3CE0E8);
        NAMED.put("shadow", 0x202028);
        NAMED.put("black", 0x000000);
    }

    public static final List<String> NAMES;

    static {
        java.util.List<String> names = new java.util.ArrayList<>(NAMED.keySet());
        names.add("party");
        NAMES = List.copyOf(names);
    }

    private LightColors() {
    }

    /** RGB (or {@link #PARTY}) from a name, "RRGGBB" or "#RRGGBB", or null. */
    public static Integer parse(String text) {
        String value = text.trim().toLowerCase(Locale.ROOT);
        if (value.equals("party") || value.equals("rainbow")) {
            return PARTY;
        }
        Integer named = NAMED.get(value);
        if (named != null) {
            return named;
        }
        String hex = value.startsWith("#") ? value.substring(1) : value;
        if (hex.length() != 6) {
            return null;
        }
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** The name when the color has one, otherwise "#RRGGBB". */
    public static String format(int color) {
        if (color == PARTY) {
            return "party";
        }
        for (Map.Entry<String, Integer> entry : NAMED.entrySet()) {
            if (entry.getValue() == (color & 0xFFFFFF)) {
                return entry.getKey();
            }
        }
        return hex(color);
    }

    public static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    /** The real RGB of a color at a time in ticks ({@link #PARTY} changes with time). */
    public static int resolve(int color, double time) {
        if (color != PARTY) {
            return color & 0xFFFFFF;
        }
        double loop = ((time % PARTY_TICKS) + PARTY_TICKS) % PARTY_TICKS / PARTY_TICKS;
        return fromHsv((float) (loop * 360.0d), 1.0f, 1.0f);
    }

    public static int lerp(int from, int to, float t) {
        int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return r << 16 | g << 8 | b;
    }

    /** RGB from hue in degrees, saturation and value in 0..1. */
    public static int fromHsv(float hue, float saturation, float value) {
        float sectorPosition = (((hue % 360.0f) + 360.0f) % 360.0f) / 60.0f;
        int sector = (int) Math.floor(sectorPosition) % 6;
        float fraction = sectorPosition - (float) Math.floor(sectorPosition);
        float p = value * (1.0f - saturation);
        float q = value * (1.0f - fraction * saturation);
        float t = value * (1.0f - (1.0f - fraction) * saturation);
        float r, g, b;
        switch (sector) {
            case 0 -> { r = value; g = t; b = p; }
            case 1 -> { r = q; g = value; b = p; }
            case 2 -> { r = p; g = value; b = t; }
            case 3 -> { r = p; g = q; b = value; }
            case 4 -> { r = t; g = p; b = value; }
            default -> { r = value; g = p; b = q; }
        }
        return Math.round(r * 255.0f) << 16 | Math.round(g * 255.0f) << 8 | Math.round(b * 255.0f);
    }

    /** {hue in degrees, saturation, value}. */
    public static float[] toHsv(int rgb) {
        float r = (rgb >> 16 & 0xFF) / 255.0f;
        float g = (rgb >> 8 & 0xFF) / 255.0f;
        float b = (rgb & 0xFF) / 255.0f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float hue;
        if (delta == 0.0f) {
            hue = 0.0f;
        } else if (max == r) {
            hue = 60.0f * (((g - b) / delta) % 6.0f);
        } else if (max == g) {
            hue = 60.0f * ((b - r) / delta + 2.0f);
        } else {
            hue = 60.0f * ((r - g) / delta + 4.0f);
        }
        if (hue < 0.0f) {
            hue += 360.0f;
        }
        return new float[]{hue, max == 0.0f ? 0.0f : delta / max, max};
    }
}
