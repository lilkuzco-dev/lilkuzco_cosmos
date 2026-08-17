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
		double rimRatio, boolean exposeFloor) implements FeatureConfiguration {

	public static final Codec<CraterConfiguration> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("min_radius").forGetter(CraterConfiguration::minRadius),
			Codec.INT.fieldOf("max_radius").forGetter(CraterConfiguration::maxRadius),
			Codec.DOUBLE.optionalFieldOf("depth_ratio", 0.22).forGetter(CraterConfiguration::depthRatio),
			Codec.DOUBLE.optionalFieldOf("rim_ratio", 0.35).forGetter(CraterConfiguration::rimRatio),
			Codec.BOOL.optionalFieldOf("expose_floor", true).forGetter(CraterConfiguration::exposeFloor))
			.apply(i, CraterConfiguration::new));
}
