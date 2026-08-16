package dev.lilkuzco.cosmos.rocket;

import dev.lilkuzco.cosmos.Cosmos;
import dev.lilkuzco.cosmos.satellite.SatelliteConstellation;
import dev.lilkuzco.cosmos.satellite.SatellitePayload;
import dev.lilkuzco.cosmos.satellite.SatelliteRecord;
import dev.lilkuzco.kinetics.fabric.KineticsMod;
import dev.lilkuzco.kinetics.fabric.KineticsService;
import dev.lilkuzco.kinetics.orbit.OrbitalRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Watches every launch in flight and decides its outcome.
 *
 * <p><b>This is deliberately not the rocket entity's job, and that was a real bug.</b> The first
 * version resolved insertion from {@code RocketEntity.tick()}, which looked reasonable until a
 * launch was flown on a server with nobody standing nearby: kinetics kept integrating the body
 * from the service tick, the entity was never ticked because its chunk was not an entity-ticking
 * chunk, and the flight completed with no insertion, no failure message and no satellite. The
 * physics had run and the result had been thrown away.
 *
 * <p>So outcome resolution lives where the physics lives - on the server tick, independent of
 * whether anyone is watching. A rocket launched from a base and then walked away from still
 * reaches orbit, which is the only behaviour that makes sense for a vehicle that takes a minute
 * to fly.
 *
 * <p>The entity is now purely a view: it shows where the body is and emits the plume. If it never
 * ticks, nothing is lost but the visuals.
 */
public final class LaunchTracker {

    /** What cosmos needs to remember about a launch until it resolves. */
    private record Pending(String bodyId, RocketTier tier, SatellitePayload payload,
                           BlockPos pad, double ignitedAt) {}

    // Insertion-ordered so simultaneous launches resolve in the order they lit.
    private static final Map<String, Pending> PENDING = new LinkedHashMap<>();

    private LaunchTracker() {}

    public static void register() {
        // Runs after kinetics' own END_SERVER_TICK handler: Fabric initialises kinetics first
        // because cosmos declares a hard dependency on it, so its callback is registered first
        // and fires first. By the time this runs, bodies have already been integrated this tick.
        ServerTickEvents.END_SERVER_TICK.register(LaunchTracker::tick);
    }

    /** Record a launch. Called at ignition. */
    public static void track(String bodyId, RocketTier tier, SatellitePayload payload,
                             BlockPos pad, double worldTime) {
        PENDING.put(bodyId, new Pending(bodyId, tier, payload, pad, worldTime));
    }

    public static int pendingCount() { return PENDING.size(); }

    private static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        KineticsService kinetics = KineticsMod.service();
        if (kinetics == null) { PENDING.clear(); return; }

        ServerLevel overworld = server.overworld();
        List<String> done = null;

        for (Pending pending : List.copyOf(PENDING.values())) {
            KineticsService.Handle handle = kinetics.handle(pending.bodyId());

            if (handle == null) {
                // Kinetics finished with the body before an insertion was ever offered - it came
                // back down. Report it rather than letting the launch evaporate.
                announce(server, Component.translatable("cosmos.launch.lost"));
                Cosmos.LOG.info("launch {} ended without insertion - the vehicle came down",
                        pending.bodyId());
                if (done == null) done = new ArrayList<>();
                done.add(pending.bodyId());
                continue;
            }

            if (!handle.director().awaitingInsertion()) continue;

            resolve(server, overworld, kinetics, pending, handle);
            if (done == null) done = new ArrayList<>();
            done.add(pending.bodyId());
        }
        if (done != null) done.forEach(PENDING::remove);
    }

    private static void resolve(MinecraftServer server, ServerLevel overworld,
                                KineticsService kinetics, Pending pending,
                                KineticsService.Handle handle) {
        double achieved = handle.body().achievedDeltaV();
        OrbitalRegistry registry = kinetics.orbits();

        if (pending.payload() == null) {
            handle.director().markInsertionFailed(e -> { });
            announce(server, Component.translatable("cosmos.launch.suborbital",
                    String.format("%.0f", achieved)));
            Cosmos.LOG.info("suborbital flight {}: {} m/s achieved, no payload aboard",
                    pending.bodyId(), String.format("%.1f", achieved));
            return;
        }

        SatelliteConstellation constellation = SatelliteConstellation.of(overworld);
        String satelliteId = constellation.nextId();

        // Where the pad is decides the orbital plane, so it matters where you build.
        double inclination = Math.min(89.0, Math.abs(pending.pad().getZ()) % 90.0 + 20.0);
        double raan = Math.floorMod(pending.pad().getX(), 360);

        var result = registry.attemptInsertion(satelliteId, achieved,
                kinetics.worldTimeSeconds(), inclination, raan, 0.0, e -> { });

        if (!result.inserted()) {
            handle.director().markInsertionFailed(e -> { });
            announce(server, Component.translatable("cosmos.launch.failed",
                    String.format("%.0f", achieved),
                    String.format("%.0f", result.requiredDeltaV()),
                    String.format("%.0f", result.requiredDeltaV() - achieved)));
            Cosmos.LOG.info("insertion refused for {}: {} m/s against a {} m/s budget",
                    pending.bodyId(), String.format("%.1f", achieved),
                    String.format("%.1f", result.requiredDeltaV()));
            return;
        }

        handle.director().markInserted(e -> { });
        double period = registry.mechanics().period(result.orbit().semiMajorAxisAtEpoch());
        constellation.add(new SatelliteRecord(satelliteId,
                        defaultName(satelliteId, pending.payload()), pending.payload(),
                        null, null, kinetics.worldTimeSeconds(), pending.tier().id()),
                result.orbit());

        announce(server, Component.translatable("cosmos.launch.inserted", satelliteId,
                String.format("%.0f", result.altitude()), String.format("%.0f", period)));
        Cosmos.LOG.info("insertion: {} at {} m, period {} s, {} m/s achieved against a {} m/s budget",
                satelliteId, String.format("%.0f", result.altitude()),
                String.format("%.1f", period), String.format("%.1f", achieved),
                String.format("%.1f", result.requiredDeltaV()));
    }

    private static String defaultName(String id, SatellitePayload payload) {
        String serial = id.substring(id.lastIndexOf('-') + 1);
        return (payload == SatellitePayload.RECON ? "Eye " : "Relay ") + serial;
    }

    private static void announce(MinecraftServer server, Component message) {
        for (var player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(message);
        }
    }
}
