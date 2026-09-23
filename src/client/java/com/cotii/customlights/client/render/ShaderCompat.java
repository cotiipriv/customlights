package com.cotii.customlights.client.render;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Iris (shader packs), looked up without depending on it. */
public final class ShaderCompat {
    private static final Object IRIS;
    private static final MethodHandle PACK_IN_USE;
    private static final MethodHandle SHADOW_PASS;

    static {
        Object api = null;
        MethodHandle packInUse = null;
        MethodHandle shadowPass = null;
        if (FabricLoader.getInstance().isModLoaded("iris")) {
            try {
                Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                api = lookup.findStatic(type, "getInstance", MethodType.methodType(type)).invoke();
                packInUse = lookup.findVirtual(type, "isShaderPackInUse", MethodType.methodType(boolean.class))
                        .asType(MethodType.methodType(boolean.class, Object.class));
                shadowPass = lookup.findVirtual(type, "isRenderingShadowPass", MethodType.methodType(boolean.class))
                        .asType(MethodType.methodType(boolean.class, Object.class));
            } catch (Throwable failed) {
                com.cotii.customlights.Customlights.LOGGER.warn("CustomLights could not reach the Iris API", failed);
                api = null;
            }
        }
        IRIS = api;
        PACK_IN_USE = packInUse;
        SHADOW_PASS = shadowPass;
    }

    private ShaderCompat() {
    }

    /** A shader pack draws the world (it lights the scene its own way). */
    public static boolean shaderPackInUse() {
        if (IRIS == null) {
            return false;
        }
        try {
            return (boolean) PACK_IN_USE.invoke(IRIS);
        } catch (Throwable failed) {
            return false;
        }
    }

    /** The shader pack is drawing its shadow map (not the view). */
    public static boolean shadowPass() {
        if (IRIS == null) {
            return false;
        }
        try {
            return (boolean) SHADOW_PASS.invoke(IRIS);
        } catch (Throwable failed) {
            return false;
        }
    }
}
