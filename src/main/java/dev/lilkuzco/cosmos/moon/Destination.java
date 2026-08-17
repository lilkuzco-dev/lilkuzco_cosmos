package dev.lilkuzco.cosmos.moon;

import dev.lilkuzco.cosmos.propellant.Propellant;
import dev.lilkuzco.cosmos.world.CosmosWorlds;
import dev.lilkuzco.kinetics.constants.Constants;
import dev.lilkuzco.kinetics.phase.FlightDirector;
import dev.lilkuzco.kinetics.profile.Airframe;
import dev.lilkuzco.kinetics.profile.Profile;
import dev.lilkuzco.kinetics.profile.Recovery;
import dev.lilkuzco.kinetics.profile.SeekerSpec;
import dev.lilkuzco.kinetics.profile.Stage;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Somewhere to go, and how arriving there works.
 *
 * <p>Two destinations, and <b>the difference between them is the entire reason the second one
 * exists</b>. The Moon is airless, so arriving is a rocketry problem: every metre per second has to
 * be cancelled by an engine, and a parachute produces exactly zero force. The outer moon has a
 * thick atmosphere, so arriving is an aerodynamics problem: drag does the work for free, chutes
 * inflate, and the price is reentry heating instead of propellant.
 *
 * <p>Neither of those is coded as a special case anywhere. They fall out of one boolean — whether
 * kinetics was told the dimension has an atmosphere — because every force in the model reads the
 * density it is given.
 */
public enum Destination {

	/** Airless, 0.165 g. Retro-burn or crater. */
	MOON(CosmosWorlds.MOON, "orbit.lunar_transfer_delta_v", "orbit.lunar_distance",
			30_000.0, "orbit.lunar_descent_delta_v", false),
	

	/**
	 * Thick air at 0.138 g, 120 planetary radii out. Parachutes, and a long coast.
	 *
	 * <p>Its descent budget is <b>zero</b> — the air does the braking. Huygens reached Titan's
	 * surface on chutes alone, and this is the body where cosmos' recovery hardware, built for
	 * Earth returns and useless on the Moon, becomes the right tool again.
	 */
	HAZE(CosmosWorlds.HAZE, null, "orbit.haze_distance",
			12_000.0, "orbit.haze_descent_delta_v", true);

	private final ResourceKey<Level> dimension;
	private final String injectionConstant;
	private final String distanceConstant;
	private final double arrivalAltitude;
	private final String descentConstant;
	private final boolean aerodynamicArrival;

	Destination(ResourceKey<Level> dimension, String injectionConstant, String distanceConstant,
	            double arrivalAltitude, String descentConstant, boolean aerodynamicArrival) {
		this.dimension = dimension;
		this.injectionConstant = injectionConstant;
		this.distanceConstant = distanceConstant;
		this.arrivalAltitude = arrivalAltitude;
		this.descentConstant = descentConstant;
		this.aerodynamicArrival = aerodynamicArrival;
	}

	public ResourceKey<Level> dimension() { return dimension; }

	public double arrivalAltitude() { return arrivalAltitude; }

	/** Whether a chute can hold this vehicle up, or an engine has to. */
	public boolean aerodynamicArrival() { return aerodynamicArrival; }

	public String translationKey() { return "cosmos.destination." + name().toLowerCase(); }

	/**
	 * Delta-v to depart for here, on top of reaching orbit.
	 *
	 * <p>The Moon uses its AUDITED constant, because the whole rocket ladder is sized against it
	 * and kinetics already cross-checks that figure against vis-viva to within 1.07%. The outer
	 * moon is DERIVED from the Hohmann first burn instead of getting a constant of its own -
	 * scaling another real-world number would be inventing one, and the geometry already answers
	 * the question.
	 */
	public double injectionDeltaV(Constants k) {
		if (injectionConstant != null) return k.d(injectionConstant);
		var m = new dev.lilkuzco.kinetics.orbit.OrbitalMechanics(k);
		return m.hohmannFirstBurn(m.radiusForAltitude(k.d("orbit.reference_orbit_altitude")),
				k.d(distanceConstant));
	}

	/** Total from the ground: orbit plus the departure burn. */
	public double departureBudget(Constants k) {
		return k.d("orbit.delta_v_to_orbit") + injectionDeltaV(k);
	}

	/** How fast a vehicle is going when it arrives. */
	/**
	 * How fast a vehicle is going when it arrives.
	 *
	 * <p>Two different KINDS of number, and the difference is the point. The Moon's is a
	 * propellant budget: the speed an engine must cancel. The outer moon's is an ENTRY VELOCITY
	 * the atmosphere will remove for nothing — 90% of orbital speed, arriving hot, which is why
	 * the vehicle that comes here is a heat shield rather than a rocket.
	 */
	public double arrivalSpeed(Constants k) {
		return aerodynamicArrival
				? k.d("orbit.reference_orbit_velocity") * 0.9
				: k.d(descentConstant);
	}

	/** Coast time to here, in simulated seconds, from kinetics' own Hohmann solution. */
	public double coastSeconds(Constants k) {
		var mechanics = new dev.lilkuzco.kinetics.orbit.OrbitalMechanics(k);
		return mechanics.hohmannTransferTime(
				mechanics.radiusForAltitude(k.d("orbit.reference_orbit_altitude")),
				k.d(distanceConstant));
	}

	/** The mission kinetics flies on arrival. */
	public FlightDirector.Mission arrivalMission() {
		return aerodynamicArrival ? FlightDirector.Mission.BALLISTIC
				: FlightDirector.Mission.LANDING;
	}

	/**
	 * The vehicle that arrives.
	 *
	 * <p>On the Moon it is a lander: engines, propellant, and a budget it either has or does not.
	 * Here it is a <b>capsule</b> — a blunt body under two chutes, with no engine at all, because
	 * an engine would be dead weight where the air will stop you for nothing.
	 */
	public Profile arrivalProfile(String bodyId, Propellant propellant, Constants k) {
		if (!aerodynamicArrival) {
			return LunarLander.profile(bodyId, propellant, LunarLander.PROPELLANT_KG, k);
		}

		// Sized like the recovery capsule, because it is the same problem: hold a ballistic
		// coefficient near 26 kg/m^2 so the vehicle is subsonic before the canopy limits.
		double mass = LunarLander.DRY_MASS_KG;
		double shieldArea = mass / (1.35 * 26.0);
		Airframe airframe = new Airframe(
				shieldArea, 0.0, 1.35, Double.POSITIVE_INFINITY,
				k.d("aerodynamics.oswald_efficiency_default"), 0.0,
				k.d("aerodynamics.default_stall_aoa_deg"),
				k.d("aerodynamics.post_stall_aoa_deg"),
				k.d("aerodynamics.post_stall_cl_fraction"),
				30.0, 90000.0, 1.5, k.d("reentry.overheat_threshold_default"), 3.0);

		Recovery recovery = new Recovery(List.of(
				new Recovery.Parachute("drogue", 1.40, shieldArea * 0.72, 18000.0, 200.0),
				new Recovery.Parachute("main", 1.65, shieldArea * 3.2, 5000.0, 90.0)));

		return new Profile(bodyId, mass, List.<Stage>of(), airframe, recovery, SeekerSpec.none(),
				0, 25.0, 0.0);
	}
}
