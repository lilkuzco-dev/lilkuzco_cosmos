package dev.lilkuzco.cosmos;

import dev.lilkuzco.cosmos.pad.LaunchPadBlockEntity;
import dev.lilkuzco.cosmos.satellite.SatelliteConsoleBlockEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Set;

public final class CosmosBlockEntities {

	public static final BlockEntityType<LaunchPadBlockEntity> LAUNCH_PAD = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE, Cosmos.id("launch_pad"),
			new BlockEntityType<>(LaunchPadBlockEntity::new, Set.of(CosmosBlocks.LAUNCH_PAD)));

	public static final BlockEntityType<SatelliteConsoleBlockEntity> SATELLITE_CONSOLE =
			Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Cosmos.id("satellite_console"),
					new BlockEntityType<>(SatelliteConsoleBlockEntity::new,
							Set.of(CosmosBlocks.SATELLITE_CONSOLE)));

	public static void register() {
	}

	private CosmosBlockEntities() {
	}
}
