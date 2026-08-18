package dev.lilkuzco.cosmos.client;

import dev.lilkuzco.cosmos.CosmosEntities;
import dev.lilkuzco.cosmos.CosmosMenus;
import dev.lilkuzco.cosmos.satellite.CosmosNet;
import dev.lilkuzco.cosmos.satellite.ReconImager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;

/**
 * Client wiring. Three jobs: register the two screens, register the two renderers, and receive
 * what the server sends the console - constellation snapshots and imaging passes.
 */
public final class CosmosClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		MenuScreens.register(CosmosMenus.LAUNCH_PAD, LaunchPadScreen::new);

		// No bespoke entity MODELS in Phase A. Minecraft 26.2 reworked entity rendering onto a
		// submit-node pipeline that wants a real model layer, and a rocket model is Phase B work
		// rather than something to guess at here. What a player sees today is the flame and smoke
		// plume the server emits, the countdown, and the camera shake - which is most of what a
		// launch actually is.
		//
		// A renderer must still be REGISTERED for every entity type, model or not. Skipping the
		// registration does not yield an invisible rocket; EntityRenderDispatcher.getRenderer
		// returns null and LevelExtractor.isEntityVisible dereferences it on the render thread,
		// crashing the client the instant the vehicle enters view. That is exactly what happened
		// on the first live ignition. EntityRendererCoverageTest now guards this.
		ModelLayerRegistry.registerModelLayer(RocketModel.LAYER, RocketModel::createLayer);
		EntityRendererRegistry.register(CosmosEntities.ROCKET, RocketRenderer::new);
		ModelLayerRegistry.registerModelLayer(CapsuleModel.LAYER, CapsuleModel::createLayer);
		EntityRendererRegistry.register(CosmosEntities.CAPSULE, CapsuleRenderer::new);
		EntityRendererRegistry.register(CosmosEntities.TRANSIT, TransitRenderer::new);

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

		// Imaging passes. The report is drawn INTO the open planetarium rather than printed to
		// chat, which is the whole reason this packet exists: the operator clicked Image while
		// looking at the console, and chat is behind the console.
		//
		// The chat path survives as a fallback for the one case it is still right for - the
		// screen was closed during the round trip - because a report that arrives with nowhere
		// to be drawn should be delivered somewhere rather than dropped on the floor.
		ClientPlayNetworking.registerGlobalReceiver(CosmosNet.ReconS2C.TYPE,
				(payload, context) -> context.client().execute(() -> {
					PlanetariumScreen open = PlanetariumScreen.open();
					if (open != null) {
						open.showRecon(payload.satelliteName(), payload.report());
						return;
					}
					if (context.client().player == null) return;
					for (Component line : ReconImager.render(payload.report(),
							payload.satelliteName())) {
						context.client().player.sendSystemMessage(line);
					}
				}));
	}
}
