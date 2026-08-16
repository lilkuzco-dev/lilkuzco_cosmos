package dev.lilkuzco.cosmos.propellant;

/**
 * One grade of rocket propellant.
 *
 * <p><b>Why this is a tag and not a fluid reference.</b> The campaign brief says to read
 * crude_empire's fuel API. There isn't one: crude_empire v0.1.x is worldgen only - crude oil,
 * reservoirs and seeps - and its own `CrudeEmpireTags.CRUDE_OIL` is commented "for future
 * refinery plumbing". Diesel does not exist yet, and refining is its Phase 2.
 *
 * <p>So cosmos does not reach into crude_empire. It declares what it needs as a fluid tag and a
 * specific impulse, and ships datapack tags that map today's crude oil into the crudest grade.
 * When crude_empire's refinery lands, wiring diesel in is a one-line addition to a tag file in
 * <em>this</em> repo - no code change in either mod, and no edit to crude_empire, which the
 * campaign fences forbid anyway.
 *
 * <p>The Isp figures are <b>real-world values</b>, exactly as kinetics profiles declare them.
 * Kinetics divides by its documented exhaust-velocity scale; cosmos never does its own physics.
 *
 * <p>Deliberately free of any Minecraft type. The grade is physics - an identifier and two
 * specific impulses - and which <em>fluids</em> count as that grade is a separate, Minecraft-side
 * question answered by {@link Propellants}. Keeping the split means the whole launch pipeline
 * (grade to profile to delta-v to orbit) can be verified headlessly against kinetics, with no
 * server and no client, exactly as kinetics verifies itself.
 *
 * @param id            namespaced grade id, e.g. {@code cosmos:crude}
 * @param ispSeaLevel   real specific impulse at sea level, s
 * @param ispVacuum     real specific impulse in vacuum, s
 * @param millibucketsPerUnit how much fluid one propellant unit represents
 * @param translationKey lang key for the pad UI
 */
public record Propellant(
        String id,
        double ispSeaLevel,
        double ispVacuum,
        int millibucketsPerUnit,
        String translationKey) {

    public Propellant {
        if (ispVacuum < ispSeaLevel) {
            // The same rejection kinetics' profile loader makes, applied a layer earlier so a
            // bad datapack fails at load rather than at ignition.
            throw new IllegalArgumentException("propellant '" + id + "' declares vacuum Isp ("
                    + ispVacuum + " s) below sea-level Isp (" + ispSeaLevel + " s), which is "
                    + "thermodynamically backwards - a nozzle performs better in vacuum than "
                    + "against ambient pressure, never worse.");
        }
        if (ispSeaLevel <= 0.0) {
            throw new IllegalArgumentException("propellant '" + id + "' has non-positive Isp");
        }
        if (millibucketsPerUnit <= 0) {
            throw new IllegalArgumentException("propellant '" + id + "' has non-positive unit size");
        }
    }

    /**
     * How much better this grade is than the reference. Purely for the pad UI - the actual
     * effect on performance goes through the rocket's kinetics profile, where it belongs.
     */
    public double relativeTo(Propellant reference) {
        return reference == null || reference.ispVacuum <= 0.0
                ? 1.0 : ispVacuum / reference.ispVacuum;
    }
}
