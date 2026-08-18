package dev.lilkuzco.cosmos.satellite;

import dev.lilkuzco.cosmos.Cosmos;
import dev.lilkuzco.cosmos.CosmosBlockEntities;
import dev.lilkuzco.cosmos.recovery.RecoveryCapsule;
import dev.lilkuzco.kinetics.fabric.KineticsMod;
import dev.lilkuzco.kinetics.fabric.KineticsService;
import dev.lilkuzco.kinetics.orbit.OrbitalRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The satellite console. Holds no state worth saving - the constellation is the state.
 *
 * <p>Every number it shows is recomputed from kinetics at the moment of asking, which is possible
 * only because orbits propagate from epoch. A console that cached would be showing a satellite
 * where it was when someone last looked.
 */
public class SatelliteConsoleBlockEntity extends BlockEntity {

    /** How far ahead to look for the next pass. Four reference orbits. */
    private static final double PASS_HORIZON_SECONDS = 4800.0;

    public SatelliteConsoleBlockEntity(BlockPos pos, BlockState state) {
        super(CosmosBlockEntities.SATELLITE_CONSOLE, pos, state);
    }

    /** Build and send the current constellation snapshot. */
    public static void sendSnapshot(ServerPlayer player, BlockPos console, boolean openScreen) {
        KineticsService kinetics = KineticsMod.service();
        if (kinetics == null) {
            player.sendSystemMessage(
                    Component.translatable("cosmos.console.no_kinetics"));
            return;
        }

        ServerLevel overworld = player.level().getServer().overworld();
        SatelliteConstellation constellation = SatelliteConstellation.of(overworld);
        OrbitalRegistry registry = kinetics.orbits();
        double now = kinetics.worldTimeSeconds();

        // Anything kinetics has dropped has come down; take it off the roster first so the
        // planetarium never lists a satellite that no longer exists.
        for (SatelliteRecord gone : constellation.pruneDeorbited(registry)) {
            player.sendSystemMessage(
                    Component.translatable("cosmos.console.deorbited", gone.name()));
        }

        List<CosmosNet.SatelliteView> views = new ArrayList<>();
        for (SatelliteConstellation.Entry entry : constellation.all()) {
            OrbitalRegistry.OrbitalState state = registry.stateAt(entry.record().id(), now);
            if (state == null) continue;

            double nextPass = -1.0;
            var passes = registry.predictPasses(entry.record().id(), now,
                    console.getX(), console.getZ(),
                    entry.record().payload().sensorHalfAngleDeg(), 1, PASS_HORIZON_SECONDS);
            if (!passes.isEmpty()) nextPass = Math.max(0.0, passes.get(0).entryTime() - now);

            views.add(new CosmosNet.SatelliteView(
                    entry.record().id(), entry.record().name(),
                    entry.record().payload().name(),
                    state.altitude(), state.periodSeconds(), state.speed(),
                    state.groundTrack().latitudeDeg(), state.groundTrack().longitudeDeg(),
                    state.groundTrack().worldX(), state.groundTrack().worldZ(),
                    argumentOfLatitude(entry, now, registry),
                    entry.orbit().inclinationDeg(),
                    state.decaying(), nextPass));
        }

        ServerPlayNetworking.send(player, new CosmosNet.PlanetariumS2C(console, views, now,
                registry.mechanics().planetRadius(), openScreen));
    }

    /** Where the satellite is around its orbit right now, for drawing the marker. */
    private static double argumentOfLatitude(SatelliteConstellation.Entry entry, double now,
                                             OrbitalRegistry registry) {
        double a = registry.semiMajorAxisAt(entry.orbit().toOrbit(entry.record().id()), now);
        double n = registry.mechanics().meanMotion(a);
        double u = entry.orbit().argumentOfLatitudeDeg()
                + Math.toDegrees(n * (now - entry.orbit().epochSeconds()));
        return ((u % 360.0) + 360.0) % 360.0;
    }

