package dev.lilkuzco.cosmos.moon;

import dev.lilkuzco.cosmos.Cosmos;
import dev.lilkuzco.kinetics.fabric.KineticsMod;
import dev.lilkuzco.kinetics.fabric.KineticsService;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Registers the Moon with kinetics, and that single call is what makes it the Moon.
 *
 * <p>Gravity 0.16519 g and <b>no atmosphere</b>. Everything a player notices follows from those
 * two facts rather than from any lunar special-casing:
 *
 * <ul>
 *   <li><b>Parachutes are useless.</b> Not disabled - useless. A canopy in vacuum computes
 *       {@code q = 0} and produces exactly zero drag, so a chute-only lander arrives at whatever
 *       speed gravity gave it.</li>
 *   <li><b>Landing is retro-thrust,</b> and it is physics rather than scripting: a descent stage
 *       must actually cancel its velocity with RD1 delta-v, at vacuum Isp per RD2b.</li>
 *   <li><b>There is nothing to breathe,</b> which {@code LifeSupport} reads from this same
 *       atmosphere rather than from a dimension whitelist.</li>
 * </ul>
 *
 * <p>Registration is deferred to the first server tick for the same reason the satellite roster's
 * is: Fabric gives no ordering guarantee between two mods' SERVER_STARTED handlers, and cosmos
 * once ran before kinetics existed.
 */
public final class MoonDimension {

	/** The Moon. Kept here because the whole of Phase B names it; the facts live in CosmosWorlds. */
	public static final ResourceKey<Level> MOON = dev.lilkuzco.cosmos.world.CosmosWorlds.MOON;

	/** The polar biome, where the ice is. */
	public static final ResourceKey<net.minecraft.world.level.biome.Biome> POLAR =
			dev.lilkuzco.cosmos.world.CosmosWorlds.LUNAR_POLAR;

	/** Lunar surface gravity as a fraction of g0 - the real 1.62 m/s^2 over 9.80665. */
	public static final double GRAVITY_SCALAR = 0.16519;

	private MoonDimension() {
	}

	/**
	 * Registration moved to {@link dev.lilkuzco.cosmos.world.CosmosWorlds}, which registers every
	 * cosmos world from one list. A second destination made a Moon-specific registrar into a
	 * template waiting to be copied, and a copied registrar is how two worlds drift apart.
	 */
	public static void register() {
		dev.lilkuzco.cosmos.world.CosmosWorlds.register();
	}

	/** Whether a level is the Moon. */
	public static boolean isMoon(Level level) {
		return level.dimension().equals(MOON);
	}
}
