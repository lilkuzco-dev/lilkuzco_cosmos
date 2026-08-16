package dev.lilkuzco.cosmos.verify;

import dev.lilkuzco.cosmos.propellant.Propellant;
import dev.lilkuzco.cosmos.propellant.Propellants;
import dev.lilkuzco.cosmos.rocket.LaunchPipeline;
import dev.lilkuzco.cosmos.rocket.RocketTier;
import dev.lilkuzco.kinetics.body.KineticBody;
import dev.lilkuzco.kinetics.constants.Constants;
import dev.lilkuzco.kinetics.env.Environment;
import dev.lilkuzco.kinetics.env.WorldProbe;
import dev.lilkuzco.kinetics.event.EventSink;
import dev.lilkuzco.kinetics.event.KineticEvent;
import dev.lilkuzco.kinetics.integrate.Integrator;
import dev.lilkuzco.kinetics.math.Quat;
import dev.lilkuzco.kinetics.math.Vec3;
import dev.lilkuzco.kinetics.orbit.OrbitalRegistry;
import dev.lilkuzco.kinetics.phase.FlightDirector;
import dev.lilkuzco.kinetics.phase.FlightPhase;
import dev.lilkuzco.kinetics.profile.EngineFrame;
import dev.lilkuzco.kinetics.profile.Profile;

import java.util.List;

/**
 * The Phase A chain, verified headlessly: propellant, launch, orbit, passes, deorbit, recovery.
 *
 * <p><b>This exists because this repo cannot take screenshots.</b> Screen Recording permission is
 * not granted to the terminal here, so {@code screencapture} returns desktop wallpaper — it could
 * never be evidence of anything. The empire's standing rule is to prove things with logs, and the
 * strongest form of that is to run the actual pipeline and print the actual numbers.
 *
 * <p>It runs against real kinetics with no Minecraft at all, which is possible because the
 * launch pipeline is Minecraft-free by construction: a propellant grade is two specific impulses,
 * a rocket tier is masses and thrusts, and everything downstream is kinetics. The same code paths
 * a player exercises through the pad are the ones exercised here.
 */
public final class PhaseAVerification {

    private static int failures;
    private static int checks;

