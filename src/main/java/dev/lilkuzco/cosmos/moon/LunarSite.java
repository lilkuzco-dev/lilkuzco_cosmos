package dev.lilkuzco.cosmos.moon;

import dev.lilkuzco.cosmos.Cosmos;
import dev.lilkuzco.cosmos.CosmosBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;

/**
 * What a lander leaves behind once it is down.
 *
 * <p>A soft landing converts the descent stage into an <b>oxygen station</b> at the touchdown
 * point. Not a gift: it is the lander's own life-support hardware, which is exactly the part of a
 * spacecraft you would repurpose the moment you stopped needing it to fly. Every real lunar plan
 * has ever assumed the same thing.
 *
 * <p>It matters mechanically too. A player who lands correctly should not then suffocate because
 * arriving alive and staying alive were designed as separate problems - the whole point of the
 * delta-v budget is that the landing is the test. Passing it earns a foothold: somewhere to refill
 * a suit, and therefore somewhere to build outward from.
 *
 * <p><b>Getting home is not solved in Phase B.</b> There is no ascent vehicle and no return path;
 * a lunar base is currently somewhere you go and stay. That is a stated scope limit, not an
 * oversight - a return trip needs propellant produced on the Moon, and lunar industry is exactly
 * what ECONOMY.md exists to propose rather than assume.
 */
public final class LunarSite {

	private LunarSite() {
	}

	/**
	 * Stand the landed hardware up at the touchdown point.
	 *
	 * <p>Placed one block below the crew so it is underfoot rather than inside them, and only over
	 * something solid - a lander that came down on the lip of a crater should not have its station
	 * hanging in the air.
	 */
	public static void deliver(ServerLevel level, BlockPos where, ServerPlayer crew) {
		BlockPos site = groundUnder(level, where);
		level.setBlock(site, CosmosBlocks.OXYGEN_STATION.defaultBlockState(), Block.UPDATE_ALL);
		crew.sendSystemMessage(Component.translatable("cosmos.transit.site",
				site.getX(), site.getY(), site.getZ()));
		Cosmos.LOG.info("landing site established at {} - the descent stage is now an oxygen "
				+ "station", site);
	}

	/** The first solid block at or below the given position, so nothing is placed in mid-air. */
	private static BlockPos groundUnder(ServerLevel level, BlockPos from) {
		BlockPos.MutableBlockPos cursor = from.mutable();
		for (int drop = 0; drop < 24; drop++) {
			if (!level.getBlockState(cursor.below()).isAir()) return cursor.immutable();
			cursor.move(0, -1, 0);
		}
		return from.immutable();
	}
}
