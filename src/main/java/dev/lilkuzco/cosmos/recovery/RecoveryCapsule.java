package dev.lilkuzco.cosmos.recovery;

import dev.lilkuzco.cosmos.Cosmos;
import dev.lilkuzco.cosmos.rocket.RocketTier;
import dev.lilkuzco.cosmos.satellite.SatelliteConstellation;
import dev.lilkuzco.kinetics.event.EventSink;
import dev.lilkuzco.kinetics.fabric.KineticsMod;
import dev.lilkuzco.kinetics.fabric.KineticsService;
import dev.lilkuzco.kinetics.math.Vec3;
import dev.lilkuzco.kinetics.orbit.OrbitalRegistry;
import dev.lilkuzco.kinetics.phase.FlightDirector;
import net.minecraft.server.level.ServerLevel;

/**
 * Taking a satellite out of orbit and bringing it home.
 *
 * <p>Nothing here decides how the descent goes. Kinetics hands back an entry position and an entry
 * velocity - the real orbital velocity, 1,819 m/s - and from that moment the capsule is an
 * ordinary falling body. Whether it survives, where it lands and how fast it arrives are all
 * consequences of its profile against the atmosphere.
 *
 * <p>Which is why the capsule's heat shield is enormous. Kinetics' atmosphere carries 1/155th of
 * Earth's column mass; a capsule with a realistic ballistic coefficient is still supersonic at
 * ground level. {@link RocketTier#buildCapsuleProfile} sizes the shield to hold beta near
 * 26 kg/m², and the result is a textbook entry: hard deceleration high up, drogue at 199 m, main
 * at 90 m, touchdown at 9.7 m/s.
 */
public final class RecoveryCapsule {

    private RecoveryCapsule() {}

    /** What a deorbit command did. */
    public record Result(boolean started, double entryX, double entryZ, double entrySpeed,
                         String reason) {

        public static Result failed(String reason) {
            return new Result(false, 0, 0, 0, reason);
        }
    }

    /**
     * Command a satellite down, aimed at a landing site.
     *
     * <p><b>A deorbit is a targeted manoeuvre, and this is where that becomes obvious.</b> The
     * sub-satellite point at the moment of the burn maps to a real orbital longitude, which in
     * world coordinates is somewhere in a million-block range - the first version dropped a
     * capsule 135 km from the player and called it recovery. That is physically where the
     * satellite was; it is not where anybody wanted the capsule.
     *
     * <p>Real deorbits solve for the landing site by choosing the burn. So does this one: the
     * entry is flown ahead of time to measure how far downrange the capsule travels, and the
     * actual entry point is then placed that far <em>short</em> of the target along the ground
     * track heading. The capsule flies exactly the trajectory kinetics says it flies, and it
     * arrives where it was aimed.
     *
     * <p>The rehearsal is a real kinetics flight, not an estimate. It costs a few thousand ticks
     * of one body - well under a millisecond - and it means the quoted landing site is the one
     * the physics will actually produce.
     *
     * @param targetX where the capsule should come down
     * @param targetZ where the capsule should come down
     */
    public static Result deorbitTo(ServerLevel level, KineticsService kinetics, String satelliteId,
                                   double targetX, double targetZ,
                                   dev.lilkuzco.cosmos.satellite.SatellitePayload payload) {
        OrbitalRegistry registry = kinetics.orbits();
        if (!registry.contains(satelliteId)) {
            return Result.failed("not in the registry");
        }

        EventSink.Recording events = new EventSink.Recording();
        OrbitalRegistry.DeorbitHandoff handoff =
                registry.deorbit(satelliteId, kinetics.worldTimeSeconds(), true, events);
        if (handoff == null) return Result.failed("registry refused the handoff");

        Vec3 velocity = handoff.worldVelocity();
        double speed = velocity.length();
        Vec3 heading = new Vec3(velocity.x(), 0.0, velocity.z());
        heading = heading.lengthSq() < 1e-9 ? new Vec3(1, 0, 0) : heading.normalized();

        var profile = RocketTier.ORBITAL.buildCapsuleProfile(
                "cosmos:capsule-" + satelliteId, kinetics.constants());

        double downrange = rehearse(kinetics, profile, handoff.altitude(), speed);
        Vec3 entry = new Vec3(targetX - heading.x() * downrange,
                kinetics.constants().d("world.sea_level_y") + handoff.altitude(),
                targetZ - heading.z() * downrange);

        CapsuleEntity capsule = CapsuleEntity.create(level, entry,
                heading.scale(speed), satelliteId, profile);
        if (capsule == null) return Result.failed("could not create the capsule entity");

        RecoveryTracker.track(profile.id(), satelliteId, payload, level);

        Cosmos.LOG.info("deorbit {}: aimed at ({}, {}), entering {} m short at ({}, {}), "
                        + "altitude {} m, {} m/s",
                satelliteId, String.format("%.0f", targetX), String.format("%.0f", targetZ),
                String.format("%.0f", downrange), String.format("%.0f", entry.x()),
                String.format("%.0f", entry.z()), String.format("%.0f", handoff.altitude()),
                String.format("%.1f", speed));

        return new Result(true, entry.x(), entry.z(), speed,
                String.format("aimed at (%.0f, %.0f), entering %.0f m short",
                        targetX, targetZ, downrange));
    }

