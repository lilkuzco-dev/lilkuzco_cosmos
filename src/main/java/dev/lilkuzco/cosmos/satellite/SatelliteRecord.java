package dev.lilkuzco.cosmos.satellite;

import java.util.UUID;

/**
 * What cosmos knows about a satellite. Where it <em>is</em> is kinetics' business.
 *
 * <p>The split matters. Kinetics' orbital registry holds the elements and propagates them from
 * epoch; this record holds ownership, naming and payload. Duplicating altitude or period here
 * would create a second source of truth that drifts the moment anything manoeuvres.
 *
 * @param id           the same id kinetics knows the orbit by
 * @param name         player-chosen; shown on the planetarium
 * @param payload      what it carries
 * @param ownerId      who launched it, or null for a server-owned satellite
 * @param ownerName    cached for display when the owner is offline
 * @param launchedAt   world time of insertion, seconds
 * @param launchTier   the rocket that put it up, for the flight log
 */
public record SatelliteRecord(
        String id,
        String name,
        SatellitePayload payload,
        UUID ownerId,
        String ownerName,
        double launchedAt,
        String launchTier) {

    public SatelliteRecord withName(String newName) {
        return new SatelliteRecord(id, newName, payload, ownerId, ownerName, launchedAt, launchTier);
    }

    /** How long it has been up, in Minecraft days (the reference orbit's period is exactly one). */
    public double ageInDays(double worldTimeSeconds, double dayLengthSeconds) {
        if (dayLengthSeconds <= 0.0) return 0.0;
        return Math.max(0.0, (worldTimeSeconds - launchedAt) / dayLengthSeconds);
    }
}
