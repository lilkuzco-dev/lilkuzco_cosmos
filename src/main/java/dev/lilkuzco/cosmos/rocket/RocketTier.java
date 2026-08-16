package dev.lilkuzco.cosmos.rocket;

import dev.lilkuzco.cosmos.propellant.Propellant;
import dev.lilkuzco.kinetics.constants.Constants;
import dev.lilkuzco.kinetics.profile.Airframe;
import dev.lilkuzco.kinetics.profile.Profile;
import dev.lilkuzco.kinetics.profile.Recovery;
import dev.lilkuzco.kinetics.profile.SeekerSpec;
import dev.lilkuzco.kinetics.profile.Stage;

import java.util.ArrayList;
import java.util.List;

/**
 * A rocket design: structure, tankage and engines. <b>Not</b> a performance rating.
 *
 * <p>The tier owns the airframe and the plumbing. The propellant owns the specific impulse. What
 * the vehicle can actually do is whatever falls out of Tsiolkovsky when the two are combined, and
 * cosmos never decides it - {@link #buildProfile} hands kinetics a profile and kinetics answers.
 *
 * <p>That indirection produces the progression for free. The orbital tier below flies to orbit on
 * refined kerosene and <em>fails</em> on raw crude - same rocket, same tanks, same engines. It
 * lifts off cleanly either way and then runs out of delta-v, which is the honest failure RD3
 * asks for. Nothing gates the tier on the fuel; the logarithm does it.
 *
 * <table>
 *   <caption>Delta-v against the 2230 m/s budget</caption>
 *   <tr><th>Tier</th><th>on crude (232 s)</th><th>on kerosene (311 s)</th><th>on cryogenic (450 s)</th></tr>
 *   <tr><td>sounding</td><td>1041</td><td>1396</td><td>2020</td></tr>
 *   <tr><td>orbital</td><td>1790</td><td>2400</td><td>3473</td></tr>
 *   <tr><td>heavy</td><td>1937</td><td>2597</td><td>3757</td></tr>
 * </table>
 */