    /** Handle a console button. */
    public static void handleAction(ServerPlayer player, CosmosNet.ConsoleActionC2S action) {
        if (player == null) return;
        // Range check: a console packet is a claim, and a player who is not standing at the
        // console has no business commanding a deorbit through it.
        if (player.distanceToSqr(action.console().getX() + 0.5, action.console().getY() + 0.5,
                action.console().getZ() + 0.5) > 64.0) {
            return;
        }

        ServerLevel overworld = player.level().getServer().overworld();
        SatelliteConstellation constellation = SatelliteConstellation.of(overworld);
        KineticsService kinetics = KineticsMod.service();
        if (kinetics == null) return;

        switch (action.action()) {
            case CosmosNet.ConsoleActionC2S.ACTION_REFRESH ->
                    sendSnapshot(player, action.console(), false);

            case CosmosNet.ConsoleActionC2S.ACTION_RENAME -> {
                String name = action.argument().trim();
                if (name.isEmpty() || name.length() > 32) return;
                if (constellation.rename(action.satelliteId(), name)) {
                    sendSnapshot(player, action.console(), false);
                }
            }

            case CosmosNet.ConsoleActionC2S.ACTION_IMAGE ->
                    image(player, constellation, kinetics, action.satelliteId());

            case CosmosNet.ConsoleActionC2S.ACTION_DEORBIT ->
                    deorbit(player, constellation, kinetics, action.satelliteId(),
                            action.console());

            default -> Cosmos.LOG.warn("unknown console action '{}'", action.action());
        }
    }

    /** Task a recon pass. Only works while the satellite is actually overhead. */
    private static void image(ServerPlayer player, SatelliteConstellation constellation,
                              KineticsService kinetics, String satelliteId) {
        var entry = constellation.get(satelliteId).orElse(null);
        if (entry == null) return;

        if (entry.record().payload() != SatellitePayload.RECON) {
            player.sendSystemMessage(Component.translatable("cosmos.console.not_recon"));
            return;
        }

        var state = kinetics.orbits().stateAt(satelliteId, kinetics.worldTimeSeconds());
        if (state == null) return;

        // Image the OVERWORLD, not the level the operator happens to be standing in. These
        // orbits are around the overworld and the ground track is in overworld coordinates;
        // reading them against another dimension's terrain samples the wrong planet at the
        // right numbers. It used to use player.level(), which was correct only because nobody
        // had yet built a console on the Moon - and would have failed silently when they did.
        ServerLevel imaged = player.level().getServer().overworld();

        ReconImager.Report report = ReconImager.image(imaged, satelliteId,
                state.groundTrack(), entry.record().payload().sensorHalfAngleDeg());

        // Straight to the console. The report is data on the wire and the screen lays it out;
        // it used to be chat, which meant the answer arrived behind the screen that asked for it.
        ServerPlayNetworking.send(player,
                new CosmosNet.ReconS2C(entry.record().name(), report));
    }

    /**
     * Command a deorbit. Kinetics hands the satellite back as a descending body, and from there
     * the capsule is on its own - reentry, chutes and a landing, all physics.
     */
    private static void deorbit(ServerPlayer player, SatelliteConstellation constellation,
                                KineticsService kinetics, String satelliteId, BlockPos console) {
        var entry = constellation.get(satelliteId).orElse(null);
        if (entry == null) return;

        // Aim it at the console. A capsule that lands where the operator is standing is the
        // whole point of commanding a deorbit from there.
        RecoveryCapsule.Result result = RecoveryCapsule.deorbitTo(
                (ServerLevel) player.level(), kinetics, satelliteId,
                console.getX() + 0.5, console.getZ() + 0.5, entry.record().payload());
        if (!result.started()) {
            player.sendSystemMessage(Component.translatable("cosmos.console.deorbit_failed"));
            return;
        }

        constellation.remove(satelliteId);
        player.sendSystemMessage(Component.translatable("cosmos.console.deorbit_started",
                entry.record().name(),
                String.format("%.0f", result.entryX()), String.format("%.0f", result.entryZ()),
                String.format("%.0f", result.entrySpeed())));
        sendSnapshot(player, console, false);
    }
}
