package dev.lilkuzco.cosmos.client;

import dev.lilkuzco.cosmos.CosmosMenus;
import dev.lilkuzco.cosmos.satellite.CosmosNet;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;

/**
 * Client wiring. Three jobs: register the two screens, register the two renderers, and receive
 * planetarium snapshots.
 */
public final class CosmosClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		MenuScreens.register(CosmosMenus.LAUNCH_PAD, LaunchPadScreen::new);

		// No bespoke entity renderers in Phase A. Minecraft 26.2 reworked entity rendering onto
		// a submit-node pipeline that wants a real model layer, and a rocket model is Phase B
		// work rather than something to guess at here. What a player sees today is the flame and
		// smoke plume the server emits, the countdown, and the camera shake - which is most of
		// what a launch actually is. Stated as a gap rather than faked.

		ClientPlayNetworking.registerGlobalReceiver(CosmosNet.PlanetariumS2C.TYPE,
				(payload, context) -> context.client().execute(() -> {
					Minecraft client = context.client();
					// Refresh a planetarium that is already open rather than reopening it, so a
					// pass ticking down on screen does not restart every time the server speaks.
					PlanetariumScreen open = PlanetariumScreen.open();
					if (open != null) {
						open.update(payload.satellites(), payload.planetRadius());
					} else if (payload.openScreen()) {
						client.gui.setScreen(new PlanetariumScreen(payload.console(),
								payload.satellites(), payload.planetRadius()));
					}
				}));
	}
}
