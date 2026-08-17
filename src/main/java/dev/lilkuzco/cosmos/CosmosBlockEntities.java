package dev.lilkuzco.cosmos;

import dev.lilkuzco.cosmos.pad.FuelTankBlock;
import dev.lilkuzco.cosmos.pad.LaunchPadBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import dev.lilkuzco.cosmos.satellite.SatelliteConsoleBlockEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Set;

public final class CosmosBlockEntities {

	public static final BlockEntityType<LaunchPadBlockEntity> LAUNCH_PAD = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE, Cosmos.id("launch_pad"),
			new BlockEntityType<>(LaunchPadBlockEntity::new, Set.of(CosmosBlocks.LAUNCH_PAD)));

	public static final BlockEntityType<dev.lilkuzco.cosmos.isru.IsruBlockEntity> ISRU =
			Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Cosmos.id("isru"),
					new BlockEntityType<>(dev.lilkuzco.cosmos.isru.IsruBlockEntity::new,
							Set.of(CosmosBlocks.ELECTROLYSER, CosmosBlocks.REGOLITH_KILN)));

	public static final BlockEntityType<SatelliteConsoleBlockEntity> SATELLITE_CONSOLE =
			Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Cosmos.id("satellite_console"),
					new BlockEntityType<>(SatelliteConsoleBlockEntity::new,
							Set.of(CosmosBlocks.SATELLITE_CONSOLE)));

	/**
	 * Expose the launch pad's propellant reservoir to anything that moves fluid.
	 *
	 * <p>This one line is what lets crude_empire's oil pipes fuel a rocket without either mod
	 * naming the other: both speak Fabric's transfer API, so the coupling is to Fabric API, which
	 * cosmos already depends on. The tank block is registered too, so a 44-tank lunar pad presents
	 * its whole ring as connection surface rather than one square in the middle.
	 */
	public static void register() {
		FluidStorage.SIDED.registerForBlockEntity(
				(pad, direction) -> pad.tank(), LAUNCH_PAD);
		// The propellant tap. A launch pad or a pipe pulls hydrolox straight out of the base's
		// production, which is what makes a lunar refuelling stop a thing you can build.
		FluidStorage.SIDED.registerForBlockEntity(
				(plant, direction) -> plant.tap(), ISRU);
		FluidStorage.SIDED.registerForBlocks((level, pos, state, entity, direction) -> {
			LaunchPadBlockEntity pad = FuelTankBlock.controllerFor(level, pos);
			return pad == null ? null : pad.tank();
		}, CosmosBlocks.FUEL_TANK);
	}

	private CosmosBlockEntities() {
	}
}
