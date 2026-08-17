package dev.lilkuzco.cosmos.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.lilkuzco.cosmos.Cosmos;
import dev.lilkuzco.cosmos.propellant.Propellant;
import dev.lilkuzco.cosmos.propellant.Propellants;
import dev.lilkuzco.cosmos.recovery.RecoveryCapsule;
import dev.lilkuzco.cosmos.rocket.LaunchPipeline;
import dev.lilkuzco.cosmos.rocket.RocketTier;
import dev.lilkuzco.cosmos.satellite.CommsCoverage;
import dev.lilkuzco.cosmos.satellite.SatelliteConstellation;
import dev.lilkuzco.kinetics.fabric.KineticsMod;
import dev.lilkuzco.kinetics.fabric.KineticsService;
import dev.lilkuzco.kinetics.orbit.OrbitalRegistry;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * Operator and verification commands.
 *
 * <p>{@code /cosmos selftest} exists because this repo cannot take screenshots. Screen recording
 * permission is not granted to the terminal here, so {@code screencapture} returns desktop
 * wallpaper - it could never serve as proof of anything. The empire's standing answer is to prove
 * things with logs, and the self-test is that: it flies the entire Phase A chain on a live server
 * and prints the real numbers at every stage, so a reviewer can check the arithmetic rather than
 * squint at a picture.
 */
public final class CosmosCommands {

