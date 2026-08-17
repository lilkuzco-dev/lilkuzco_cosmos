package dev.lilkuzco.cosmos.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * One crater size band. Craters are placed by several placed_features at different rarities, so
 * the surface ends up with many small pits and a few large basins rather than one uniform size.
 *
 * @param minRadius   smallest crater in this band, blocks
 * @param maxRadius   largest crater in this band
 * @param depthRatio  bowl depth as a fraction of radius. Real craters are SHALLOW - about 1:5 -
 *                    and a 1:1 hole reads as a mineshaft, not an impact
 * @param rimRatio    rim height as a fraction of depth
 * @param exposeFloor whether the floor is excavated to mare basalt, as a real impact would be
 */
public record CraterConfiguration(int minRadius, int maxRadius, double depthRatio,
		double rimRatio, boolean exposeFloor,
		net.minecraft.world.level.block.state.BlockState surface,
		net.minecraft.world.level.block.state.BlockState floor) implements FeatureConfiguration {

	/**
	 * WHICH BLOCKS A CRATER IS MADE OF, and it has to be configurable.
	 *
	 * <p>The feature used to name {@code CosmosBlocks.REGOLITH} and {@code MARE_BASALT} directly.
	 * That was invisible while the Moon was the only body with craters — and the moment a second
	 * world reused the feature, it started painting lunar regolith across an outer moon whose
	 * surface is orange tholin. A worldgen feature that hardcodes a block belongs to exactly one
	 * world, whatever its name suggests.
	 *
	 * <p>Defaults are the lunar pair, so the Moon's own configured features did not have to change.
	 */
	public static final Codec<CraterConfiguration> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("min_radius").forGetter(CraterConfiguration::minRadius),
			Codec.INT.fieldOf("max_radius").forGetter(CraterConfiguration::maxRadius),
			Codec.DOUBLE.optionalFieldOf("depth_ratio", 0.22).forGetter(CraterConfiguration::depthRatio),
			Codec.DOUBLE.optionalFieldOf("rim_ratio", 0.35).forGetter(CraterConfiguration::rimRatio),
			Codec.BOOL.optionalFieldOf("expose_floor", true).forGetter(CraterConfiguration::exposeFloor),
			net.minecraft.world.level.block.state.BlockState.CODEC
					.optionalFieldOf("surface",
							dev.lilkuzco.cosmos.CosmosBlocks.REGOLITH.defaultBlockState())
					.forGetter(CraterConfiguration::surface),
			net.minecraft.world.level.block.state.BlockState.CODEC
					.optionalFieldOf("floor",
							dev.lilkuzco.cosmos.CosmosBlocks.MARE_BASALT.defaultBlockState())
					.forGetter(CraterConfiguration::floor))
			.apply(i, CraterConfiguration::new));
}