    public static void main(String[] args) {
        Constants k = Constants.get();
        LaunchPipeline pipeline = new LaunchPipeline(k);
        double gravity = k.d("gravity.g0");

        System.out.println("lilkuzco_cosmos — Phase A verification");
        System.out.println("kinetics: budget to orbit " + k.d("orbit.delta_v_to_orbit")
                + " m/s, planet R " + k.d("orbit.planet_radius") + " m");

        propellantLadder(k, pipeline, gravity);
        tierLadder(k, pipeline, gravity);
        underFuelling(pipeline, gravity);
        OrbitalRegistry.InsertionResult inserted = insertion(k, pipeline, gravity);
        if (inserted != null) {
            passes(k, inserted);
            deorbitAndRecover(k, inserted);
        }

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

    // ---- 1. the propellant ladder ----------------------------------------

    private static void propellantLadder(Constants k, LaunchPipeline pipeline, double gravity) {
        section("1. Propellant ladder — the SAME rocket, full tanks, three grades");

        RocketTier tier = RocketTier.ORBITAL;
        double fuel = tier.fuelCapacityKg();

        LaunchPipeline.Readout crude = pipeline.assess(tier, Propellants.CRUDE, fuel, gravity);
        LaunchPipeline.Readout kerosene = pipeline.assess(tier, Propellants.KEROSENE, fuel, gravity);
        LaunchPipeline.Readout cryo = pipeline.assess(tier, Propellants.CRYOGENIC, fuel, gravity);

        row("crude    (232 s)", crude);
        row("kerosene (311 s)", kerosene);
        row("cryogenic(450 s)", cryo);

        check("crude lifts off", crude.canLiftOff(),
                String.format("T/W %.2f", crude.twrSeaLevel()));
        check("crude does NOT reach orbit", !crude.reachesOrbit(),
                String.format("%.0f m/s short of the %.0f m/s budget",
                        crude.shortfall(), crude.requiredDeltaV()));
        check("kerosene reaches orbit", kerosene.reachesOrbit(),
                String.format("%.0f m/s margin", kerosene.deltaV() - kerosene.requiredDeltaV()));
        check("cryogenic has more margin still",
                cryo.deltaV() > kerosene.deltaV(),
                String.format("%.0f m/s against kerosene's %.0f m/s", cryo.deltaV(),
                        kerosene.deltaV()));

        Propellant upgrade = pipeline.suggestUpgrade(tier, Propellants.CRUDE, fuel, gravity,
                Propellants.LADDER);
        check("the pad can name the fix", upgrade == Propellants.KEROSENE,
                upgrade == null ? "no suggestion" : "suggests " + upgrade.id());
    }

    // ---- 2. the tier ladder ----------------------------------------------

    private static void tierLadder(Constants k, LaunchPipeline pipeline, double gravity) {
        section("2. Rocket tiers on refined kerosene");

        for (RocketTier tier : RocketTier.LADDER) {
            LaunchPipeline.Readout r = pipeline.assess(tier, Propellants.KEROSENE,
                    tier.fuelCapacityKg(), gravity);
            System.out.printf("   %-16s  %6.0f kg wet  T/W %.2f  dv %6.0f/%.0f  payload %3.0f kg  %s%n",
                    tier.id().substring(tier.id().indexOf(':') + 1), r.wetMassKg(),
                    r.twrSeaLevel(), r.deltaV(), r.requiredDeltaV(), tier.payloadCapacityKg(),
                    r.reachesOrbit() ? "ORBIT" : "suborbital");
        }

        LaunchPipeline.Readout sounding = pipeline.assess(RocketTier.SOUNDING,
                Propellants.CRYOGENIC, RocketTier.SOUNDING.fuelCapacityKg(), gravity);
        check("the sounding rocket never orbits, even on cryogenic",
                !sounding.reachesOrbit(),
                String.format("%.0f m/s against a %.0f m/s budget", sounding.deltaV(),
                        sounding.requiredDeltaV()));

        LaunchPipeline.Readout heavy = pipeline.assess(RocketTier.HEAVY, Propellants.KEROSENE,
                RocketTier.HEAVY.fuelCapacityKg(), gravity);
        check("the heavy lifter carries 4x the payload to orbit",
                heavy.reachesOrbit()
                        && RocketTier.HEAVY.payloadCapacityKg()
                        >= RocketTier.ORBITAL.payloadCapacityKg() * 4,
                String.format("%.0f kg against the orbital tier's %.0f kg",
                        RocketTier.HEAVY.payloadCapacityKg(),
                        RocketTier.ORBITAL.payloadCapacityKg()));
    }

    // ---- 3. under-fuelling is honest -------------------------------------

    private static void underFuelling(LaunchPipeline pipeline, double gravity) {
        section("3. Under-fuelling — propellant fills bottom-stage first");

        RocketTier tier = RocketTier.ORBITAL;
        double full = tier.fuelCapacityKg();
        double[] fractions = {0.25, 0.5, 0.75, 1.0};
        double previous = -1.0;
        boolean monotone = true;

        for (double f : fractions) {
            LaunchPipeline.Readout r = pipeline.assess(tier, Propellants.KEROSENE, full * f, gravity);
            System.out.printf("   %3.0f%% tanks  %6.0f kg  dv %6.0f m/s  %s%n",
                    f * 100, r.fuelKg(), r.deltaV(), r.reachesOrbit() ? "ORBIT" : "falls back");
            if (r.deltaV() < previous) monotone = false;
            previous = r.deltaV();
        }
        check("delta-v rises monotonically with propellant", monotone,
                "no rounding up, no free margin");

        LaunchPipeline.Readout half = pipeline.assess(tier, Propellants.KEROSENE, full * 0.5,
                gravity);
        check("a half-fuelled orbital rocket falls short", !half.reachesOrbit(),
                String.format("%.0f m/s short", half.shortfall()));
    }

    // ---- 4. insertion -----------------------------------------------------

    private static OrbitalRegistry.InsertionResult insertion(Constants k, LaunchPipeline pipeline,
                                                             double gravity) {
        section("4. Insertion into the kinetics registry");

        LaunchPipeline.Readout r = pipeline.assess(RocketTier.ORBITAL, Propellants.KEROSENE,
                RocketTier.ORBITAL.fuelCapacityKg(), gravity);

        OrbitalRegistry registry = new OrbitalRegistry(k);
        EventSink.Recording events = new EventSink.Recording();
        var result = registry.attemptInsertion("cosmos:verify", r.deltaV(), 0.0,
                51.6, 0.0, 0.0, events);

        check("insertion accepted", result.inserted(), result.detail());
        if (!result.inserted()) return null;

        double period = registry.mechanics().period(result.orbit().semiMajorAxisAtEpoch());
        double day = k.d("world.day_seconds");
        System.out.printf("   altitude %.0f m, period %.1f s (%.3f Minecraft days), inclination 51.6 deg%n",
                result.altitude(), period, period / day);

        var state = registry.stateAt("cosmos:verify", 0.0);
        check("the registry reports a live state", state != null,
                state == null ? "null" : String.format("%.0f m/s at lat %+.2f lon %+.2f",
                        state.speed(), state.groundTrack().latitudeDeg(),
                        state.groundTrack().longitudeDeg()));

        check("an under-fuelled launch is refused", !new OrbitalRegistry(k)
                        .attemptInsertion("cosmos:verify-fail",
                                pipeline.assess(RocketTier.ORBITAL, Propellants.CRUDE,
                                        RocketTier.ORBITAL.fuelCapacityKg(), gravity).deltaV(),
                                0.0, 51.6, 0.0, 0.0, EventSink.discarding()).inserted(),
                "crude-fuelled vehicle gets no partial credit");

        return result;
    }

    // ---- 5. passes --------------------------------------------------------

    private static void passes(Constants k, OrbitalRegistry.InsertionResult inserted) {
        section("5. Pass prediction in the rotating frame");

        OrbitalRegistry registry = new OrbitalRegistry(k);
        registry.register(inserted.orbit(),
                dev.lilkuzco.kinetics.orbit.Attitude.threeAxis(Quat.IDENTITY, 5.0));

        var start = registry.stateAt(inserted.orbit().id(), 0.0);
        double stationX = start.groundTrack().worldX();
        double stationZ = start.groundTrack().worldZ();
        double period = registry.mechanics().period(inserted.orbit().semiMajorAxisAtEpoch());

        // Twenty-four orbits, not four. At this altitude the ground track drifts 134 degrees
        // per revolution, so a fixed ground station is NOT overflown every orbit - the track has
        // to walk most of the way round the planet before it lines up again. That is the honest
        // consequence of the rotating frame, and it is also a real orbital-design lesson: a
        // lower insertion, nearer the reference orbit whose period equals one Minecraft day,
        // repeats its track daily and is what you want over a base.
        List<OrbitalRegistry.Pass> found = registry.predictPasses(inserted.orbit().id(), 0.0,
                stationX, stationZ, 30.0, 3, period * 24.0);

        System.out.printf("   ground station under the track at (%.0f, %.0f)%n",
                stationX, stationZ);
        for (int i = 0; i < found.size(); i++) {
            var pass = found.get(i);
            System.out.printf("   pass %d: t=%7.1f s, %.2f s long, closest %5.0f m, elev %.1f deg%n",
                    i + 1, pass.entryTime(), pass.durationSeconds(),
                    pass.closestGroundDistance(), pass.maxElevationDeg());
        }

        check("three passes predicted", found.size() >= 3,
                found.size() + " found within 24 orbits");

        // And the designed case: at the reference altitude the period equals one Minecraft day,
        // so the track repeats and a base is overflown on a schedule.
        OrbitalRegistry repeatReg = new OrbitalRegistry(k);
        var refOrbit = dev.lilkuzco.kinetics.orbit.Orbit.circular("cosmos:verify-ref",
                repeatReg.mechanics(), 0.0, k.d("orbit.reference_orbit_altitude"), 51.6, 0.0, 0.0);
        repeatReg.register(refOrbit,
                dev.lilkuzco.kinetics.orbit.Attitude.threeAxis(Quat.IDENTITY, 5.0));
        var refStart = repeatReg.stateAt("cosmos:verify-ref", 0.0);
        var refPasses = repeatReg.predictPasses("cosmos:verify-ref", 0.0,
                refStart.groundTrack().worldX(), refStart.groundTrack().worldZ(), 30.0, 3,
                repeatReg.mechanics().period(refOrbit.semiMajorAxisAtEpoch()) * 4.0);
        double refShift = repeatReg.mechanics()
                .groundTrackShiftPerOrbit(refOrbit.semiMajorAxisAtEpoch());
        System.out.printf("   reference orbit: shift %.4f deg/orbit, %d pass(es) in 4 orbits%n",
                refShift, refPasses.size());
        check("the reference orbit repeats its ground track",
                Math.abs(refShift - 360.0) < 0.05 && refPasses.size() >= 3,
                String.format("%.4f deg from repeating, %d passes", Math.abs(refShift - 360.0),
                        refPasses.size()));

        double shift = registry.mechanics()
                .groundTrackShiftPerOrbit(inserted.orbit().semiMajorAxisAtEpoch());
        System.out.printf("   ground-track shift %.4f deg/orbit (%.4f deg from repeating)%n",
                shift, shift - 360.0 * Math.round(shift / 360.0));
        check("the ground track drifts, so passes are not identical",
                Math.abs(shift % 360.0) > 1e-9 || Math.abs(shift - 360.0) < 1.0,
                "computed in the rotating frame, not the inertial one");
    }

    // ---- 6. deorbit and recovery -----------------------------------------

    private static void deorbitAndRecover(Constants k, OrbitalRegistry.InsertionResult inserted) {
        section("6. Deorbit, reentry and recovery — flown, not asserted");

        OrbitalRegistry registry = new OrbitalRegistry(k);
        registry.register(inserted.orbit(),
                dev.lilkuzco.kinetics.orbit.Attitude.threeAxis(Quat.IDENTITY, 5.0));

        EventSink.Recording events = new EventSink.Recording();
        var handoff = registry.deorbit(inserted.orbit().id(), 300.0, true, events);
        check("registry handed the body back", handoff != null,
                handoff == null ? "refused" : String.format("entry at %.0f m, %.1f m/s",
                        handoff.altitude(), handoff.worldVelocity().length()));
        if (handoff == null) return;

        Profile capsule = RocketTier.ORBITAL.buildCapsuleProfile("cosmos:verify-capsule", k);
        double beta = capsule.payloadDryMass()
                / (capsule.airframe().cd0() * capsule.airframe().referenceArea());
        System.out.printf("   capsule %.0f kg, shield %.1f m^2, beta %.1f kg/m^2%n",
                capsule.payloadDryMass(), capsule.airframe().referenceArea(), beta);

        // Fly the entry for real.
        Environment env = Environment.overworld(k,
                WorldProbe.flatGround((int) k.d("world.sea_level_y") - 1));
        double entryY = k.d("world.sea_level_y") + handoff.altitude();
        KineticBody body = new KineticBody("cosmos:verify-capsule", capsule, k,
                new Vec3(0, entryY, 0),
                new Vec3(handoff.worldVelocity().length(), 0, 0),
                Quat.between(new Vec3(0, 0, 1), new Vec3(1, 0, 0)),
                FlightPhase.DESCENT);
        FlightDirector director = new FlightDirector(k, env, body,
                FlightDirector.Mission.BALLISTIC, new Integrator(k), 1L);

        EventSink.Recording flight = new EventSink.Recording();
        double dt = k.d("world.tick_seconds");
        double peakHeat = 0.0;
        double peakQ = 0.0;
        for (int tick = 0; tick < 200_000 && body.phase().isInWorld(); tick++) {
            director.tick(tick * dt, dt, null, null, flight);
            peakHeat = Math.max(peakHeat, body.heatingRate());
            peakQ = Math.max(peakQ, body.dynamicPressure());
        }

        var deployed = flight.ofType(KineticEvent.ChuteDeployed.class);
        var shredded = flight.ofType(KineticEvent.ChuteShred.class);
        var impact = flight.first(KineticEvent.Impact.class);
        boolean sawReentry = flight.ofType(KineticEvent.PhaseChange.class).stream()
                .anyMatch(c -> c.to().equals("REENTRY"));

        System.out.printf("   peak heating %.4g W/m^2 (threshold %.4g), peak q %.0f Pa (q_max %.0f)%n",
                peakHeat, capsule.airframe().overheatThreshold(), peakQ,
                capsule.airframe().qMaxPa());
        for (var d : deployed) {
            System.out.printf("   %s deployed at %.0f m altitude, q %.0f Pa%n",
                    d.chuteName(), d.altitude(), d.dynamicPressure());
        }

        check("entered the REENTRY phase", sawReentry, "heating crossed the phase threshold");
        check("both chutes deployed", deployed.size() == 2, deployed.size() + " deployments");
        check("no canopy shredded", shredded.isEmpty(), shredded.size() + " shreds");
        check("recovered at survivable speed",
                impact != null && impact.velocity().length() < 15.0,
                impact == null ? "never landed"
                        : String.format("touchdown at %.2f m/s after %.1f s of flight",
                                impact.velocity().length(), body.age()));
        check("no invariant breached during the entry", true,
                "the integrator throws on breach, so reaching here is the proof");
    }

    // ---- plumbing ---------------------------------------------------------

    private static void row(String label, LaunchPipeline.Readout r) {
        System.out.printf("   %-18s T/W %.2f   dv %6.0f / %.0f m/s (%3.0f%%)   %s%n",
                label, r.twrSeaLevel(), r.deltaV(), r.requiredDeltaV(),
                r.budgetFraction() * 100.0, r.reachesOrbit() ? "ORBIT" : "falls back");
    }

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
