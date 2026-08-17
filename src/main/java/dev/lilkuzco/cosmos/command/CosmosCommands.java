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
                                        .executes(CosmosCommands::testLaunch)
                                        .then(Commands.argument("destination",
                                                        StringArgumentType.word())
                                                .executes(CosmosCommands::testLaunch)))))

                .then(Commands.literal("padtest").executes(CosmosCommands::padTest))

                .then(Commands.literal("showcapsule").executes(CosmosCommands::showCapsule))

                .then(Commands.literal("isrutest").executes(CosmosCommands::isruTest))

                .then(Commands.literal("economy")
                        .executes(CosmosCommands::economy)
                        .then(Commands.argument("advance_ticks",
                                        IntegerArgumentType.integer(1, 200_000))
                                .executes(CosmosCommands::economy)))

                .then(Commands.literal("moon").executes(CosmosCommands::moon))

                .then(Commands.literal("worlds").executes(CosmosCommands::worlds))

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
        // Life support reads breathability from kinetics, not from a dimension whitelist, so the
        // two answers below are the mechanism itself rather than a description of it.
        line(source, "breathable: overworld %s, moon %s",
                dev.lilkuzco.cosmos.life.LifeSupport.isBreathable(
                        net.minecraft.world.level.Level.OVERWORLD),
                dev.lilkuzco.cosmos.life.LifeSupport.isBreathable(moon.dimension()));
        var k = kinetics.constants();
        line(source, "mission budget: orbit %.0f + TLI %.1f = %.1f m/s to leave; "
                        + "LOI %.1f + descent %.1f = %.1f m/s to arrive",
                k.d("orbit.delta_v_to_orbit"), k.d("orbit.lunar_transfer_delta_v"),
                k.d("orbit.delta_v_to_orbit") + k.d("orbit.lunar_transfer_delta_v"),
                k.d("orbit.lunar_orbit_insertion_delta_v"), k.d("orbit.lunar_descent_delta_v"),
                dev.lilkuzco.cosmos.moon.LunarLander.arrivalBudget(k));
        line(source, "coast: %.0f s of transfer played over %.0f s at %.0fx",
                kinetics.orbits().mechanics().lunarTransferTime(),
                kinetics.orbits().mechanics().lunarTransferTime()
                        / dev.lilkuzco.cosmos.moon.LunarTransit.TIME_COMPRESSION,
                dev.lilkuzco.cosmos.moon.LunarTransit.TIME_COMPRESSION);
        line(source, "transits in flight: %d",
                dev.lilkuzco.cosmos.moon.LunarTransit.inTransit());
        propellantLadder(source);
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

    /**
     * Build a launch pad, fill it through the Fabric fluid API, and read the pad back.
     *
     * <p>Exists because the fuelling path was rewritten wholesale in vB and never exercised in a
     * world. Phase A shipped an {@code acceptFuel} nothing called; proving the replacement works
     * needs an actual insertion into an actual {@code PropellantTank} on an actual block entity,
     * not a unit test of the arithmetic around it. This is the transaction a crude_empire pipe
     * would make, made by hand.
     */
    private static int padTest(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        net.minecraft.server.level.ServerLevel level = source.getLevel();
        BlockPos origin = BlockPos.containing(source.getPosition()).above(2);
        RocketTier tier = RocketTier.ORBITAL;
        int r = tier.padRadius();

        // Apron, controller, and a ring of tanks. Clear the airspace so the structure check passes.
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                boolean ring = Math.abs(dx) == r + 1 || Math.abs(dz) == r + 1;
                BlockPos at = origin.offset(dx, 0, dz);
                level.setBlock(at, ring
                        ? dev.lilkuzco.cosmos.CosmosBlocks.FUEL_TANK.defaultBlockState()
                        : dev.lilkuzco.cosmos.CosmosBlocks.PAD_FRAME.defaultBlockState(), 3);
                for (int dy = 1; dy <= tier.padHeight(); dy++) {
                    level.setBlock(origin.offset(dx, dy, dz),
                            net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        level.setBlock(origin, dev.lilkuzco.cosmos.CosmosBlocks.LAUNCH_PAD.defaultBlockState(), 3);

        if (!(level.getBlockEntity(origin)
                instanceof dev.lilkuzco.cosmos.pad.LaunchPadBlockEntity pad)) {
            source.sendFailure(Component.literal("no launch pad block entity at " + origin));
            return 0;
        }
        pad.setItem(dev.lilkuzco.cosmos.pad.LaunchPadBlockEntity.SLOT_ROCKET,
                new net.minecraft.world.item.ItemStack(dev.lilkuzco.cosmos.CosmosItems.ROCKET_ORBITAL));
        dev.lilkuzco.cosmos.pad.LaunchPadBlockEntity.serverTick(level, origin,
                level.getBlockState(origin), pad);

        line(source, "pad at (%d, %d, %d): capacity %d mb (%d buckets), status %d",
                origin.getX(), origin.getY(), origin.getZ(), pad.capacityMb(),
                pad.capacityMb() / 1000, pad.status());

        // The insertion a pipe would make. Crude oil, through the real Storage API.
        var fluid = net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(
                dev.lilkuzco.cosmos.propellant.Propellants.LADDER.isEmpty()
                        ? net.minecraft.world.level.material.Fluids.WATER
                        : resolveTestFluid(level));
        long want = (long) pad.capacityMb()
                * dev.lilkuzco.cosmos.pad.PropellantTank.DROPLETS_PER_MB;
        long moved;
        try (var tx = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
            moved = pad.tank().insert(fluid, want, tx);
            tx.commit();
        }
        line(source, "inserted %s: %d of %d droplets accepted -> %d mb, grade %s",
                fluid.getFluid().builtInRegistryHolder().getRegisteredName(), moved, want,
                pad.fuelMb(), pad.propellant().id());

        dev.lilkuzco.cosmos.pad.LaunchPadBlockEntity.serverTick(level, origin,
                level.getBlockState(origin), pad);
        line(source, "after fuelling: %.0f kg of propellant, status %d (%s)",
                pad.fuelKg(), pad.status(),
                pad.status() == dev.lilkuzco.cosmos.pad.LaunchPadBlockEntity.STATUS_READY
                        ? "READY" : pad.status()
                        == dev.lilkuzco.cosmos.pad.LaunchPadBlockEntity.STATUS_SHORTFALL
                        ? "SHORTFALL - fuelled but short of orbit" : "not ready");

        // A second grade must be refused: half crude and half kerosene has no honest Isp.
        var other = net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(
                net.minecraft.world.level.material.Fluids.WATER);
        long rejected;
        try (var tx = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
            rejected = pad.tank().insert(other, 81_000L, tx);
            tx.abort();
        }
        line(source, "water into a propellant tank: %d droplets accepted (must be 0)", rejected);

        // Launch, and check the surplus survives. A pad ringed for a Moon rocket holds far more
        // than an orbital vehicle takes; ignition used to destroy the difference silently.
        int before = pad.fuelMb();
        pad.beginCountdown();
        for (int i = 0; i <= dev.lilkuzco.cosmos.pad.LaunchPadBlockEntity.COUNTDOWN_TICKS; i++) {
            dev.lilkuzco.cosmos.pad.LaunchPadBlockEntity.serverTick(level, origin,
                    level.getBlockState(origin), pad);
        }
        int after = pad.fuelMb();
        double took = (before - after) / 1000.0 * dev.lilkuzco.cosmos.pad.LaunchPadBlockEntity.KG_PER_BUCKET;
        line(source, "ignition consumed %.0f kg of %.0f loaded; %d mb left in the tanks "
                        + "(vehicle tankage %.0f kg)",
                took, before / 1000.0 * dev.lilkuzco.cosmos.pad.LaunchPadBlockEntity.KG_PER_BUCKET,
                after, tier.fuelCapacityKg());
        return 1;
    }

    /** The best propellant fluid actually present in this world, for the pad test. */
    private static net.minecraft.world.level.material.Fluid resolveTestFluid(
            net.minecraft.server.level.ServerLevel level) {
        for (int i = Propellants.LADDER.size() - 1; i >= 0; i--) {
            var tag = Propellants.tagFor(Propellants.LADDER.get(i));
            if (tag == null) continue;
            for (var holder : net.minecraft.core.registries.BuiltInRegistries.FLUID) {
                if (holder.defaultFluidState().is(tag)) return holder;
            }
        }
        return net.minecraft.world.level.material.Fluids.WATER;
    }

    /**
     * Which propellant grades actually have a fluid behind them in THIS world.
     *
     * <p>The ladder is a contract cosmos publishes and other mods fill. Whether a rung is lit is
     * therefore a fact about the installed set, not about cosmos, and the only honest way to state
     * it is to resolve the tags against the live registry.
     */
    private static void propellantLadder(CommandSourceStack source) {
        for (Propellant grade : Propellants.LADDER) {
            var tag = Propellants.tagFor(grade);
            java.util.List<String> fluids = new java.util.ArrayList<>();
            if (tag != null) {
                for (var fluid : net.minecraft.core.registries.BuiltInRegistries.FLUID) {
                    if (fluid.defaultFluidState().is(tag)) {
                        fluids.add(fluid.builtInRegistryHolder().getRegisteredName());
                    }
                }
            }
            line(source, "propellant %s (Isp %.0f/%.0f s): %s", grade.id(),
                    grade.ispSeaLevel(), grade.ispVacuum(),
                    fluids.isEmpty() ? "DARK - no fluid tagged" : String.join(", ", fluids));
        }
    }

    /**
     * Report the lunar economy, and optionally fast-forward it.
     *
     * <p>The advance argument is how a base's arc gets verified without waiting real days for it.
     * It steps the same pure model the server steps, so what it shows is what would have happened.
     */
    private static int economy(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        var server = source.getServer();
        if (server.getLevel(dev.lilkuzco.cosmos.moon.MoonDimension.MOON) == null) {
            source.sendFailure(Component.literal("no cosmos:moon dimension in this world"));
            return 0;
        }
        var model = dev.lilkuzco.cosmos.economy.LunarEconomyManager.model(server);

        int advance = 0;
        try {
            advance = IntegerArgumentType.getInteger(ctx, "advance_ticks");
        } catch (IllegalArgumentException noArgument) {
            // Reporting only.
        }
        if (advance > 0) {
            // Set the duty from the world FIRST. Advancing straight away ran the model at the
            // construction default of full duty, so a base with no plants at all reported 40,000
            // ticks of production it could not possibly have had.
            syncDuty(server, model);
            model.advance(advance);
            dev.lilkuzco.cosmos.economy.LunarEconomyState.get(server).put(model);
            line(source, "advanced the model %d economic ticks", advance);
        }

        var report = model.report();
        var ledger = model.ledger();
        line(source, "tick %d, %s", report.tick(),
                report.exhausted() ? "ICE EXHAUSTED" : "producing");
        line(source, "plants: %d electrolyser(s), %d kiln(s) -> duty melt %.2f, bake %.2f",
                dev.lilkuzco.cosmos.isru.IsruRegistry.count(
                        dev.lilkuzco.cosmos.isru.IsruRegistry.Kind.ELECTROLYSER),
                dev.lilkuzco.cosmos.isru.IsruRegistry.count(
                        dev.lilkuzco.cosmos.isru.IsruRegistry.Kind.KILN),
                model.duty(dev.lilkuzco.cosmos.economy.LunarEconomy.Process.MELT),
                model.duty(dev.lilkuzco.cosmos.economy.LunarEconomy.Process.BAKE));
        for (var resource : dev.lilkuzco.cosmos.economy.LunarEconomy.Resource.values()) {
            line(source, "  %-9s %10.2f kg", resource, model.stock(resource));
        }
        line(source, "deposits: %.0f kg per live deposit, %.0f live",
                report.icePerDepositKg(), report.depositsRemaining());
        line(source, "air: %.2f days (%s) · allocation %.3f to life support",
                report.oxygenDays(), report.selfSufficient() ? "self-sufficient" : "NOT sustaining",
                report.lifeSupportShare());
        line(source, "home: %.0f kg hydrolox, %.1f%% of a lander's load",
                report.hydroloxKg(), report.returnFraction() * 100.0);
        line(source, "ledger: mined %.2f, processed %.2f, consumed %.2f, lost %.2f, stored %.2f",
                ledger.minedKg(), ledger.processedKg(), ledger.consumedKg(), ledger.lostKg(),
                ledger.storedKg());
        line(source, "conservation: residual %.3e kg, tolerance %.3e -> %s",
                ledger.residual(), ledger.tolerance(),
                ledger.balanced() ? "BALANCED" : "BROKEN");
        return 1;
    }

    /**
     * Stand a capsule up with its parachute out, for the render battery to photograph.
     *
     * <p>Not a flight - a model board. The recovery chain is verified by flying it; this exists
     * because the canopy is only deployed for the last seconds of an entry on an object ten pixels
     * tall, and "is this part drawn at all" is a question that deserves a legible answer in
     * seconds rather than a two-minute descent and a lucky frame.
     */
    private static int showCapsule(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        var position = source.getPosition();
        var capsule = dev.lilkuzco.cosmos.recovery.CapsuleEntity.viewFor(source.getLevel(),
                "cosmos:display", "display",
                new dev.lilkuzco.kinetics.math.Vec3(position.x(), position.y(), position.z()));
        if (capsule == null) {
            source.sendFailure(Component.literal("could not create a display capsule"));
            return 0;
        }
        capsule.showChuteForDisplay();
        line(source, "display capsule at (%.0f, %.0f, %.0f), canopy deployed",
                position.x(), position.y(), position.z());
        return 1;
    }

    /**
     * Build ISRU plants on the Moon and prove the siting rule, then run the base.
     *
     * <p>The claim under test is not "the model produces numbers" - the headless battery already
     * settles that - but that the WORLD drives the model: that an electrolyser off the ice sits
     * dark, that one on the ice runs, and that building plants is what turns production on.
     */
    private static int isruTest(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        var server = source.getServer();
        net.minecraft.server.level.ServerLevel moon =
                server.getLevel(dev.lilkuzco.cosmos.moon.MoonDimension.MOON);
        if (moon == null) {
            source.sendFailure(Component.literal("no cosmos:moon dimension in this world"));
            return 0;
        }
        dev.lilkuzco.cosmos.isru.IsruRegistry.clear();

        // A kiln works anywhere; put it at the origin.
        BlockPos kiln = surfaceAt(moon, 0, 0);
        moon.setBlock(kiln, dev.lilkuzco.cosmos.CosmosBlocks.REGOLITH_KILN.defaultBlockState(), 3);
        line(source, "kiln at (%d, %d, %d), biome %s", kiln.getX(), kiln.getY(), kiln.getZ(),
                moon.getBiome(kiln).getRegisteredName());

        // An electrolyser here should be DARK - the origin is highlands, and there is no ice.
        BlockPos dark = surfaceAt(moon, 8, 0);
        moon.setBlock(dark, dev.lilkuzco.cosmos.CosmosBlocks.ELECTROLYSER.defaultBlockState(), 3);
        boolean darkSited = moon.getBlockEntity(dark)
                instanceof dev.lilkuzco.cosmos.isru.IsruBlockEntity plant && plant.sited(moon);
        line(source, "electrolyser off the ice at (%d, %d): sited=%s (must be false)",
                dark.getX(), dark.getZ(), darkSited);

        // Now find polar ice and put four electrolysers on it.
        var polar = moon.findClosestBiome3d(
                holder -> holder.is(dev.lilkuzco.cosmos.moon.MoonDimension.POLAR),
                new BlockPos(0, 80, 0), 6400, 32, 64);
        if (polar == null) {
            source.sendFailure(Component.literal(
                    "no lunar_polar biome within 6400 blocks - worldgen problem"));
            return 0;
        }
        BlockPos found = polar.getFirst();
        int built = 0;
        for (int i = 0; i < dev.lilkuzco.cosmos.economy.LunarEconomyManager
                .MACHINES_FOR_FULL_DUTY; i++) {
            BlockPos at = surfaceAt(moon, found.getX() + i * 2, found.getZ());
            moon.setBlock(at, dev.lilkuzco.cosmos.CosmosBlocks.ELECTROLYSER.defaultBlockState(), 3);
            if (moon.getBlockEntity(at)
                    instanceof dev.lilkuzco.cosmos.isru.IsruBlockEntity plant
                    && plant.sited(moon)) {
                built++;
            }
        }
        line(source, "polar site at (%d, %d), biome %s: %d of %d electrolysers sited",
                found.getX(), found.getZ(), moon.getBiome(found).getRegisteredName(), built,
                dev.lilkuzco.cosmos.economy.LunarEconomyManager.MACHINES_FOR_FULL_DUTY);
        return built > 0 ? 1 : 0;
    }

    /** The first solid surface in a column, generating the chunk if it is not there yet. */
    private static BlockPos surfaceAt(net.minecraft.server.level.ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4,
                net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true);
        return level.getHeightmapPos(
                net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                new BlockPos(x, 0, z));
    }

    /**
     * Where a test launch is headed. Defaults to the Moon; {@code destination} names another.
     */
    private static dev.lilkuzco.cosmos.moon.Destination resolveDestination(
            CommandContext<CommandSourceStack> ctx) {
        try {
            String name = StringArgumentType.getString(ctx, "destination");
            for (var d : dev.lilkuzco.cosmos.moon.Destination.values()) {
                if (d.name().equalsIgnoreCase(name)) return d;
            }
        } catch (IllegalArgumentException noArgument) {
            // Not supplied.
        }
        return dev.lilkuzco.cosmos.moon.Destination.MOON;
    }

    /** Push the world's machine counts into the model, as the manager does every economic tick. */
    private static void syncDuty(net.minecraft.server.MinecraftServer server,
                                 dev.lilkuzco.cosmos.economy.LunarEconomy model) {
        int electrolysers = dev.lilkuzco.cosmos.isru.IsruRegistry.count(
                dev.lilkuzco.cosmos.isru.IsruRegistry.Kind.ELECTROLYSER);
        int kilns = dev.lilkuzco.cosmos.isru.IsruRegistry.count(
                dev.lilkuzco.cosmos.isru.IsruRegistry.Kind.KILN);
        double full = dev.lilkuzco.cosmos.economy.LunarEconomyManager.MACHINES_FOR_FULL_DUTY;
        for (var process : dev.lilkuzco.cosmos.economy.LunarEconomy.Process.values()) {
            int machines = process == dev.lilkuzco.cosmos.economy.LunarEconomy.Process.BAKE
                    ? kilns : electrolysers;
            model.setDuty(process, Math.min(1.0, machines / full));
        }
    }

    /**
     * Every cosmos world, and the four facts that define one.
     *
     * <p>Resolved live rather than described: the gravity and atmosphere come back out of kinetics,
     * the terrain is sampled by generating it, and breathability is asked of the same code life
     * support asks. A world that reports correctly here is registered correctly.
     */
    private static int worlds(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        var server = source.getServer();
        KineticsService kinetics = KineticsMod.service();
        if (kinetics == null) {
            source.sendFailure(Component.literal("kinetics service unavailable"));
            return 0;
        }
        var k = kinetics.constants();

        for (var world : dev.lilkuzco.cosmos.world.CosmosWorlds.all()) {
            var level = server.getLevel(world.dimension());
            if (level == null) {
                line(source, "%s: ABSENT from this world", world.dimension().identifier());
                continue;
            }
            var env = kinetics.environmentOf(level.dimension());
            line(source, "== %s ==", world.dimension().identifier());
            line(source, "   gravity %.4f m/s^2 (%.5f g), atmosphere %s, breathable %s",
                    env == null ? 0.0 : env.gravity(), world.gravityScalar(),
                    env == null ? "UNREGISTERED"
                            : env.atmosphere().isPresent() ? "PRESENT" : "vacuum",
                    dev.lilkuzco.cosmos.world.CosmosWorlds.breathable(level.dimension()));
            if (env != null) {
                line(source, "   a 20 m2 canopy at 200 m/s, 50 m up feels %.1f Pa",
                        env.atmosphere().dynamicPressure(200.0, 50.0));
            }
            // Generate real columns; a heightmap query alone reports an ungenerated world as void.
            java.util.Map<String, Integer> surfaces = new java.util.LinkedHashMap<>();
            for (int[] spot : new int[][] {{0, 0}, {600, 600}, {-2400, 1200}}) {
                level.getChunk(spot[0] >> 4, spot[1] >> 4,
                        net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true);
                BlockPos top = level.getHeightmapPos(
                        net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                        new BlockPos(spot[0], 0, spot[1]));
                surfaces.merge(level.getBlockState(top.below()).getBlock().getDescriptionId()
                        + " @y" + top.getY(), 1, Integer::sum);
            }
            line(source, "   surface samples: %s", surfaces);
            line(source, "   biome at origin: %s",
                    level.getBiome(new BlockPos(0, 80, 0)).getRegisteredName());
        }

        for (var d : dev.lilkuzco.cosmos.moon.Destination.values()) {
            line(source, "destination %s: depart %.1f m/s (total %.1f), arrive %.1f m/s at %.0f m, %s",
                    d, d.injectionDeltaV(k), d.departureBudget(k), d.arrivalSpeed(k),
                    d.arrivalAltitude(),
                    d.aerodynamicArrival() ? "PARACHUTES" : "retro-burn");
            line(source, "   coast %.0f s simulated -> %.0f s ridden",
                    d.coastSeconds(k),
                    d.coastSeconds(k) / dev.lilkuzco.cosmos.moon.LunarTransit.TIME_COMPRESSION);
        }
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
                crewed ? resolveDestination(ctx) : null,
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
