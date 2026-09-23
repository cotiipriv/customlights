package com.cotii.customlights.client.command;

import com.cotii.customlights.client.light.Light;
import com.cotii.customlights.client.light.LightAnimation;
import com.cotii.customlights.client.light.LightColors;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Writes lights back as the commands that create them (copy, editor). */
public final class CommandWriter {
    public static final String ROOT = "/customlight";

    private CommandWriter() {
    }

    /** The command that creates the light (set or follow). */
    public static String create(Light light) {
        StringBuilder builder = new StringBuilder(ROOT);
        if (light.isFollow()) {
            builder.append(" follow ").append(light.id).append(' ').append(light.target)
                    .append(' ').append(number(light.offsetX)).append(' ').append(number(light.offsetY)).append(' ').append(number(light.offsetZ));
        } else {
            builder.append(" set ").append(light.id)
                    .append(' ').append(number(light.x)).append(' ').append(number(light.y)).append(' ').append(number(light.z));
        }
        builder.append(' ').append(light.shape.id)
                .append(' ').append(LightColors.format(light.color))
                .append(' ').append(number(light.radius))
                .append(' ').append(number(light.distance))
                .append(' ').append(number(light.intensity))
                .append(' ').append(number(light.atmosphere))
                .append(' ').append(light.fadeIn)
                .append(' ').append(light.fadeOut);
        appendDirection(builder, light);
        if (light.isFollow() && !light.visibleFirstPerson) {
            builder.append(" false");
        }
        return builder.toString();
    }

    /**
     * {@code model_id <id> <x> <y> <z> <shape> <color> <radius> <opacity> <light> <fade_in> <fade_out> <distance> [yaw
     * pitch]}.
     */
    public static String model(Light template) {
        StringBuilder builder = new StringBuilder(ROOT).append(" model_id ").append(template.id)
                .append(' ').append(number(template.x)).append(' ').append(number(template.y)).append(' ').append(number(template.z))
                .append(' ').append(template.shape.id)
                .append(' ').append(LightColors.format(template.color))
                .append(' ').append(number(template.radius))
                .append(' ').append(number(template.atmosphere / Light.MAX_ATMOSPHERE))
                .append(' ').append(number(template.intensity))
                .append(' ').append(template.fadeIn)
                .append(' ').append(template.fadeOut)
                .append(' ').append(number(template.distance));
        appendDirection(builder, template);
        return builder.toString();
    }

    private static void appendDirection(StringBuilder builder, Light light) {
        if (!light.shape.directional()) {
            return;
        }
        if (light.yaw != 0.0f || light.pitch != light.shape.defaultPitch) {
            builder.append(' ').append(number(light.yaw)).append(' ').append(number(light.pitch));
        }
    }

    /** The animate commands that give the light its current animations. */
    public static List<String> animations(Light light) {
        List<String> lines = new ArrayList<>();
        LightAnimation animation = light.animation;
        String prefix = ROOT + " animate " + light.id + " ";
        boolean first = true;
        for (LightAnimation.Entry entry : animation.presets) {
            lines.add(prefix + (first ? "" : "add ") + entry.preset.id + " " + entry.time + " " + number(entry.amount));
            first = false;
        }
        if (animation.radiusPulseTime > 0) {
            lines.add(prefix + "animation_radius " + number(animation.radiusPulseTarget) + " " + animation.radiusPulseTime);
        }
        if (animation.colors.length >= 2) {
            StringBuilder builder = new StringBuilder(prefix).append("color_transition");
            for (int i = 0; i < animation.colors.length; i++) {
                builder.append(' ').append(LightColors.format(animation.colors[i])).append(' ').append(animation.colorTime(i));
            }
            lines.add(builder.toString());
        }
        if (animation.sizeTime > 0) {
            lines.add(prefix + "size " + number(animation.sizeRadius) + " " + number(animation.sizeDistance) + " " + animation.sizeTime);
        }
        return lines;
    }

    /** Every command of the light, one per line. */
    public static String full(Light light, boolean template) {
        List<String> lines = new ArrayList<>();
        lines.add(template ? model(light) : create(light));
        if (light.isStretched()) {
            lines.add(stretch(light));
        }
        if (light.hasPivot()) {
            lines.add(pivot(light));
        }
        if (light.shape.hasAngle() && light.angle > 0.0f && light.angle != light.shape.look.angle()) {
            lines.add(ROOT + " angle " + light.id + " " + number(light.angle));
        }
        lines.addAll(animations(light));
        return String.join("\n", lines);
    }

    public static String pivot(Light light) {
        return ROOT + " pivot " + light.id + " " + number(light.pivotX) + " " + number(light.pivotY) + " " + number(light.pivotZ);
    }

    public static String stretch(Light light) {
        return ROOT + " stretch " + light.id + " " + number(light.stretchX) + " " + number(light.stretchY) + " " + number(light.stretchZ);
    }

    public static String number(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "0";
        }
        String text = BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        return text.equals("-0") ? "0" : text;
    }
}
