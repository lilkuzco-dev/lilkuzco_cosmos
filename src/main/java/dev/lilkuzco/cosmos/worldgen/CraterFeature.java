package dev.lilkuzco.cosmos.worldgen;

import dev.lilkuzco.cosmos.CosmosBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

/**
 * Carves an impact crater: a shallow bowl, an excavated basalt floor, and a raised rim.
 *
 * <p><b>The proportions are the whole thing.</b> A crater is not a hole — it is a bowl about a
 * fifth as deep as it is wide, with the excavated material piled around the edge. Dig a
 * hemisphere instead and it reads as a mineshaft; skip the rim and it reads as a puddle. Getting
 * the ratios right is what makes a field of these look like the Moon rather than like damage.
 *
 * <p>The floor is excavated to {@link CosmosBlocks#MARE_BASALT} because that is what an impact
 * does: it removes the loose regolith and exposes the rock beneath. That also makes craters the
 * natural place to mine, which is a gameplay consequence of a physical fact rather than a rule.
 */
public class CraterFeature extends Feature<CraterConfiguration> {

	public CraterFeature() {
		super(CraterConfiguration.CODEC);
	}

	@Override
	public boolean place(FeaturePlaceContext<CraterConfiguration> context) {
		WorldGenLevel level = context.level();
		RandomSource random = context.random();
		CraterConfiguration config = context.config();
		BlockPos origin = context.origin();

		int radius = config.minRadius()
				+ (config.maxRadius() > config.minRadius()
						? random.nextInt(config.maxRadius() - config.minRadius() + 1) : 0);
		if (radius < 2) return false;

		double depth = Math.max(1.0, radius * config.depthRatio());
		double rim = Math.max(1.0, depth * config.rimRatio());
		int surfaceY = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, origin.getX(), origin.getZ());

		// A slight ellipse and a random rotation, so a field of craters does not look stamped.
		double squash = 0.85 + random.nextDouble() * 0.3;
		double angle = random.nextDouble() * Math.PI;
		double cos = Math.cos(angle);
		double sin = Math.sin(angle);

		int reach = (int) Math.ceil(radius * 1.35);
		BlockState regolith = CosmosBlocks.REGOLITH.defaultBlockState();
		BlockState basalt = CosmosBlocks.MARE_BASALT.defaultBlockState();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				// Rotate into the ellipse's frame before measuring.
				double rx = dx * cos - dz * sin;
				double rz = (dx * sin + dz * cos) / squash;
				double distance = Math.sqrt(rx * rx + rz * rz);
				if (distance > reach) continue;

				int x = origin.getX() + dx;
				int z = origin.getZ() + dz;
				int localSurface = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);

				if (distance <= radius) {
					// The bowl: parabolic, deepest at the centre.
					double t = distance / radius;
					int bowl = (int) Math.round(depth * (1.0 - t * t));
					if (bowl <= 0) continue;

					for (int y = localSurface; y > localSurface - bowl; y--) {
						cursor.set(x, y, z);
						if (level.getBlockState(cursor).isAir()) continue;
						level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
					}
					// Floor: exposed rock, dusted with the regolith the impact threw back.
					int floorY = localSurface - bowl;
					cursor.set(x, floorY, z);
					if (!level.getBlockState(cursor).isAir()) {
						level.setBlock(cursor, config.exposeFloor() && t < 0.75 ? basalt : regolith, 2);
					}
					continue;
				}

				// The rim: the excavated material, tapering outward.
				double outward = (distance - radius) / (reach - radius);
				int raise = (int) Math.round(rim * (1.0 - outward) * (1.0 - outward));
				for (int i = 1; i <= raise; i++) {
					cursor.set(x, localSurface + i, z);
					if (!level.getBlockState(cursor).isAir()) break;
					level.setBlock(cursor, regolith, 2);
				}
			}
		}
		return true;
	}
}
