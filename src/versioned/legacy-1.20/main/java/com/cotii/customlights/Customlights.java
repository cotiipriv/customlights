package com.cotii.customlights;

import net.minecraft.resources.ResourceLocation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared constants. */
public final class Customlights {
	public static final String MOD_ID = "customlights";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private Customlights() {
	}

	public static ResourceLocation id(String path) {
		return new ResourceLocation(MOD_ID, path);
	}
}
