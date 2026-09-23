package com.cotii.customlights.client;

import com.cotii.customlights.client.ClientSetup;
import net.fabricmc.api.ClientModInitializer;

public class CustomlightsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientSetup.initialize();
	}
}
