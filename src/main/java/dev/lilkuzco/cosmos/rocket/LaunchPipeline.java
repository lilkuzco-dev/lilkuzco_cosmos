package dev.lilkuzco.cosmos.rocket;

import dev.lilkuzco.cosmos.propellant.Propellant;
import dev.lilkuzco.kinetics.constants.Constants;
import dev.lilkuzco.kinetics.profile.EngineFrame;
import dev.lilkuzco.kinetics.profile.Profile;
import dev.lilkuzco.kinetics.propulsion.Propulsion;

/**
 * Everything the launch pad needs to know before the countdown, and nothing it decides itself.
 *
 * <p>The pad shows a player two numbers before ignition - thrust-to-weight and delta-v against
 * the budget - and both come straight from kinetics' own {@link Propulsion#assess}. That matters
 * more than it sounds: the figure on the pad readout and the figure that governs the flight are
 * the <em>same computation</em>, so the pad cannot promise something the physics will not honour.
 *
 * <p>A player who launches anyway gets the honest outcome. Under-fuelled vehicles lift off and
 * fall back; under-thrusted ones never leave the pad. Cosmos does not stop either.
 */
public final class LaunchPipeline {

    private final Constants constants;
    private final Propulsion propulsion;
    private final EngineFrame engineFrame;

    public LaunchPipeline(Constants constants) {
        this.constants = constants;
        this.propulsion = new Propulsion(constants);
        this.engineFrame = EngineFrame.of(constants);
    }

    /** What the pad readout shows, and what ignition will do. */
    public record Readout(
            boolean canLiftOff,
            boolean reachesOrbit,
            double twrSeaLevel,
            double deltaV,
            double requiredDeltaV,
            double wetMassKg,
            double fuelKg,
            double fuelFraction,
            String verdict) {

        /** Delta-v short of orbit, or 0 if it clears. */
        public double shortfall() { return Math.max(0.0, requiredDeltaV - deltaV); }

        /** Fraction of the orbital budget achieved - what the pad's bar fills to. */
        public double budgetFraction() {
            return requiredDeltaV <= 0.0 ? 0.0 : deltaV / requiredDeltaV;
        }
    }

    /**
     * Assess a fuelled vehicle.
     *
     * @param fuelKg propellant actually in the tanks
     */
    public Readout assess(RocketTier tier, Propellant propellant, double fuelKg, double gravity) {
        Profile profile = tier.buildProfile("cosmos:readout", propellant, fuelKg, constants);
        Propulsion.LaunchAssessment assessment = propulsion.assess(profile, gravity);

        double capacity = tier.fuelCapacityKg();
        return new Readout(
                assessment.canLiftOff(),
                assessment.reachesOrbit(),
                assessment.twrSeaLevel(),
                assessment.idealDeltaV(),
                assessment.requiredDeltaV(),
                profile.wetMass(),
                Math.min(fuelKg, capacity),
                capacity <= 0.0 ? 0.0 : Math.min(fuelKg, capacity) / capacity,
                assessment.verdict());
    }

    /**
     * The flight profile handed to kinetics at ignition. Identical inputs to {@link #assess}, so
     * what flies is what was quoted.
     */
    public Profile profileFor(String bodyId, RocketTier tier, Propellant propellant,
                              double fuelKg) {
        return tier.buildProfile(bodyId, propellant, fuelKg, constants);
    }

    /**
     * Whether a better propellant would fix a shortfall, and which one. Used by the pad to give a
     * player a reason rather than only a refusal - "883 m/s short; refined kerosene would clear
     * it" is a design brief, "cannot reach orbit" is a wall.
     */
    public Propellant suggestUpgrade(RocketTier tier, Propellant current, double fuelKg,
                                     double gravity, java.util.List<Propellant> ladder) {
        boolean past = false;
        for (Propellant candidate : ladder) {
            if (candidate == current) { past = true; continue; }
            if (!past) continue;
            if (assess(tier, candidate, fuelKg, gravity).reachesOrbit()) return candidate;
        }
        return null;
    }

    public Constants constants() { return constants; }

    public EngineFrame engineFrame() { return engineFrame; }
}
