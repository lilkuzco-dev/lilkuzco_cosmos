package dev.lilkuzco.cosmos.world;

import dev.lilkuzco.cosmos.Cosmos;
import dev.lilkuzco.kinetics.fabric.KineticsMod;
import dev.lilkuzco.kinetics.fabric.KineticsService;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every world cosmos adds, and the four facts that define one.
 *
 * <p>A destination in this campaign is a gravity scalar, an atmosphere or the absence of one, a
 * dimension, and an economy. Nothing downstream knows which body it is looking at: parachutes work
 * where there is air because the drag equation says so, and life support fails where there is
 * nothing to breathe because it asks rather than assumes.
 *
 * <p><b>Breathability is cosmos' to decide, not kinetics'.</b> The Moon taught the wrong lesson
 * here: it has no atmosphere, so "has air" and "can breathe" were the same question and life
 * support could ask kinetics. The outer moon breaks that — it has a thick atmosphere of nitrogen
 * and methane and you would die in it in minutes. Kinetics answers "is there air to fly through",
 * which is a physics question; whether a person can breathe it is not, and is answered here.
 */
public final class CosmosWorlds {

	/** One destination, and everything that distinguishes it from every other. */
	public record World(ResourceKey<Level> dimension, double gravityScalar, boolean hasAtmosphere,
	                    boolean breathable, String translationKey) {}

	public static final ResourceKey<Level> MOON =
			ResourceKey.create(Registries.DIMENSION, Cosmos.id("moon"));

	public static final ResourceKey<Level> HAZE =
			ResourceKey.create(Registries.DIMENSION, Cosmos.id("haze"));

	/** The polar biome, where lunar water ice is. Siting an electrolyser depends on it. */
	public static final ResourceKey<Biome> LUNAR_POLAR =
			ResourceKey.create(Registries.BIOME, Cosmos.id("lunar_polar"));

	/** Where the ammonia is. Siting a cracker depends on it. */
	public static final ResourceKey<Biome> AMMONIA_SHELF =
			ResourceKey.create(Registries.BIOME, Cosmos.id("ammonia_shelf"));

	private static final Map<ResourceKey<Level>, World> WORLDS = new LinkedHashMap<>();

	static {
		// The Moon: airless, a sixth of a g. Landing is a retro-burn because nothing brakes you.
		put(new World(MOON, 0.16519, false, false, "cosmos.world.moon"));
		// The outer moon: THICK AIR at 0.138 g, and unbreathable. Landing is parachutes.
		put(new World(HAZE, 0.13787, true, false, "cosmos.world.haze"));
	}

	private CosmosWorlds() {
	}

	private static void put(World world) {
		WORLDS.put(world.dimension(), world);
	}

	public static Iterable<World> all() {
		return WORLDS.values();
	}

	public static World of(ResourceKey<Level> dimension) {
		return WORLDS.get(dimension);
	}

	public static boolean isCosmosWorld(Level level) {
		return WORLDS.containsKey(level.dimension());
	}

	/**
	 * Whether a player can breathe here.
	 *
	 * <p>A dimension cosmos does not know about is breathable, so a world without these dimensions
	 * registered never suffocates anybody.
	 */
	public static boolean breathable(ResourceKey<Level> dimension) {
		World world = WORLDS.get(dimension);
		return world == null || world.breathable();
	}

	/**
	 * Register every cosmos world with kinetics on the first server tick.
	 *
	 * <p>Deferred rather than done at SERVER_STARTED because Fabric gives no ordering guarantee
	 * between two mods' handlers, and cosmos once ran before kinetics existed.
	 */
	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(new ServerTickEvents.EndTick() {
			private boolean done;

			@Override
			public void onEndTick(MinecraftServer server) {
				if (done) return;
				KineticsService kinetics = KineticsMod.service();
				if (kinetics == null) return;
				done = true;

				for (World world : WORLDS.values()) {
					ServerLevel level = server.getLevel(world.dimension());
					if (level == null) {
						// A world created before cosmos was installed has no such dimension.
						Cosmos.LOG.warn("no {} dimension in this world - not registered",
								world.dimension().identifier());
						continue;
					}
					kinetics.registerDimension(level, world.hasAtmosphere(),
							world.gravityScalar(), false);
					Cosmos.LOG.info("{} registered with kinetics: {} g, {}",
							world.dimension().identifier(), world.gravityScalar(),
							world.hasAtmosphere()
									? "atmosphere present (drag, lift, parachutes all work)"
									: "vacuum (no drag, no lift, parachutes inert)");
				}
			}
		});
	}
}
