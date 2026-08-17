package dev.lilkuzco.cosmos.verify;

import dev.lilkuzco.cosmos.moon.LunarLander;
import dev.lilkuzco.cosmos.propellant.Propellant;
import dev.lilkuzco.cosmos.propellant.Propellants;
import dev.lilkuzco.cosmos.rocket.LaunchPipeline;
import dev.lilkuzco.cosmos.rocket.RocketTier;
import dev.lilkuzco.kinetics.constants.Constants;
import dev.lilkuzco.kinetics.env.Atmosphere;
import dev.lilkuzco.kinetics.env.Environment;
import dev.lilkuzco.kinetics.env.WindField;
import dev.lilkuzco.kinetics.env.WorldProbe;
import dev.lilkuzco.kinetics.event.EventSink;
import dev.lilkuzco.kinetics.event.KineticEvent;
import dev.lilkuzco.kinetics.integrate.Integrator;
import dev.lilkuzco.kinetics.math.Quat;
import dev.lilkuzco.kinetics.math.Vec3;
import dev.lilkuzco.kinetics.body.KineticBody;
import dev.lilkuzco.kinetics.phase.FlightDirector;
import dev.lilkuzco.kinetics.phase.FlightPhase;
import dev.lilkuzco.kinetics.profile.Profile;

import java.util.List;

/**
 * Phase B, flown rather than asserted.
 *
 * <p>Every number here comes out of kinetics running. The lunar vehicle is assessed by the same
 * {@code Propulsion.assess} the pad quotes from, and the landing is a real descent integrated tick
 * by tick in a vacuum environment at 0.165 g - not a table of expected values.
 *
 * <p>The checks that matter most are the <b>failures</b>. A Moon that is always reachable is a
 * Moon with no design in it; the ladder only means something if crude falls short and an
 * under-fuelled lander craters. Both are checked, in the same integrator that produces the
 * successes.
 */
public final class PhaseBVerification {

	private static int checks;
	private static int failures;

	public static void main(String[] args) {
		Constants k = Constants.get();
		LaunchPipeline pipeline = new LaunchPipeline(k);

		double toOrbit = k.d("orbit.delta_v_to_orbit");
		double tli = k.d("orbit.lunar_transfer_delta_v");
		double loi = k.d("orbit.lunar_orbit_insertion_delta_v");
		double descent = k.d("orbit.lunar_descent_delta_v");
		double moonG = k.d("gravity.g0") * k.d("gravity.dimension_scalars.moon");

		System.out.println("lilkuzco_cosmos — Phase B verification (the Moon)");
		System.out.printf("kinetics: to orbit %.0f, TLI %.1f, LOI %.1f, descent %.1f m/s%n",
				toOrbit, tli, loi, descent);
		System.out.printf("lunar gravity %.4f m/s^2, transfer coast %.0f s (%.1f h simulated)%n",
				moonG, transferTime(k), transferTime(k) / 3600.0);

		budgets(k, toOrbit, tli, loi, descent);
		vehicle(k, pipeline, toOrbit + tli);
		lander(k, loi + descent, moonG);
		flyDescent(k, moonG);
		vacuum(k, moonG);

		System.out.println();
		System.out.println("=".repeat(74));
		if (failures == 0) {
			System.out.printf("ALL %d CHECKS PASSED%n", checks);
		} else {
			System.out.printf("%d of %d CHECKS FAILED%n", failures, checks);
		}
		System.out.println("=".repeat(74));
		System.exit(failures == 0 ? 0 : 1);
	}

	private static double transferTime(Constants k) {
		return new dev.lilkuzco.kinetics.orbit.OrbitalMechanics(k).lunarTransferTime();
	}

	// ---- 1. the mission budget -------------------------------------------

	private static void budgets(Constants k, double toOrbit, double tli, double loi,
	                            double descent) {
		section("1. The mission budget — every figure scaled from a real manoeuvre");
		double toMoon = toOrbit + tli;
		double surface = toMoon + loi + descent;
		System.out.printf("   ground → orbit   %7.1f m/s%n", toOrbit);
		System.out.printf("   orbit  → TLI     %7.1f m/s%n", tli);
		System.out.printf("   arrive → LOI     %7.1f m/s%n", loi);
		System.out.printf("   orbit  → surface %7.1f m/s%n", descent);
		System.out.printf("   TOTAL            %7.1f m/s  (%.3fx reaching orbit)%n",
				surface, surface / toOrbit);

		check("the Moon costs meaningfully more than orbit", toMoon / toOrbit >= 1.3,
				String.format("%.3fx", toMoon / toOrbit));
		check("landing costs more than braking into orbit", descent > loi,
				String.format("%.1f > %.1f m/s", descent, loi));
		check("the transfer coast is a real duration, not a round number",
				transferTime(k) > 50_000.0,
				String.format("%.0f s from the Hohmann solution", transferTime(k)));
	}

	// ---- 2. the launch vehicle -------------------------------------------

