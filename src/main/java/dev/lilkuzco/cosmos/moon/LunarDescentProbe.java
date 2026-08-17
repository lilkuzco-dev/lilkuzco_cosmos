package dev.lilkuzco.cosmos.moon;

import dev.lilkuzco.cosmos.Cosmos;
import dev.lilkuzco.cosmos.propellant.Propellant;
import dev.lilkuzco.kinetics.fabric.KineticsMod;
import dev.lilkuzco.kinetics.fabric.KineticsService;
import dev.lilkuzco.kinetics.math.Vec3;
import dev.lilkuzco.kinetics.phase.FlightDirector;
import dev.lilkuzco.kinetics.phase.FlightPhase;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Flies an uncrewed lander in the real Moon dimension and logs what happens.
 *
 * <p>The headless battery already proves the descent law over {@code WorldProbe.flatGround}. This
 * proves the <b>same law over the terrain that actually generated</b>, through the real
 * {@code MinecraftWorldProbe}, in a dimension registered by the real mod - which is a different
 * claim, and the one that would break if the Moon were registered with an atmosphere, or if the
 * lander were spawned into a dimension kinetics had never heard of.
 *
 * <p>No player required, so it runs on a headless server and its evidence is a log.
 */
public final class LunarDescentProbe {

	private static final class Run {
		final String bodyId;
		final String label;
		double lastSpeed;
		double startAltitude;
		int ticks;
		boolean burned;
		FlightPhase lastPhase = FlightPhase.DESCENT;

		Run(String bodyId, String label) {
			this.bodyId = bodyId;
			this.label = label;
		}
	}

	private static final Map<String, Run> RUNS = new LinkedHashMap<>();
	private static boolean registered;

	private LunarDescentProbe() {
	}

	public static void register() {
		if (registered) return;
		registered = true;
		ServerTickEvents.END_SERVER_TICK.register(LunarDescentProbe::tick);
	}

	/**
	 * Drop a lander from the arrival altitude with the arrival velocity.
	 *
	 * @param propellantKg how much it carries; under-fuel it and it should crater
	 * @return the body id, or null if kinetics refused
	 */
	public static String drop(MinecraftServer server, Propellant propellant, double propellantKg,
	                          double x, double z, String label) {
		KineticsService kinetics = KineticsMod.service();
		if (kinetics == null) return null;
		if (server.getLevel(MoonDimension.MOON) == null) return null;

		register();
		var k = kinetics.constants();
		double arrival = k.d("orbit.lunar_descent_delta_v");
		double y = k.d("world.sea_level_y") + LunarTransit.ARRIVAL_ALTITUDE;

		String bodyId = "cosmos:probe-" + label + "-" + RUNS.size();
		var profile = LunarLander.profile(bodyId, propellant, propellantKg, k);
		KineticsService.Handle handle = kinetics.spawn(bodyId, profile, MoonDimension.MOON,
				new Vec3(x, y, z), new Vec3(0.0, -arrival, 0.0),
				FlightDirector.Mission.LANDING);
		if (handle == null) {
			Cosmos.LOG.error("probe {}: kinetics refused a lander in {} - not registered?",
					label, MoonDimension.MOON.identifier());
			return null;
		}

		Run run = new Run(bodyId, label);
		run.startAltitude = LunarTransit.ARRIVAL_ALTITUDE;
		RUNS.put(bodyId, run);
		Cosmos.LOG.info("probe {}: released at {} m over ({}, {}) at {} m/s with {} kg of "
						+ "propellant ({} m/s of delta-v against a {} m/s bill)",
				label, String.format("%.0f", LunarTransit.ARRIVAL_ALTITUDE),
				String.format("%.0f", x), String.format("%.0f", z),
				String.format("%.1f", arrival), String.format("%.0f", propellantKg),
				String.format("%.1f", LunarLander.deltaV(propellant, propellantKg, k)),
				String.format("%.1f", LunarLander.arrivalBudget(k)));
		return bodyId;
	}

	private static void tick(MinecraftServer server) {
		if (RUNS.isEmpty()) return;
		KineticsService kinetics = KineticsMod.service();
		if (kinetics == null) { RUNS.clear(); return; }
		var k = kinetics.constants();

		List<String> done = null;
		for (Run run : List.copyOf(RUNS.values())) {
			KineticsService.Handle handle = kinetics.handle(run.bodyId);
			if (handle == null) {
				report(run, null, 0.0);
				if (done == null) done = new ArrayList<>();
				done.add(run.bodyId);
				continue;
			}

			var body = handle.body();
			run.ticks++;
			if (body.speed() > 0.0) run.lastSpeed = body.speed();
			if (body.phase() == FlightPhase.LANDING) run.burned = true;

			if (body.phase() != run.lastPhase) {
				Cosmos.LOG.info("probe {}: {} -> {} at {} m, {} m/s", run.label, run.lastPhase,
						body.phase(),
						String.format("%.0f", body.position().y() - k.d("world.sea_level_y")),
						String.format("%.1f", body.speed()));
				run.lastPhase = body.phase();
			}
			// One line a second, so a watching harness can tell the difference between a long
			// descent and a hung server.
			if (run.ticks % 20 == 0) {
				Cosmos.LOG.info("probe {}: t+{}s  {} m  {} m/s  {}", run.label, run.ticks / 20,
						String.format("%.0f", body.position().y() - k.d("world.sea_level_y")),
						String.format("%.1f", body.speed()), body.phase());
			}

			if (body.phase().isTerminal()) {
				report(run, body.phase(), body.position().y() - k.d("world.sea_level_y"));
				kinetics.despawn(run.bodyId);
				if (done == null) done = new ArrayList<>();
				done.add(run.bodyId);
			}
		}
		if (done != null) done.forEach(RUNS::remove);
	}

	private static void report(Run run, FlightPhase phase, double altitude) {
		Cosmos.LOG.info("probe {}: RESULT {} after {} s at {} m over real lunar terrain, "
						+ "arrival {} m/s, retro-burn {}",
				run.label, phase == null ? "vanished" : phase, run.ticks / 20,
				String.format("%.0f", altitude), String.format("%.2f", run.lastSpeed),
				run.burned ? "fired" : "NEVER FIRED");
	}
}