    /**
     * Fly the entry once, headlessly, to measure how far downrange the capsule travels before it
     * touches down. Runs over an empty world so the rehearsal is not stopped by terrain that the
     * real flight will meet at a different place.
     */
    private static double rehearse(KineticsService kinetics,
                                   dev.lilkuzco.kinetics.profile.Profile profile,
                                   double entryAltitude, double speed) {
        var k = kinetics.constants();
        var env = dev.lilkuzco.kinetics.env.Environment.overworld(k,
                dev.lilkuzco.kinetics.env.WorldProbe.flatGround(
                        (int) k.d("world.sea_level_y") - 1));

        double entryY = k.d("world.sea_level_y") + entryAltitude;
        var body = new dev.lilkuzco.kinetics.body.KineticBody("cosmos:rehearsal", profile, k,
                new Vec3(0, entryY, 0), new Vec3(speed, 0, 0),
                dev.lilkuzco.kinetics.math.Quat.between(new Vec3(0, 0, 1), new Vec3(1, 0, 0)),
                dev.lilkuzco.kinetics.phase.FlightPhase.DESCENT);
        var director = new FlightDirector(k, env, body, FlightDirector.Mission.BALLISTIC,
                new dev.lilkuzco.kinetics.integrate.Integrator(k), 1L);

        double dt = k.d("world.tick_seconds");
        for (int tick = 0; tick < 200_000 && body.phase().isInWorld(); tick++) {
            director.tick(tick * dt, dt, null, null, EventSink.discarding());
        }
        return body.position().x();
    }

    /** Bring down every satellite whose orbit has decayed to the floor. */
    public static int sweepDecayed(ServerLevel level, KineticsService kinetics,
                                   SatelliteConstellation constellation) {
        EventSink.Recording events = new EventSink.Recording();
        var handoffs = kinetics.orbits().advanceDecay(kinetics.worldTimeSeconds(), events);
        int brought = 0;

        for (var handoff : handoffs) {
            var profile = RocketTier.ORBITAL.buildCapsuleProfile(
                    "cosmos:capsule-" + handoff.id(), kinetics.constants());
            if (CapsuleEntity.create(level, handoff.worldPosition(), handoff.worldVelocity(),
                    handoff.id(), profile) != null) {
                RecoveryTracker.track(profile.id(), handoff.id(), null, level);
                brought++;
            }
            constellation.remove(handoff.id());
            Cosmos.LOG.info("uncommanded deorbit: {} decayed to the floor and is coming down",
                    handoff.id());
        }
        return brought;
    }

    /** The mission kind a descending capsule flies. Ballistic - it has no engine left. */
    public static FlightDirector.Mission mission() {
        return FlightDirector.Mission.BALLISTIC;
    }

    public static KineticsService service() { return KineticsMod.service(); }
}
