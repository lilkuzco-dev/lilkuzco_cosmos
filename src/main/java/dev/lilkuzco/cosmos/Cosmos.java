package dev.lilkuzco.cosmos;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cosmos: rockets, orbit, satellites and recovery.
 *
 * <p><b>Cosmos contains no physics.</b> Every metre of motion in this mod is integrated by
 * kinetics. Cosmos decides what a rocket is made of, what it costs, what it looks like and what
 * a player can do with it; kinetics decides whether it flies. Any motion need not covered by the
 * kinetics API is a proposed addition to kinetics, never local maths here - because local maths
 * is how two mods end up with two different gravities.
 *
 * <p>Damage, if it is ever needed, goes through warfront's {@code AreaStrike.resolve()}. Energy
 * is crude_empire's lane. Worldgen is nobody's in this campaign.
 */
public final class Cosmos implements ModInitializer {

    public static final String MOD_ID = "cosmos";
    public static final Logger LOG = LoggerFactory.getLogger("lilkuzco-cosmos");

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        requireKineticsConstants();
        CosmosFluids.register();
        CosmosBlocks.register();
        CosmosItems.register();
        CosmosBlockEntities.register();
        CosmosEntities.register();
        CosmosMenus.register();
        dev.lilkuzco.cosmos.command.CosmosCommands.register();
        dev.lilkuzco.cosmos.satellite.SatelliteConstellation.register();
        dev.lilkuzco.cosmos.rocket.LaunchTracker.register();
        dev.lilkuzco.cosmos.recovery.RecoveryTracker.register();
        dev.lilkuzco.cosmos.worldgen.CosmosWorldgen.register();
        dev.lilkuzco.cosmos.moon.MoonDimension.register();
        dev.lilkuzco.cosmos.moon.LunarTransit.register();
        dev.lilkuzco.cosmos.isru.IsruRegistry.register();
        dev.lilkuzco.cosmos.economy.LunarEconomyManager.register();
        dev.lilkuzco.cosmos.life.LifeSupport.register();

        LOG.info("cosmos {} ready: {} rocket tiers, {} propellant grades",
                "0.2.2",
                dev.lilkuzco.cosmos.rocket.RocketTier.LADDER.size(),
                dev.lilkuzco.cosmos.propellant.Propellants.LADDER.size());
    }

    /**
     * Refuse to start against a kinetics that is missing constants cosmos needs.
     *
     * <p><b>A released version is immutable, and this exists because I broke that.</b> Haze's
     * constants were added to kinetics and committed <em>after</em> 0.1.3 was published, without a
     * version bump — so 0.1.3 named two different artifacts, and the manifest happily shipped the
     * one without them beside a cosmos that needed them. The load-compatibility gate could not see
     * it: cosmos declared {@code kinetics >=0.1.3}, and 0.1.3 was present.
     *
     * <p>A version predicate cannot catch a version that changed underneath it. Naming the
     * constants can. The failure is now at load, loudly, saying exactly which one is missing —
     * rather than a ConstantsException thrown mid-flight the first time somebody aims at Haze.
     */
    private static void requireKineticsConstants() {
        var constants = dev.lilkuzco.kinetics.constants.Constants.get();
        java.util.List<String> missing = new java.util.ArrayList<>();
        for (String required : new String[] {
                "orbit.delta_v_to_orbit", "orbit.lunar_distance", "orbit.lunar_transfer_delta_v",
                "orbit.lunar_descent_delta_v", "orbit.haze_distance",
                "orbit.haze_orbit_insertion_delta_v", "orbit.haze_descent_delta_v",
                "gravity.dimension_scalars.moon", "gravity.dimension_scalars.haze",
                "landing.touchdown_speed" }) {
            try {
                constants.d(required);
            } catch (RuntimeException absent) {
                missing.add(required);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("this kinetics is missing constants cosmos needs: "
                    + missing + ". A kinetics version was published and then changed underneath "
                    + "its own version number; install a newer one.");
        }
    }
}
