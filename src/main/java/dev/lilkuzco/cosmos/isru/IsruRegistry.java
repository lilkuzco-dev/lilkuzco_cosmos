package dev.lilkuzco.cosmos.isru;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Which ISRU machines physically exist, and therefore how fast the lunar economy runs.
 *
 * <p>This is the world's half of the model's duty cycle. The economy has a fixed roster so its
 * snapshot always decodes into the same shape; what varies is how much of that roster is actually
 * built, and the only honest source for that is the blocks a player has placed.
 *
 * <p>Positions rather than a count, because a count cannot be corrected. A block entity that is
 * unloaded and reloaded would otherwise increment the tally twice, and a base would quietly
 * produce more the more often its chunks were revisited.
 */
public final class IsruRegistry {

	public enum Kind {
		/** Melts ice, splits water, mixes propellant. Needs to be sited on ice. */
		ELECTROLYSER,
		/** Bakes regolith for oxygen and building material. Works anywhere. */
		KILN,
		/**
		 * Cracks ammonia into nitrogen and hydrogen, on the outer moon.
		 *
		 * <p>The kind implies the world: electrolysers and kilns only run on the Moon, crackers
		 * only on the outer moon. So a machine's kind is enough to say which economy it drives,
		 * and the registry does not need to carry a dimension as well.
		 */
		CRACKER
	}

	private static final Map<Kind, Set<BlockPos>> LIVE = new EnumMap<>(Kind.class);

	static {
		for (Kind kind : Kind.values()) LIVE.put(kind, new LinkedHashSet<>());
	}

	private IsruRegistry() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
	}

	/**
	 * Record a plant. Called when the block is PLACED, not when its chunk loads.
	 *
	 * <p>That distinction is the whole point. Tying the roster to block-entity ticks meant a base
	 * stopped producing the moment its chunks unloaded — a player who built four electrolysers at
	 * the pole and flew home found the base had done nothing while they were away. Cosmos has now
	 * learned this three times: launches, recoveries, and here. <b>Anything that should happen
	 * while nobody is watching cannot hang off an entity or block-entity tick.</b>
	 */
	public static void add(Kind kind, BlockPos pos,
	                       net.minecraft.server.MinecraftServer server) {
		if (LIVE.get(kind).add(pos.immutable())) persist(server);
	}

	/** Forget a plant. Called on genuine removal only — never on chunk unload. */
	public static void remove(Kind kind, BlockPos pos,
	                          net.minecraft.server.MinecraftServer server) {
		if (LIVE.get(kind).remove(pos)) persist(server);
	}

	public static int count(Kind kind) {
		return LIVE.get(kind).size();
	}

	public static java.util.Set<BlockPos> positions(Kind kind) {
		return java.util.Set.copyOf(LIVE.get(kind));
	}

	public static void clear() {
		for (Set<BlockPos> positions : LIVE.values()) positions.clear();
	}

	/** Load the roster from the world. Idempotent; called before the first count of a session. */
	public static void hydrate(net.minecraft.server.MinecraftServer server) {
		var state = dev.lilkuzco.cosmos.economy.LunarEconomyState.get(server);
		for (Kind kind : Kind.values()) {
			LIVE.get(kind).clear();
			LIVE.get(kind).addAll(state.plants(kind));
		}
	}

	private static void persist(net.minecraft.server.MinecraftServer server) {
		if (server == null) return;
		var state = dev.lilkuzco.cosmos.economy.LunarEconomyState.get(server);
		for (Kind kind : Kind.values()) state.putPlants(kind, LIVE.get(kind));
	}
}