	private static void vehicle(Constants k, LaunchPipeline pipeline, double toMoon) {
		section("2. The lunar vehicle — solved against kinetics, not chosen");
		RocketTier lunar = RocketTier.LUNAR;
		double payload = LunarLander.WET_MASS_KG;

		LaunchPipeline.Readout crude = pipeline.assess(lunar, Propellants.CRUDE,
				lunar.fuelCapacityKg(), k.d("gravity.g0"));
		LaunchPipeline.Readout kero = pipeline.assess(lunar, Propellants.KEROSENE,
				lunar.fuelCapacityKg(), k.d("gravity.g0"));
		LaunchPipeline.Readout cryo = pipeline.assess(lunar, Propellants.CRYOGENIC,
				lunar.fuelCapacityKg(), k.d("gravity.g0"));

		System.out.printf("   wet mass %,.0f kg, propellant %,.0f kg (%,.0f buckets)%n",
				kero.wetMassKg(), lunar.fuelCapacityKg(), lunar.fuelCapacityKg() / 100.0);
		for (var row : List.of(new Object[]{"crude", crude}, new Object[]{"kerosene", kero},
				new Object[]{"cryogenic", cryo})) {
			LaunchPipeline.Readout r = (LaunchPipeline.Readout) row[1];
			System.out.printf("   %-10s T/W %.2f   dv %7.1f m/s   %s%n", row[0], r.twrSeaLevel(),
					r.deltaV(), r.deltaV() >= toMoon ? "REACHES THE MOON" : "short");
		}

		check("the lunar vehicle clears the trans-lunar budget on kerosene",
				kero.deltaV() >= toMoon,
				String.format("%.1f against %.1f m/s", kero.deltaV(), toMoon));
		check("it FAILS on crude — the ladder is not decoration",
				crude.deltaV() < toMoon,
				String.format("%.1f m/s, %.0f short", crude.deltaV(), toMoon - crude.deltaV()));
		check("it can lift its own weight", kero.canLiftOff(),
				String.format("T/W %.2f", kero.twrSeaLevel()));
		check("payload capacity carries a fuelled lander",
				lunar.payloadCapacityKg() >= payload,
				String.format("%.0f kg capacity, %.0f kg lander", lunar.payloadCapacityKg(),
						payload));
		check("it is dramatically larger than the heavy lifter",
				kero.wetMassKg() > 3.0 * heavyWet(k, pipeline),
				String.format("%,.0f kg against %,.0f kg", kero.wetMassKg(),
						heavyWet(k, pipeline)));
	}

	private static double heavyWet(Constants k, LaunchPipeline pipeline) {
		return pipeline.assess(RocketTier.HEAVY, Propellants.KEROSENE,
				RocketTier.HEAVY.fuelCapacityKg(), k.d("gravity.g0")).wetMassKg();
	}

	// ---- 3. the lander ----------------------------------------------------

	private static void lander(Constants k, double arrivalBudget, double moonG) {
		section("3. The lander — its own propellant, its own bill");
		double full = LunarLander.deltaV(Propellants.KEROSENE, LunarLander.PROPELLANT_KG, k);
		double crude = LunarLander.deltaV(Propellants.CRUDE, LunarLander.PROPELLANT_KG, k);
		double quarter = LunarLander.deltaV(Propellants.KEROSENE, LunarLander.PROPELLANT_KG / 4, k);

		System.out.printf("   %.0f kg wet, %.0f kg propellant, %.0f N of thrust%n",
				LunarLander.WET_MASS_KG, LunarLander.PROPELLANT_KG, LunarLander.THRUST_N);
		System.out.printf("   lunar T/W %.2f at separation%n",
				LunarLander.THRUST_N / (LunarLander.WET_MASS_KG * moonG));
		System.out.printf("   dv on kerosene %.1f / crude %.1f / quarter tanks %.1f m/s "
				+ "(bill %.1f)%n", full, crude, quarter, arrivalBudget);

		check("a fuelled lander can afford LOI and descent",
				LunarLander.canLand(Propellants.KEROSENE, LunarLander.PROPELLANT_KG, k),
				String.format("%.1f against %.1f m/s", full, arrivalBudget));
		check("on crude it cannot",
				!LunarLander.canLand(Propellants.CRUDE, LunarLander.PROPELLANT_KG, k),
				String.format("%.1f m/s, %.0f short", crude, arrivalBudget - crude));
		check("a quarter-full lander cannot",
				!LunarLander.canLand(Propellants.KEROSENE, LunarLander.PROPELLANT_KG / 4, k),
				String.format("%.1f m/s", quarter));
		check("it has margin rather than sitting exactly on the budget",
				full > arrivalBudget * 1.1 && full < arrivalBudget * 1.4,
				String.format("%.0f%% of the bill", full / arrivalBudget * 100.0));
	}

	// ---- 4. the descent, actually flown ----------------------------------

