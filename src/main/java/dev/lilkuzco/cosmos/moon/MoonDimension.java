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

	public static final ResourceKey<Level> MOON =
			ResourceKey.create(Registries.DIMENSION, Cosmos.id("moon"));

	/** Lunar surface gravity as a fraction of g0 — the real 1.62 m/s^2 over 9.80665. */
	public static final double GRAVITY_SCALAR = 0.16519;

	private MoonDimension() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(new ServerTickEvents.EndTick() {
			private boolean done;

			@Override
			public void onEndTick(MinecraftServer server) {
				if (done) return;
				KineticsService kinetics = KineticsMod.service();
				if (kinetics == null) return;

				ServerLevel moon = server.getLevel(MOON);
				if (moon == null) {
					// A world created before cosmos was installed has no Moon until it is
					// regenerated. Say so once rather than retrying forever.
					done = true;
					Cosmos.LOG.warn("no {} dimension in this world - lunar physics not registered",
							MOON.identifier());
					return;
				}

				done = true;
				kinetics.registerDimension(moon, false, GRAVITY_SCALAR, false);
				Cosmos.LOG.info("moon registered with kinetics: {} g, vacuum "
						+ "(no drag, no lift, parachutes inert)", GRAVITY_SCALAR);
			}
		});
	}

	/** Whether a level is the Moon. */
	public static boolean isMoon(Level level) {
		return level.dimension().equals(MOON);
	}
}
