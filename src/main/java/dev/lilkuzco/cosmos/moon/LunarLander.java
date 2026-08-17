package dev.lilkuzco.cosmos.moon;

import dev.lilkuzco.cosmos.propellant.Propellant;
import dev.lilkuzco.kinetics.constants.Constants;
import dev.lilkuzco.kinetics.profile.Airframe;
import dev.lilkuzco.kinetics.profile.Profile;
import dev.lilkuzco.kinetics.profile.Recovery;
import dev.lilkuzco.kinetics.profile.SeekerSpec;
import dev.lilkuzco.kinetics.profile.Stage;
import dev.lilkuzco.kinetics.propulsion.Propulsion;

import java.util.List;

/**
 * The lander: the only part of a Moon rocket that touches the Moon.
 *
 * <p><b>It carries its own propellant, and that is the whole design.</b> The launch vehicle's job
 * ends at trans-lunar injection - it has spent every gram getting a 500 kg payload onto a transfer
 * ellipse. Braking into lunar orbit and descending to the surface are the lander's bill, and it is
 * a big one: 657 m/s, of which 444 is descent alone.
 *
 * <p>Descent is expensive because the Moon has no air. There is nothing to give the arrival energy
 * to, so every metre per second has to be cancelled by the engine, and gravity keeps adding more
 * while it burns. A parachute here is not weak, it is <em>inert</em>: kinetics computes q = 0 in
 * vacuum, so a canopy produces exactly zero force. That is not a rule cosmos wrote; it is what the
 * drag equation does when density is zero.
 *
 * <p><b>Not refuellable.</b> A lander is built with its propellant and has one landing in it. That
 * is a stated Phase B limitation, not a physical claim - a later phase that adds surface refuelling
 * changes only where the propellant comes from, never what it buys.
 */
public final class LunarLander {

	/** Structure that survives the burn: the lander a player walks away from. */
	public static final double DRY_MASS_KG = 160.0;

	/** Propellant built into it, kg. Sized against LOI + descent with 15% margin. */
	public static final double PROPELLANT_KG = 300.0;

	/**
	 * Descent engine thrust, N. Chosen for a lunar T/W near 3.4 at full mass.
	 *
	 * <p>Not more. A landing burn is a race between shedding velocity and running out of ground,
	 * and huge thrust wins it trivially - the suicide-burn height collapses to nothing and the
	 * landing stops being a decision. Around 3 g of margin over lunar gravity leaves the burn
	 * long enough to watch and short enough to survive.
	 */
	public static final double THRUST_N = 2500.0;

	/** Total mass at separation, kg. */
	public static final double WET_MASS_KG = DRY_MASS_KG + PROPELLANT_KG;

	private LunarLander() {
	}

	/**
	 * The lander's kinetics profile.
	 *
	 * <p>Half the dry mass is the descent stage that is shed at burnout and half is the lander
	 * itself, so the mass ledger still has something in it when the engine quits - kinetics
	 * rejects a profile that would end at zero kilograms, and it is right to.
	 */
	public static Profile profile(String bodyId, Propellant propellant, double propellantKg,
	                              Constants k) {
		double loaded = Math.max(0.0, Math.min(propellantKg, PROPELLANT_KG));
		Airframe airframe = new Airframe(
				2.0, 0.0, 0.35, Double.POSITIVE_INFINITY,
				k.d("aerodynamics.oswald_efficiency_default"), 0.0,
				k.d("aerodynamics.default_stall_aoa_deg"),
				k.d("aerodynamics.post_stall_aoa_deg"),
				k.d("aerodynamics.post_stall_cl_fraction"),
				20.0, 1.0e9, 1.4, k.d("reentry.overheat_threshold_default"), 40.0);

		return new Profile(bodyId, DRY_MASS_KG / 2.0,
				List.of(new Stage("lander_descent", loaded, DRY_MASS_KG / 2.0, THRUST_N,
						propellant.ispSeaLevel(), propellant.ispVacuum())),
				airframe, Recovery.none(), SeekerSpec.none(), 0, 90.0, 0.0);
	}

	/** Delta-v this lander has, m/s. Kinetics computes it; cosmos only asks. */
	public static double deltaV(Propellant propellant, double propellantKg, Constants k) {
		return new Propulsion(k)
				.assess(profile("cosmos:lander-assess", propellant, propellantKg, k),
						k.d("gravity.g0") * k.d("gravity.dimension_scalars.moon"))
				.idealDeltaV();
	}

	/** What arriving at the Moon costs: lunar orbit insertion plus powered descent, m/s. */
	public static double arrivalBudget(Constants k) {
		return k.d("orbit.lunar_orbit_insertion_delta_v") + k.d("orbit.lunar_descent_delta_v");
	}

	/** Whether this lander can both brake into lunar orbit and land. */
	public static boolean canLand(Propellant propellant, double propellantKg, Constants k) {
		return deltaV(propellant, propellantKg, k) >= arrivalBudget(k);
	}
}