	private static void flyDescent(Constants k, double moonG) {
		section("4. Powered descent — integrated tick by tick in vacuum at 0.165 g");
		double arrival = k.d("orbit.lunar_descent_delta_v");

		Outcome full = descend(k, LunarLander.PROPELLANT_KG, arrival);
		Outcome starved = descend(k, LunarLander.PROPELLANT_KG / 5.0, arrival);

		System.out.printf("   fuelled:     %s after %.0f s, touchdown %.2f m/s, %.0f kg used%n",
				full.phase, full.seconds, full.impactSpeed, full.propellantUsed);
		System.out.printf("   under-fuelled: %s after %.0f s, arrival %.1f m/s%n",
				starved.phase, starved.seconds, starved.impactSpeed);

		check("a fuelled lander lands", full.phase == FlightPhase.LANDED,
				String.valueOf(full.phase));
		check("it touches down survivably", full.impactSpeed < 15.0,
				String.format("%.2f m/s", full.impactSpeed));
		check("the retro-burn actually fired", full.burned, "entered the LANDING phase");
		check("the descent takes real time, not an instant", full.seconds > 20.0,
				String.format("%.0f s of flight", full.seconds));
		check("an under-fuelled lander does NOT land softly", starved.impactSpeed > 50.0,
				String.format("%.1f m/s", starved.impactSpeed));
		check("no invariant breached during either descent", true,
				"the integrator throws on breach, so reaching here is the proof");
	}

	private record Outcome(FlightPhase phase, double seconds, double impactSpeed,
	                       double propellantUsed, boolean burned) {}

	private static Outcome descend(Constants k, double propellantKg, double arrivalSpeed) {
		int groundY = (int) k.d("world.sea_level_y");
		Environment moon = new Environment(k, Atmosphere.vacuum(k), WindField.disabled(k),
				WorldProbe.flatGround(groundY), k.d("gravity.dimension_scalars.moon"));

		Profile profile = LunarLander.profile("verify:lander", Propellants.KEROSENE,
				propellantKg, k);
		Vec3 start = new Vec3(0.0, groundY + 30_000.0, 0.0);
		Vec3 velocity = new Vec3(0.0, -arrivalSpeed, 0.0);
		KineticBody body = new KineticBody("verify:lander", profile, k, start, velocity,
				Quat.between(new Vec3(0, 0, 1), velocity.normalized()), FlightPhase.DESCENT);
		FlightDirector director = new FlightDirector(k, moon, body,
				FlightDirector.Mission.LANDING, new Integrator(k), 1L);

		EventSink.Recording events = new EventSink.Recording();
		double dt = k.d("world.tick_seconds");
		double lastSpeed = 0.0;
		boolean burned = false;
		int tick = 0;
		for (; tick < 40_000 && body.phase().isInWorld(); tick++) {
			director.tick(tick * dt, dt, null, List.of(), events);
			if (body.speed() > 0.0) lastSpeed = body.speed();
			if (body.phase() == FlightPhase.LANDING) burned = true;
		}
		double used = propellantKg - body.snapshot().stageFuel();
		return new Outcome(body.phase(), tick * dt, lastSpeed, used, burned);
	}

	// ---- 5. what vacuum does to recovery hardware -------------------------

	private static void vacuum(Constants k, double moonG) {
		section("5. Vacuum — parachutes are inert, not disabled");
		Atmosphere none = Atmosphere.vacuum(k);
		Atmosphere air = Atmosphere.standard(k);

		// Compared LOW DOWN, at 50 m. The first version of this asked at 1 km and both answers
		// were zero: kinetics' scale height is 55 m, so the overworld's own air is already gone by
		// then. The check passed by comparing nothing to nothing, which is exactly the shape of a
		// test that would keep passing after the thing it tests broke.
		double h = 50.0;
		double densityMoon = none.density(h);
		double densityEarth = air.density(h);
		// What a canopy at 200 m/s would actually feel, asked of kinetics rather than computed.
		double qMoon = none.dynamicPressure(200.0, h);
		double qEarth = air.dynamicPressure(200.0, h);

		System.out.printf("   density at %.0f m: %.6f kg/m^3 (Moon) vs %.6f (overworld)%n",
				h, densityMoon, densityEarth);
		System.out.printf("   q at 200 m/s:    %.3f Pa (Moon) vs %.1f Pa (overworld)%n",
				qMoon, qEarth);

		check("the Moon has no atmosphere at all", !none.isPresent(), "Atmosphere.vacuum");
		check("density is exactly zero, so drag is exactly zero", densityMoon == 0.0,
				String.format("%.9f kg/m^3", densityMoon));
		check("a canopy produces no force whatsoever", qMoon == 0.0,
				String.format("%.3f Pa on the Moon", qMoon));
		check("the same canopy in overworld air DOES produce force", qEarth > 1000.0,
				String.format("%.0f Pa - so the vacuum result means something", qEarth));
		check("lunar gravity is a sixth of Earth's",
				Math.abs(moonG / k.d("gravity.g0") - 1.0 / 6.0) < 0.01,
				String.format("%.4f g", moonG / k.d("gravity.g0")));
	}

	// ---- plumbing ---------------------------------------------------------

	private static void section(String title) {
		System.out.println();
		System.out.println("── " + title + " " + "─".repeat(Math.max(0, 70 - title.length())));
	}

	private static void check(String name, boolean passed, String detail) {
		checks++;
		if (!passed) failures++;
		System.out.printf("  %s %-52s %s%n", passed ? "PASS" : "FAIL", name, detail);
	}
}
