package dev.lilkuzco.cosmos.economy;

import dev.lilkuzco.cosmos.Cosmos;
import dev.lilkuzco.cosmos.isru.IsruRegistry;
import dev.lilkuzco.cosmos.moon.MoonDimension;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Runs the lunar economy on the server tick, exactly as warfront's {@code EconomyManager} runs its
 * cities: hold one live model, step it on a slower clock than the game, persist the snapshot, and
 * keep the last report for anything that wants to read it.
 *
 * <p>The one structural difference is where the population comes from. Warfront's model is sized
 * by a city's citizen count; this one has a fixed roster and is <b>throttled by the machines that
 * physically exist</b>. A world with no electrolyser produces no propellant — not because the model
 * says so, but because the duty cycle for that process is zero.
 */
public final class LunarEconomyManager {

	/** Game ticks per economic tick. Warfront runs its economy slowly too, and for the same reason. */
	public static final int TICKS_PER_ECONOMIC_TICK = 20;

	/** Machines of one kind that constitute a full duty cycle. Beyond this, more is not faster. */
	public static final int MACHINES_FOR_FULL_DUTY = 4;

	private static LunarEconomy model;
	private static LunarEconomy haze;
	private static LunarEconomy.Report lastHazeReport;
	private static LunarEconomy.Report lastReport;
	private static long lastNanos = -1L;

	private LunarEconomyManager() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			model = null;
			haze = null;
			lastHazeReport = null;
			lastReport = null;
			lastNanos = -1L;
			hydrated = false;
		});
		ServerTickEvents.END_SERVER_TICK.register(LunarEconomyManager::tick);
	}

	private static boolean hydrated;

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % TICKS_PER_ECONOMIC_TICK != 0) return;
		if (!hydrated) {
			IsruRegistry.hydrate(server);
			hydrated = true;
		}
		ServerLevel moon = server.getLevel(MoonDimension.MOON);
		if (moon == null) return;

		LunarEconomy economy = model(server);
		long started = System.nanoTime();
		tickHaze(server);

		// The world sets the throttle. Counting machines every economic tick rather than caching
		// it means breaking an electrolyser stops production immediately, which is the behaviour a
		// player will assume and would be surprised to find delayed.
		economy.setDuty(LunarEconomy.Process.MELT,
				duty(IsruRegistry.count(IsruRegistry.Kind.ELECTROLYSER)));
		economy.setDuty(LunarEconomy.Process.ELECTROLYSE,
				duty(IsruRegistry.count(IsruRegistry.Kind.ELECTROLYSER)));
		economy.setDuty(LunarEconomy.Process.MIX,
				duty(IsruRegistry.count(IsruRegistry.Kind.ELECTROLYSER)));
		economy.setDuty(LunarEconomy.Process.BAKE,
				duty(IsruRegistry.count(IsruRegistry.Kind.KILN)));

		economy.step();
		lastReport = economy.report();
		lastNanos = System.nanoTime() - started;

		// Persist every economic tick. The snapshot is 424 characters; the alternative is losing
		// a base's entire production history to a crash, which is a much worse trade.
		LunarEconomyState.get(server).put(economy);
	}

	/**
	 * The outer moon's economy: one cracker roster, one model, the same conservation audit.
	 *
	 * <p>A second world does not get a second model class. It gets the same one with a different
	 * process enabled and a different seed - which is the test of whether the template generalised
	 * or was only ever the Moon's.
	 */
	private static void tickHaze(MinecraftServer server) {
		if (server.getLevel(dev.lilkuzco.cosmos.world.CosmosWorlds.HAZE) == null) return;
		LunarEconomy economy = hazeModel(server);
		for (LunarEconomy.Process process : LunarEconomy.Process.values()) {
			economy.setDuty(process, process == LunarEconomy.Process.CRACK
					? duty(IsruRegistry.count(IsruRegistry.Kind.CRACKER)) : 0.0);
		}
		economy.step();
		lastHazeReport = economy.report();
		LunarEconomyState.get(server).putHaze(economy);
	}

	private static LunarEconomy hazeModel(MinecraftServer server) {
		if (haze != null) return haze;
		String snapshot = LunarEconomyState.get(server).hazeSnapshot();
		if (snapshot != null && !snapshot.isEmpty()) {
			try {
				haze = LunarEconomy.decode(snapshot);
				return haze;
			} catch (IllegalArgumentException exception) {
				Cosmos.LOG.error("outer-moon economy snapshot failed validation; starting fresh",
						exception);
			}
		}
		long seed = server.overworld().getSeed() ^ 0x48415A45L;   // "HAZE"
		haze = new LunarEconomy(LunarEconomy.Config.validation(seed));
		Cosmos.LOG.info("outer-moon economy started, seed {}", seed);
		return haze;
	}

	/** The outer moon's last report, or null if that world does not exist. */
	public static LunarEconomy.Report hazeReport() { return lastHazeReport; }

	public static LunarEconomy hazeModelOrNull() { return haze; }

	private static double duty(int machines) {
		return Math.min(1.0, machines / (double) MACHINES_FOR_FULL_DUTY);
	}

	/** The live model, decoded from the snapshot on first use. */
	public static LunarEconomy model(MinecraftServer server) {
		if (model != null) return model;
		String snapshot = LunarEconomyState.get(server).snapshot();
		if (snapshot != null && !snapshot.isEmpty()) {
			try {
				model = LunarEconomy.decode(snapshot);
				Cosmos.LOG.info("lunar economy restored at tick {}", model.tick());
				return model;
			} catch (IllegalArgumentException exception) {
				Cosmos.LOG.error("lunar economy snapshot failed validation; starting fresh",
						exception);
			}
		}
		long seed = server.overworld().getSeed() ^ 0x4C554E41L;   // "LUNA"
		model = new LunarEconomy(LunarEconomy.Config.validation(seed));
		Cosmos.LOG.info("lunar economy started: {} deposits, seed {}",
				LunarEconomy.Config.validation(seed).deposits(), seed);
		return model;
	}

	public static LunarEconomy.Report report(MinecraftServer server) {
		return lastReport != null ? lastReport : model(server).report();
	}

	public static LunarEconomy.Ledger ledger(MinecraftServer server) {
		return model(server).ledger();
	}

	public static long tickNanos() {
		return lastNanos;
	}

	/**
	 * Take produced mass out of the model and into the world.
	 *
	 * <p>The other half of {@link LunarEconomy#reconcile}: when a machine hands a player a bucket
	 * of hydrolox or a stack of sinter, that mass has left the model's books and has to be
	 * recorded as consumed, not quietly deleted. Free energy leaks in both directions.
	 *
	 * @return how much was actually available and withdrawn, kg
	 */
	public static double withdraw(MinecraftServer server, LunarEconomy.Resource resource,
	                              double kilograms) {
		LunarEconomy economy = model(server);
		double available = Math.min(kilograms, economy.stock(resource));
		if (available <= 0.0) return 0.0;
		economy.reconcile(resource, economy.stock(resource) - available);
		LunarEconomyState.get(server).put(economy);
		return available;
	}
}
