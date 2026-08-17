package dev.lilkuzco.cosmos;

import dev.lilkuzco.cosmos.fluid.HydroloxFluid;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluid;

/**
 * Cosmos' one fluid.
 *
 * <p>Hydrolox is made on the Moon out of lunar ice, and it is the only propellant in the empire
 * that cosmos produces rather than declares. Every other rung of the ladder is a contract another
 * mod fills; this one exists because the mod that would fill it is physics.
 *
 * <p>It is tagged into {@code #cosmos:propellant/cryogenic}, the grade that has carried hydrolox
 * Isp figures and no fluid since Phase A. Lighting that rung from the lunar surface is the whole
 * point of the lunar economy: the best propellant in the game is the one you cannot buy, cannot
 * refine on Earth, and have to go and make.
 */
public final class CosmosFluids {

	public static final Fluid HYDROLOX = new HydroloxFluid();

	private CosmosFluids() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.FLUID, Cosmos.id("hydrolox"), HYDROLOX);
	}
}
