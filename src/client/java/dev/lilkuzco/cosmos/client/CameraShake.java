package dev.lilkuzco.cosmos.client;

import dev.lilkuzco.cosmos.rocket.RocketEntity;
import dev.lilkuzco.kinetics.phase.FlightPhase;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * Camera shake near a burning rocket.
 *
 * <p>Amplitude falls as 1/r rather than 1/r², because sound pressure does - and shake is what
 * standing in a pressure wave feels like. A first stage is felt at 60 blocks and is unpleasant at
 * 10, which is roughly the message a launch pad should be sending.
 *
 * <p>Purely cosmetic and purely client-side. It reads the rocket's synced flight phase and nothing
 * else, so it cannot desynchronise anything.
 */
public final class CameraShake {

	/** Beyond this, nothing is felt. */
	private static final double MAX_DISTANCE = 60.0;
	private static final double MAX_AMPLITUDE = 1.6;

	private static float phase;

	private CameraShake() {
	}

	/** Called every client tick. Returns the current shake amplitude in degrees. */
	public static float tick(Minecraft client) {
		if (client.player == null || client.level == null) return 0.0F;

		double strongest = 0.0;
		for (Entity entity : client.level.entitiesForRendering()) {
			if (!(entity instanceof RocketEntity rocket)) continue;
			if (rocket.phase() != FlightPhase.BOOST) continue;

			double distance = Math.sqrt(client.player.distanceToSqr(rocket));
			if (distance > MAX_DISTANCE) continue;
			double amplitude = MAX_AMPLITUDE * (1.0 - distance / MAX_DISTANCE) * rocket.throttle();
			strongest = Math.max(strongest, amplitude);
		}

		if (strongest <= 0.0) return 0.0F;
		phase += 0.9F;
		// Two frequencies so it reads as rumble rather than a sine wave.
		return (float) (strongest * (Math.sin(phase) * 0.6 + Math.sin(phase * 2.7) * 0.4));
	}
}