public record RocketTier(
        String id,
        String translationKey,
        double payloadDryMass,
        List<StageSpec> stages,
        double referenceArea,
        double dragCoefficient,
        double gLimitG,
        double qMaxPa,
        double noseRadius,
        double rcs,
        int padRadius,
        int padHeight,
        double payloadCapacityKg) {

    /** One stage's structure and plumbing. Isp is supplied by the propellant, not stored here. */
    public record StageSpec(String engineName, double dryMass, double fuelCapacityKg,
                            double thrustVacuum) {}

    // ---- the ladder -------------------------------------------------------

    /**
     * Tier 1 - a sounding rocket. Single stage, cheap, and it will never reach orbit on anything,
     * not even cryogenic. That is deliberate: it exists to teach the pad, the countdown and the
     * recovery chain without asking for a refinery first.
     */
    public static final RocketTier SOUNDING = new RocketTier(
            "cosmos:sounding", "cosmos.rocket.sounding",
            40.0,
            List.of(new StageSpec("sounding_motor", 300.0, 2000.0, 40000.0)),
            1.2, 0.35, 12.0, 80000.0, 0.5, 4.0,
            2, 6, 40.0);

    /**
     * Tier 2 - the workhorse. Two stages, 19.6 tonnes on the pad for 100 kg in orbit, and the
     * vehicle the whole propellant ladder is calibrated against.
     */
    public static final RocketTier ORBITAL = new RocketTier(
            "cosmos:orbital", "cosmos.rocket.orbital",
            100.0,
            List.of(new StageSpec("first_stage", 2276.0, 16691.0, 380752.0),
                    new StageSpec("upper_stage", 60.0, 500.0, 12945.0)),
            3.5, 0.35, 12.0, 80000.0, 0.9, 12.0,
            3, 12, 100.0);

    /**
     * Tier 3 - heavy lifter. Three stages, 42.5 tonnes for a 400 kg payload. Enough margin to put
     * a real satellite up on kerosene rather than only just clearing the budget.
     */
    public static final RocketTier HEAVY = new RocketTier(
            "cosmos:heavy", "cosmos.rocket.heavy",
            400.0,
            List.of(new StageSpec("heavy_first_stage", 4000.0, 30000.0, 700000.0),
                    new StageSpec("heavy_second_stage", 800.0, 6000.0, 120000.0),
                    new StageSpec("heavy_third_stage", 150.0, 1200.0, 20000.0)),
            6.0, 0.35, 10.0, 80000.0, 1.2, 20.0,
            4, 18, 400.0);

    public static final List<RocketTier> LADDER = List.of(SOUNDING, ORBITAL, HEAVY);

    public static RocketTier byId(String id) {
        for (RocketTier t : LADDER) if (t.id().equals(id)) return t;
        return null;
    }

    // ---- profile construction --------------------------------------------

    /** Total propellant this design can hold, kg. */
    public double fuelCapacityKg() {
        double total = 0.0;
        for (StageSpec s : stages) total += s.fuelCapacityKg();
        return total;
    }

    /**
     * Build the kinetics profile for this rocket as actually fuelled.
     *
     * <p>Under-fuelling is honest: propellant is filled from the bottom stage up, so a half-full
     * vehicle is a full first stage and an empty second, and its delta-v is exactly what that
     * arrangement earns. Nothing rounds it up.
     *
     * @param propellant grade in the tanks, supplying both Isp figures
     * @param fuelKg     propellant actually loaded, clamped to capacity
     */
    public Profile buildProfile(String bodyId, Propellant propellant, double fuelKg,
                                Constants k) {
        double remaining = Math.max(0.0, Math.min(fuelKg, fuelCapacityKg()));

        List<Stage> built = new ArrayList<>(stages.size());
        for (StageSpec spec : stages) {
            double loaded = Math.min(remaining, spec.fuelCapacityKg());
            remaining -= loaded;
            built.add(new Stage(spec.engineName(), loaded, spec.dryMass(), spec.thrustVacuum(),
                    propellant.ispSeaLevel(), propellant.ispVacuum()));
        }

        Airframe airframe = new Airframe(
                referenceArea, 0.0, dragCoefficient, Double.POSITIVE_INFINITY,
                k.d("aerodynamics.oswald_efficiency_default"),
                0.0,
                k.d("aerodynamics.default_stall_aoa_deg"),
                k.d("aerodynamics.post_stall_aoa_deg"),
                k.d("aerodynamics.post_stall_cl_fraction"),
                gLimitG, qMaxPa, noseRadius,
                k.d("reentry.overheat_threshold_default"), rcs);

        return new Profile(bodyId, payloadDryMass, built, airframe,
                Recovery.none(), SeekerSpec.none(),
                0, 12.0, 0.0);
    }

    /**
     * The profile for the returning payload capsule: blunt, drogue then main.
     *
     * <p>The ballistic coefficient here is not a style choice. Kinetics' atmosphere carries
     * 1/155th of Earth's column mass, so a capsule with a real-world beta arrives at the ground
     * still supersonic. Recovery hardware in cosmos is visibly, necessarily large.
     */
    public Profile buildCapsuleProfile(String bodyId, Constants k) {
        double mass = Math.max(payloadDryMass, 40.0);
        // Hold beta near 26 kg/m^2, the figure kinetics' own reentry battery is built around.
        double shieldArea = mass / (1.35 * 26.0);

        Airframe airframe = new Airframe(
                shieldArea, 0.0, 1.35, Double.POSITIVE_INFINITY,
                k.d("aerodynamics.oswald_efficiency_default"),
                0.0,
                k.d("aerodynamics.default_stall_aoa_deg"),
                k.d("aerodynamics.post_stall_aoa_deg"),
                k.d("aerodynamics.post_stall_cl_fraction"),
                30.0, 90000.0, 1.5, 250000.0, 3.0);

        Recovery recovery = new Recovery(List.of(
                new Recovery.Parachute("drogue", 1.40, shieldArea * 0.72, 18000.0, 200.0),
                new Recovery.Parachute("main", 1.65, shieldArea * 3.2, 5000.0, 90.0)));

        return new Profile(bodyId, mass, List.of(), airframe, recovery, SeekerSpec.none(),
                0, 25.0, 0.0);
    }
}
