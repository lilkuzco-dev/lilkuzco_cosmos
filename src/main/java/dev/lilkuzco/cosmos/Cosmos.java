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
                "0.1.0-F",
                dev.lilkuzco.cosmos.rocket.RocketTier.LADDER.size(),
                dev.lilkuzco.cosmos.propellant.Propellants.LADDER.size());
    }
}
