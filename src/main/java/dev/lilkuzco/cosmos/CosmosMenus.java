package dev.lilkuzco.cosmos;

import dev.lilkuzco.cosmos.pad.LaunchPadMenu;
import dev.lilkuzco.cosmos.satellite.CosmosNet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

/**
 * One menu: the launch pad.
 *
 * <p>The satellite console deliberately has none. A planetarium is a display, not a container,
 * and forcing it through the inventory system would buy a slot grid nobody wants and cost the
 * freedom to draw an orbit. It talks over {@link CosmosNet} instead.
 */
public final class CosmosMenus {

	public static final MenuType<LaunchPadMenu> LAUNCH_PAD = Registry.register(
			BuiltInRegistries.MENU, Cosmos.id("launch_pad").toString(),
			new MenuType<>(LaunchPadMenu::new, FeatureFlags.VANILLA_SET));

	public static void register() {
		CosmosNet.registerCommon();
	}

	private CosmosMenus() {
	}
}
