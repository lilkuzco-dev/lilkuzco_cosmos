package dev.lilkuzco.cosmos.worldgen;

import dev.lilkuzco.cosmos.Cosmos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * Cosmos' only worldgen, and it is <b>dimension-local by construction</b>.
 *
 * <p>The crater feature is referenced solely by cosmos' own lunar biomes, which exist solely in
 * cosmos' own dimension. There is no biome modification, no overworld tag, and no placement that
 * could reach an existing world. The campaign fences overworld worldgen absolutely, and the way
 * to honour that is to have nothing that could touch it - not a flag that could be switched.
 */
public final class CosmosWorldgen {

	public static final Feature<CraterConfiguration> CRATER = new CraterFeature();

	public static void register() {
		Registry.register(BuiltInRegistries.FEATURE, Cosmos.id("crater"), CRATER);
	}

	private CosmosWorldgen() {
	}
}
