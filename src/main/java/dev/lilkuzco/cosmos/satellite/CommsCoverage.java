package dev.lilkuzco.cosmos.satellite;

import dev.lilkuzco.kinetics.fabric.KineticsMod;
import dev.lilkuzco.kinetics.fabric.KineticsService;
import dev.lilkuzco.kinetics.orbit.OrbitalRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * Comms coverage: the public contract other empire mods read.
 *
 * <p><b>Cosmos does not import warfront.</b> The campaign brief says comms satellites extend
 * warfront's radar range and names {@code warfront:threats} as the contract to consume — but
 * warfront v0.2.1 has no radar and no such contract; it is squads, stations, orders and dialogue.
 * There is nothing to consume yet.
 *
 * <p>So the dependency runs the only way it safely can: cosmos publishes coverage as a plain
 * static query, and whoever grows a sensor asks. Warfront needs no cosmos import to benefit
 * (a soft-dependency lookup is enough), cosmos needs no warfront import at all, and neither repo
 * has to be touched to wire them together — which the campaign fences require anyway.
 *
 * <pre>{@code
 * double multiplier = CommsCoverage.rangeMultiplierAt(level, radarPos);
 * double effectiveRange = baseRange * multiplier;
 * }</pre>
 *
 * <p>Coverage is a <em>window</em>, not a state. A satellite is overhead for as long as its
 * footprint covers the point, and at the reference orbit that is a few seconds per pass. A
 * consumer that samples once and caches the answer has misunderstood the mechanic.
 */
public final class CommsCoverage {

    /** Best multiplier a single relay can give. Fourth-root, per the radar range equation. */
    private static final double MAX_RANGE_MULTIPLIER = 2.0;

    private CommsCoverage() {}

    /** One relay currently covering a point. */
    public record Link(String satelliteId, String name, double groundDistance,
                       double footprintRadius, double elevationDeg) {

        /** 1 at the centre of the footprint, 0 at its edge. */
        public double strength() {
            if (footprintRadius <= 0.0) return 0.0;
            return Math.max(0.0, 1.0 - groundDistance / footprintRadius);
        }
    }

    /**
     * Range multiplier for a ground sensor at this position, 1.0 when nothing is overhead.
     *
     * <p>Multiple relays do not stack linearly. Detection range scales as the fourth root of
     * received power (kinetics RF1), so doubling the relays buys about 19%, not 100% — the same
     * brutal exponent that makes stealth expensive works against stacking here.
     */
    public static double rangeMultiplierAt(ServerLevel level, BlockPos pos) {
        List<Link> links = linksAt(level, pos);
        if (links.isEmpty()) return 1.0;

        double power = 0.0;
        for (Link link : links) power += link.strength();
        if (power <= 0.0) return 1.0;

        double gain = 1.0 + (MAX_RANGE_MULTIPLIER - 1.0) * Math.pow(power, 0.25);
        return Math.min(MAX_RANGE_MULTIPLIER, gain);
    }

    /** Whether any relay is currently overhead. */
    public static boolean hasCoverage(ServerLevel level, BlockPos pos) {
        return !linksAt(level, pos).isEmpty();
    }

    /** Every comms relay whose footprint currently covers this position. */
    public static List<Link> linksAt(ServerLevel level, BlockPos pos) {
        List<Link> links = new ArrayList<>();
        KineticsService kinetics = KineticsMod.service();
        if (kinetics == null) return links;

        SatelliteConstellation constellation = SatelliteConstellation.of(level.getServer().overworld());
        OrbitalRegistry registry = kinetics.orbits();
        double now = kinetics.worldTimeSeconds();
        double planetRadius = registry.mechanics().planetRadius();

        for (SatelliteConstellation.Entry entry : constellation.all()) {
            if (entry.record().payload() != SatellitePayload.COMMS) continue;

            OrbitalRegistry.OrbitalState state = registry.stateAt(entry.record().id(), now);
            if (state == null) continue;

            double halfAngle = entry.record().payload().sensorHalfAngleDeg();
            double radius = state.groundTrack().footprintRadius(halfAngle);
            double distance = state.groundTrack()
                    .groundDistanceTo(pos.getX(), pos.getZ(), planetRadius);
            if (distance > radius) continue;

            double elevation = Math.toDegrees(Math.atan2(state.altitude(), Math.max(distance, 1e-6)));
            links.add(new Link(entry.record().id(), entry.record().name(), distance, radius,
                    elevation));
        }
        return links;
    }

    /**
     * Seconds until the next comms window opens at this position, or -1 if none is predicted.
     * A ground station can schedule around this rather than polling.
     */
    public static double secondsToNextWindow(ServerLevel level, BlockPos pos, double horizon) {
        KineticsService kinetics = KineticsMod.service();
        if (kinetics == null) return -1.0;

        SatelliteConstellation constellation = SatelliteConstellation.of(level.getServer().overworld());
        OrbitalRegistry registry = kinetics.orbits();
        double now = kinetics.worldTimeSeconds();
        double best = -1.0;

        for (SatelliteConstellation.Entry entry : constellation.all()) {
            if (entry.record().payload() != SatellitePayload.COMMS) continue;
            var passes = registry.predictPasses(entry.record().id(), now,
                    pos.getX(), pos.getZ(), entry.record().payload().sensorHalfAngleDeg(),
                    1, horizon);
            if (passes.isEmpty()) continue;
            double wait = passes.get(0).entryTime() - now;
            if (wait < 0.0) wait = 0.0;
            if (best < 0.0 || wait < best) best = wait;
        }
        return best;
    }
}