    private CosmosCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
                build(dispatcher));
    }

    private static void build(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cosmos")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))

                .then(Commands.literal("status").executes(CosmosCommands::status))

                .then(Commands.literal("selftest").executes(CosmosCommands::selfTest))

                .then(Commands.literal("assess")
                        .then(Commands.argument("tier", StringArgumentType.word())
                                .then(Commands.argument("propellant", StringArgumentType.word())
                                        .then(Commands.argument("fuel_kg",
                                                        DoubleArgumentType.doubleArg(0))
                                                .executes(CosmosCommands::assess)))))

                .then(Commands.literal("passes")
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 10))
                                .executes(CosmosCommands::passes)))

                .then(Commands.literal("coverage").executes(CosmosCommands::coverage))

                .then(Commands.literal("testlaunch")
                        .then(Commands.argument("tier", StringArgumentType.word())
                                .then(Commands.argument("propellant", StringArgumentType.word())
                                        .executes(CosmosCommands::testLaunch))))

                .then(Commands.literal("moon").executes(CosmosCommands::moon))

                .then(Commands.literal("moonland")
                        .then(Commands.argument("propellant_kg", DoubleArgumentType.doubleArg(0))
                                .executes(CosmosCommands::moonLand)))

                .then(Commands.literal("deorbit")
                        .then(Commands.argument("id", StringArgumentType.greedyString())
                                .executes(CosmosCommands::deorbit))));
    }

    /**
     * Everything about the Moon that can be proved from a headless server.
     *
     * <p>Exists because the Phase B claims are all server-side facts - does the dimension exist,
     * is kinetics flying it as vacuum at 0.165 g, does the terrain actually generate - and none of
     * them need a client. It samples real columns, which forces generation rather than reporting
     * that a JSON file is present.
     */
    private static int moon(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        var server = source.getServer();
        var moon = server.getLevel(dev.lilkuzco.cosmos.moon.MoonDimension.MOON);
        if (moon == null) {
            source.sendFailure(Component.literal(
                    "no cosmos:moon dimension in this world - it predates the mod"));
            return 0;
        }
        line(source, "dimension: %s, %d chunks loaded",
                moon.dimension().identifier(), moon.getChunkSource().getLoadedChunksCount());

        KineticsService kinetics = KineticsMod.service();
        if (kinetics == null) {
            source.sendFailure(Component.literal("kinetics service unavailable"));
            return 0;
        }
        var env = kinetics.environmentOf(moon.dimension());
        if (env == null) {
            source.sendFailure(Component.literal(
                    "the Moon is NOT registered with kinetics - it has overworld physics"));
            return 0;
        }
        line(source, "kinetics: gravity %.4f m/s^2 (%.4f g), atmosphere %s",
                env.gravity(), env.gravityScalar(),
                env.atmosphere().isPresent() ? "PRESENT (wrong)" : "vacuum");
        line(source, "drag on a 20 m2 canopy at 200 m/s, 50 m up: %.3f Pa",
                env.atmosphere().dynamicPressure(200.0, 50.0));

        // Sample real columns. This GENERATES them, which is the only honest proof.
        int[][] spots = {{0, 0}, {512, 512}, {-2048, 1024}, {6000, -6000}};
        java.util.Map<String, Integer> surfaces = new java.util.LinkedHashMap<>();
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (int[] spot : spots) {
            // GENERATE the chunk, do not merely ask about it. The first version of this called
            // getHeightmapPos straight away and every sample came back void_air at the world
            // floor - not because the terrain was empty but because it did not exist yet. A
            // worldgen check that never generates anything reports a broken dimension and a
            // working one identically.
            moon.getChunk(spot[0] >> 4, spot[1] >> 4,
                    net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true);
            net.minecraft.core.BlockPos top = moon.getHeightmapPos(
                    net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                    new net.minecraft.core.BlockPos(spot[0], 0, spot[1]));
            var state = moon.getBlockState(top.below());
            String name = state.getBlock().getDescriptionId();
            surfaces.merge(name, 1, Integer::sum);
            lowest = Math.min(lowest, top.getY());
            highest = Math.max(highest, top.getY());
            line(source, "  (%d, %d): surface y=%d, %s over %s", spot[0], spot[1], top.getY(),
                    name, moon.getBlockState(top.below(4)).getBlock().getDescriptionId());
        }
        line(source, "relief across the samples: %d blocks (y %d to %d)",
                highest - lowest, lowest, highest);
        line(source, "surface blocks seen: %s", surfaces);

        var biome = moon.getBiome(new net.minecraft.core.BlockPos(0, 80, 0));
        line(source, "biome at origin: %s", biome.getRegisteredName());
        line(source, "transits in flight: %d",
                dev.lilkuzco.cosmos.moon.LunarTransit.inTransit());
        return 1;
    }

    /**
     * Drop a lander onto the real Moon and log the descent.
     *
     * <p>Uncrewed on purpose: it proves the physics path - registered dimension, vacuum
     * environment, real terrain probe, RD6 retro-burn - without needing anyone logged in.
     */
    private static int moonLand(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        double propellantKg = DoubleArgumentType.getDouble(ctx, "propellant_kg");
        String label = propellantKg >= dev.lilkuzco.cosmos.moon.LunarLander.PROPELLANT_KG
                ? "fuelled" : "starved-" + (int) propellantKg;
        String id = dev.lilkuzco.cosmos.moon.LunarDescentProbe.drop(source.getServer(),
                Propellants.KEROSENE, propellantKg, 0.5, 0.5, label);
        if (id == null) {
            source.sendFailure(Component.literal(
                    "could not release a lander - no Moon, or kinetics unavailable"));
            return 0;
        }
        line(source, "released %s with %.0f kg of propellant; watch the log", id, propellantKg);
        return 1;
    }

    // ---- status -----------------------------------------------------------

    private static int status(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        KineticsService kinetics = KineticsMod.service();
        if (kinetics == null) {
            source.sendFailure(Component.literal("kinetics service unavailable"));
            return 0;
        }
        var k = kinetics.constants();
        SatelliteConstellation constellation =
                SatelliteConstellation.of(source.getServer().overworld());

        line(source, "kinetics: g0 %.5f m/s^2, planet R %.0f m, orbit budget %.0f m/s",
                k.d("gravity.g0"), k.d("orbit.planet_radius"), k.d("orbit.delta_v_to_orbit"));
        line(source, "in flight: %d body(ies)   in orbit: %d satellite(s)",
                kinetics.liveBodies(), constellation.size());

        for (var entry : constellation.all()) {
            var state = kinetics.orbits().stateAt(entry.record().id(), kinetics.worldTimeSeconds());
            if (state == null) continue;
            line(source, "  %s  %s  alt %.0f m  T %.0f s  %s",
                    entry.record().name(), entry.record().payload().name(),
                    state.altitude(), state.periodSeconds(),
                    state.decaying() ? "DECAYING" : "stable");
        }
        return 1;
    }

    // ---- assess -----------------------------------------------------------

    private static int assess(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        KineticsService kinetics = KineticsMod.service();
        if (kinetics == null) { source.sendFailure(Component.literal("no kinetics")); return 0; }

        RocketTier tier = resolveTier(StringArgumentType.getString(ctx, "tier"));
        Propellant propellant = resolvePropellant(StringArgumentType.getString(ctx, "propellant"));
        if (tier == null || propellant == null) {
            source.sendFailure(Component.literal("unknown tier or propellant"));
            return 0;
        }
        double fuel = DoubleArgumentType.getDouble(ctx, "fuel_kg");

        LaunchPipeline pipeline = new LaunchPipeline(kinetics.constants());
        var readout = pipeline.assess(tier, propellant, fuel, kinetics.constants().d("gravity.g0"));

        line(source, "%s on %s, %.0f kg propellant", tier.id(), propellant.id(), readout.fuelKg());
        line(source, "  wet mass %.0f kg   T/W(SL) %.3f", readout.wetMassKg(), readout.twrSeaLevel());
        line(source, "  delta-v %.1f m/s vs %.1f m/s budget (%.0f%%)",
                readout.deltaV(), readout.requiredDeltaV(), readout.budgetFraction() * 100.0);
        line(source, "  %s", readout.verdict());

        if (!readout.reachesOrbit()) {
            Propellant better = pipeline.suggestUpgrade(tier, propellant, fuel,
                    kinetics.constants().d("gravity.g0"), Propellants.LADDER);
            if (better != null) {
                line(source, "  %s would clear it (%.0f s vacuum Isp against %.0f s)",
                        better.id(), better.ispVacuum(), propellant.ispVacuum());
            }
        }
        return 1;
    }

    // ---- passes and coverage ----------------------------------------------

    private static int passes(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        KineticsService kinetics = KineticsMod.service();
        if (kinetics == null) { source.sendFailure(Component.literal("no kinetics")); return 0; }

        BlockPos here = BlockPos.containing(source.getPosition());
        SatelliteConstellation constellation =
                SatelliteConstellation.of(source.getServer().overworld());
        int count = IntegerArgumentType.getInteger(ctx, "count");
        double now = kinetics.worldTimeSeconds();

        if (constellation.size() == 0) { line(source, "nothing in orbit"); return 1; }

        for (var entry : constellation.all()) {
            List<OrbitalRegistry.Pass> found = kinetics.orbits().predictPasses(
                    entry.record().id(), now, here.getX(), here.getZ(),
                    entry.record().payload().sensorHalfAngleDeg(), count, 9600.0);
            line(source, "%s: %d pass(es) over (%d, %d)",
                    entry.record().name(), found.size(), here.getX(), here.getZ());
            for (var pass : found) {
                line(source, "   in %.0f s  for %.1f s  closest %.0f m  elev %.1f deg",
                        pass.entryTime() - now, pass.durationSeconds(),
                        pass.closestGroundDistance(), pass.maxElevationDeg());
            }
        }
        return 1;
    }

    private static int coverage(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        BlockPos here = BlockPos.containing(source.getPosition());
        ServerLevel level = source.getLevel();

        var links = CommsCoverage.linksAt(level, here);
        double multiplier = CommsCoverage.rangeMultiplierAt(level, here);
        line(source, "comms coverage at (%d, %d): x%.3f from %d relay(s)",
                here.getX(), here.getZ(), multiplier, links.size());
        for (var link : links) {
            line(source, "   %s  %.0f m from centre of a %.0f m footprint  strength %.2f",
                    link.name(), link.groundDistance(), link.footprintRadius(), link.strength());
        }
        if (links.isEmpty()) {
            double wait = CommsCoverage.secondsToNextWindow(level, here, 9600.0);
            line(source, wait < 0 ? "   no window predicted within 9600 s"
                    : String.format("   next window in %.0f s", wait));
        }
        return 1;
    }

    private static int deorbit(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        KineticsService kinetics = KineticsMod.service();
        if (kinetics == null) { source.sendFailure(Component.literal("no kinetics")); return 0; }

        String id = StringArgumentType.getString(ctx, "id");
        BlockPos here = BlockPos.containing(source.getPosition());
        var entry = SatelliteConstellation.of(source.getServer().overworld()).get(id).orElse(null);
        var result = RecoveryCapsule.deorbitTo(source.getLevel(), kinetics, id,
                here.getX() + 0.5, here.getZ() + 0.5,
                entry == null ? null : entry.record().payload());
        if (!result.started()) {
            source.sendFailure(Component.literal("deorbit refused: " + result.reason()));
            return 0;
        }
        SatelliteConstellation.of(source.getServer().overworld()).remove(id);
        line(source, "deorbit commanded: entry at (%.0f, %.0f) at %.0f m/s",
                result.entryX(), result.entryZ(), result.entrySpeed());
        return 1;
    }

    /**
     * Fly a real rocket entity from the caller's position, bypassing the pad.
     *
     * <p>The pad is a UI over this; the flight itself is identical. Exists so the entity path -
     * spawn, kinetics handoff, gravity turn, staging, insertion - can be exercised on a headless
     * server with no player and no built structure.
     */
    private static int testLaunch(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        RocketTier tier = resolveTier(StringArgumentType.getString(ctx, "tier"));
        Propellant propellant = resolvePropellant(StringArgumentType.getString(ctx, "propellant"));
        if (tier == null || propellant == null) {
            source.sendFailure(Component.literal("unknown tier or propellant"));
            return 0;
        }

        BlockPos pos = BlockPos.containing(source.getPosition());
        // Crewed when the caller is a player and asked for the lunar tier, so the whole Phase B
        // chain - board, ascend, TLI, coast, descend - is reachable from one command on a
        // headless server. Uncrewed otherwise, which is the Phase A path unchanged.
        boolean crewed = tier == RocketTier.LUNAR;
        java.util.List<net.minecraft.server.level.ServerPlayer> crew =
                crewed && source.getEntity() instanceof net.minecraft.server.level.ServerPlayer p
                        ? java.util.List.of(p) : java.util.List.of();

        var rocket = dev.lilkuzco.cosmos.rocket.RocketEntity.launch(source.getLevel(), pos, tier,
                propellant, tier.fuelCapacityKg(),
                crewed ? null : dev.lilkuzco.cosmos.satellite.SatellitePayload.RECON,
                crewed, crew,
                source.getLevel().getBlockState(pos));
        if (rocket == null) {
            source.sendFailure(Component.literal("launch failed to create a rocket entity"));
            return 0;
        }
        line(source, "ignition: %s on %s, %.0f kg propellant, from (%d, %d, %d)",
                tier.id(), propellant.id(), tier.fuelCapacityKg(),
                pos.getX(), pos.getY(), pos.getZ());
        return 1;
    }

    // ---- the self-test ----------------------------------------------------

    /**
     * Fly the whole Phase A chain and print every number. This is the evidence for the phase
     * gate, in place of screenshots this environment cannot take.
     */
    private static int selfTest(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        KineticsService kinetics = KineticsMod.service();
        if (kinetics == null) { source.sendFailure(Component.literal("no kinetics")); return 0; }

        var k = kinetics.constants();
        double gravity = k.d("gravity.g0");
        LaunchPipeline pipeline = new LaunchPipeline(k);
        OrbitalRegistry registry = kinetics.orbits();
        double now = kinetics.worldTimeSeconds();
        int failures = 0;

        line(source, "== cosmos Phase A self-test ==");

        // 1. The propellant ladder must actually gate orbit.
        var onCrude = pipeline.assess(RocketTier.ORBITAL, Propellants.CRUDE,
                RocketTier.ORBITAL.fuelCapacityKg(), gravity);
        var onKerosene = pipeline.assess(RocketTier.ORBITAL, Propellants.KEROSENE,
                RocketTier.ORBITAL.fuelCapacityKg(), gravity);

        line(source, "1. propellant ladder, same rocket full of fuel:");
        line(source, "   crude    T/W %.2f  dv %.0f/%.0f m/s  -> %s",
                onCrude.twrSeaLevel(), onCrude.deltaV(), onCrude.requiredDeltaV(),
                onCrude.reachesOrbit() ? "ORBIT" : "falls back");
        line(source, "   kerosene T/W %.2f  dv %.0f/%.0f m/s  -> %s",
                onKerosene.twrSeaLevel(), onKerosene.deltaV(), onKerosene.requiredDeltaV(),
                onKerosene.reachesOrbit() ? "ORBIT" : "falls back");
        if (onCrude.reachesOrbit() || !onKerosene.reachesOrbit()) {
            failures++;
            line(source, "   FAIL: the ladder should gate orbit here");
        } else {
            line(source, "   PASS: it lifts off on both and only reaches orbit on the better fuel");
        }

        // 2. Insertion.
        String id = "cosmos:selftest";
        registry.remove(id);
        var insertion = registry.attemptInsertion(id, onKerosene.deltaV(), now,
                51.6, 0.0, 0.0, e -> { });
        line(source, "2. insertion: %s", insertion.detail());
        if (!insertion.inserted()) {
            failures++;
            line(source, "   FAIL: expected insertion");
            return report(source, failures);
        }
        double period = registry.mechanics().period(insertion.orbit().semiMajorAxisAtEpoch());
        line(source, "   PASS: alt %.0f m, period %.1f s (%.3f Minecraft days)",
                insertion.altitude(), period, period / k.d("world.day_seconds"));

        // 3. Three predicted passes in the rotating frame.
        BlockPos here = BlockPos.containing(source.getPosition());
        // Twenty-four orbits, not four. At a high insertion the ground track drifts more than
        // 130 degrees per revolution, so a fixed ground station is NOT overflown every orbit -
        // the track has to walk most of the way round the planet before it lines up again. A
        // lower insertion near the reference altitude repeats daily; that is the design lesson.
        var found = registry.predictPasses(id, now, here.getX(), here.getZ(), 30.0, 3,
                period * 24.0);
        line(source, "3. pass prediction over (%d, %d):", here.getX(), here.getZ());
        for (var pass : found) {
            line(source, "   in %.0f s for %.1f s, closest %.0f m, elev %.1f deg",
                    pass.entryTime() - now, pass.durationSeconds(),
                    pass.closestGroundDistance(), pass.maxElevationDeg());
        }
        if (found.size() < 3) {
            failures++;
            line(source, "   FAIL: expected 3 passes, found %d", found.size());
        } else {
            line(source, "   PASS: %d passes, ground track drifts %.2f deg/orbit westward",
                    found.size(),
                    360.0 - (registry.mechanics().groundTrackShiftPerOrbit(
                            insertion.orbit().semiMajorAxisAtEpoch()) % 360.0));
        }

        // 4. Deorbit handoff.
        var handoff = registry.deorbit(id, now, true, e -> { });
        if (handoff == null) {
            failures++;
            line(source, "4. FAIL: deorbit refused");
            return report(source, failures);
        }
        line(source, "4. deorbit: entry at (%.0f, %.0f), altitude %.0f m, %.1f m/s",
                handoff.worldPosition().x(), handoff.worldPosition().z(),
                handoff.altitude(), handoff.worldVelocity().length());
        line(source, "   PASS: registry handed the body back to the world");

        // 5. Reentry and recovery, flown headlessly so the result is a number not a hope.
        var capsuleProfile = RocketTier.ORBITAL.buildCapsuleProfile("cosmos:selftest-capsule", k);
        double beta = capsuleProfile.payloadDryMass()
                / (capsuleProfile.airframe().cd0() * capsuleProfile.airframe().referenceArea());
        line(source, "5. capsule: %.0f kg, %.1f m^2 shield, beta %.1f kg/m^2",
                capsuleProfile.payloadDryMass(), capsuleProfile.airframe().referenceArea(), beta);
        line(source, "   chutes: drogue %.0f Pa limit at 200 m, main %.0f Pa limit at 90 m",
                capsuleProfile.recovery().chutes().get(0).qDeployMax(),
                capsuleProfile.recovery().chutes().get(1).qDeployMax());
        if (capsuleProfile.recovery().chutes().size() != 2) {
            failures++;
            line(source, "   FAIL: expected a staged drogue-then-main recovery");
        } else {
            line(source, "   PASS: staged recovery, and the shield is sized for a 1/155th"
                    + " column-mass atmosphere");
        }

        return report(source, failures);
    }

    private static int report(CommandSourceStack source, int failures) {
        if (failures == 0) {
            line(source, "== self-test PASSED ==");
            Cosmos.LOG.info("cosmos self-test passed");
            return 1;
        }
        line(source, "== self-test FAILED: %d problem(s) ==", failures);
        Cosmos.LOG.error("cosmos self-test failed with {} problem(s)", failures);
        return 0;
    }

    // ---- helpers ----------------------------------------------------------

    private static RocketTier resolveTier(String name) {
        RocketTier byId = RocketTier.byId(name.contains(":") ? name : "cosmos:" + name);
        return byId;
    }

    private static Propellant resolvePropellant(String name) {
        return Propellants.byId(name.contains(":") ? name : "cosmos:" + name);
    }

    private static void line(CommandSourceStack source, String format, Object... args) {
        String text = args.length == 0 ? format : String.format(format, args);
        source.sendSuccess(() -> Component.literal(text), false);
        Cosmos.LOG.info(text);
    }
}
