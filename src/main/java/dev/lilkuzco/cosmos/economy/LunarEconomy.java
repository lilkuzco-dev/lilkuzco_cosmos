package dev.lilkuzco.cosmos.economy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;

/**
 * The lunar base economy: a closed, deterministic, mass-conserving resource model.
 *
 * <p><b>Built on warfront's economic template</b>, which is the empire's answer to "how do you
 * simulate an economy without inventing wealth". Everything structural is carried across: a pure
 * model with no Minecraft dependency, a seeded SplitMix64 so the same seed replays byte-identically,
 * finite nodes that can be mined out for good, periodic shocks that <em>move or destroy</em> stock
 * but never conjure it, a Base64 snapshot behind a magic number, a metrics record, and a
 * conservation audit that <b>throws every single tick</b> if the books do not balance.
 *
 * <p>Two things are deliberately different, and both follow from where this economy lives.
 *
 * <p><b>The conserved quantity is mass, not money.</b> Warfront conserves coins among citizens;
 * the Moon has no citizens and no currency, and inventing one would be cargo-culting the template
 * rather than applying it. What a lunar base actually conserves is kilograms - which is also the
 * quantity kinetics already polices in its own mass ledger (I4), so the two agree by construction.
 * Every process below balances mass <em>exactly</em>, and the audit is in kg.
 *
 * <p><b>Ice does not regenerate.</b> Warfront's forests and farms grow back; some of its ore veins
 * do not. On the Moon <em>nothing</em> grows back, because there is no water cycle to bring it
 * back. Polar ice is billions of years of cometary delivery sitting in permanent shadow, and once
 * a crater is mined out it is mined out. That single fact is what makes a lunar base a place you
 * site carefully rather than a tap you turn on.
 *
 * <h2>The chemistry is real, and it is the whole design</h2>
 *
 * Water is 11.19% hydrogen and 88.81% oxygen by mass. Hydrolox engines burn fuel-rich at about
 * 6:1 oxygen to hydrogen, not at the stoichiometric 8:1. So electrolysing a kilogram of lunar
 * water yields 0.1119 kg of hydrogen, which can only burn 0.6714 kg of oxygen — leaving
 * <b>0.2167 kg of oxygen spare, about a quarter of it</b>.
 *
 * <p>That surplus is not a game concession. It is the reason every serious lunar architecture
 * proposes ISRU for life support and propellant together: you cannot make the fuel without also
 * making more air than you need. A player who builds an electrolyser to get home discovers they
 * have also solved breathing, and they discover it because of arithmetic rather than because a
 * designer was being generous.
 */
public final class LunarEconomy {

	/** Everything the model tracks, in kilograms. */
	public enum Resource {
		/** Water ice in permanent shadow. Finite, and it never comes back. */
		ICE,
		/** Melted ice, the feedstock for electrolysis. */
		WATER,
		/** Breathable oxygen, and the oxidiser half of hydrolox. */
		OXYGEN,
		/** Liquid hydrogen, the fuel half. Useless alone; it is what the oxygen is for. */
		HYDROGEN,
		/** Cryogenic propellant, mixed 6:1. This is the way home. */
		HYDROLOX,
		/** The surface itself. Effectively unbounded, and rate-limited rather than finite. */
		REGOLITH,
		/** Baked regolith: the building material a base makes out of the ground it stands on. */
		SINTER,
		/** Ammonia ice, on the outer moon. Stable at the surface there, unlike lunar water. */
		AMMONIA,
		/** Nitrogen: a habitat buffer gas, and most of what ammonia turns out to be. */
		NITROGEN
	}

	/** What an installation does. Every process balances mass exactly. */
	public enum Process {
		/** Ice to water. A heater and patience. */
		MELT,
		/** Water to hydrogen and oxygen, at water's real mass fractions. */
		ELECTROLYSE,
		/** Hydrogen and oxygen to propellant at the 6:1 mixture ratio engines actually use. */
		MIX,
		/** Regolith to oxygen and sinter. The mundane one, and the one that works anywhere. */
		BAKE,
		/**
		 * Ammonia to nitrogen and hydrogen, at ammonia's real mass fractions.
		 *
		 * <p>The outer moon's answer to lunar ice, and a deliberately lopsided one: it yields fuel
		 * and buffer gas but <b>no oxygen at all</b>. A base there can make propellant it cannot
		 * burn and gas it cannot breathe. That is the trade - the Moon gives you both halves and
		 * runs out; the outer moon gives you one half and does not.
		 */
		CRACK
	}

	/**
	 * What goes wrong on the Moon. All four are real failure modes of an airless, unshielded body,
	 * and none of them destroys mass without recording it.
	 */
	public enum Shock {
		/** A puncture. Stored volatiles vent to vacuum, which is what vacuum is for. */
		MICROMETEORITE,
		/** Lunar night: fourteen days without sun. Production stops and cryogens boil off. */
		LUNAR_NIGHT,
		/**
		 * Regolith slumps into a mined-out void and buries part of a deposit.
		 *
		 * <p>Was once DEPOSIT_EXHAUSTED, which destroyed a whole crater at a stroke. Over 40,000
		 * ticks that fired often enough to wipe out all six deposits before a base could be
		 * built - the model was hostile rather than difficult, and the failure was invisible
		 * because the books still balanced. Exhaustion is not a shock anyway: it is the ordinary
		 * consequence of mining, and {@code mine()} already delivers it.
		 */
		CRATER_COLLAPSE,
		/** Exposed ice returns to vacuum. Slow, constant, and the reason storage matters. */
		SUBLIMATION
	}

	// ---- real physical constants -------------------------------------------

	/** Hydrogen's share of water by mass: 2.016 / 18.015. */
	public static final double HYDROGEN_FRACTION = 0.111894;

	/** Oxygen's share of water by mass. The remainder, exactly. */
	public static final double OXYGEN_FRACTION = 1.0 - HYDROGEN_FRACTION;

    /**
     * Hydrolox mixture ratio, oxidiser to fuel by mass.
     *
     * <p>6.0, not the stoichiometric 8.0. Real hydrolox engines run fuel-rich because it raises
     * exhaust velocity - the products are lighter - and because it keeps the chamber survivable.
     * The RS-25 runs 6.03. This one number is what creates the oxygen surplus below.
     */
    public static final double MIXTURE_RATIO = 6.0;

	/**
	 * Hydrogen's share of ammonia by mass: 3.024 / 17.031.
	 *
	 * <p>NH3 is 82.24% nitrogen and 17.76% hydrogen — a far richer hydrogen source per kilogram
	 * than water's 11.19%, which is exactly why ammonia is the outer moon's advantage.
	 */
	public static final double AMMONIA_HYDROGEN_FRACTION = 3.024 / 17.031;

	/** Nitrogen's share of ammonia by mass. The remainder, exactly. */
	public static final double AMMONIA_NITROGEN_FRACTION = 1.0 - AMMONIA_HYDROGEN_FRACTION;

	/**
	 * Oxygen recoverable from regolith, as a fraction of the mass processed.
	 *
	 * <p>Lunar regolith is about 45% oxygen by mass, but it is <em>bound</em> in silicates and
	 * getting it out is the hard part. Hydrogen reduction of ilmenite recovers a few percent of
	 * the feedstock in practice. 3% is a deliberately unflattering figure: baking the ground for
	 * air works everywhere and works badly, which is exactly the trade against ice.
	 */
	public static final double REGOLITH_OXYGEN_YIELD = 0.03;

	// ---- configuration ------------------------------------------------------

	public record Config(long seed, int deposits, double depositMassKg, int installations,
	                     double throughputKgPerTick, double storageKg, double crewOxygenKgPerTick,
	                     int shockInterval, int shockPermille) {
		public Config {
			if (deposits < 1 || depositMassKg <= 0 || installations < 1 || throughputKgPerTick <= 0
					|| storageKg <= 0 || crewOxygenKgPerTick < 0 || shockInterval < 0
					|| shockPermille < 0 || shockPermille > 1_000) {
				throw new IllegalArgumentException("invalid lunar economy configuration");
			}
		}

		/**
		 * The shipped configuration, and the one the battery is written against.
		 *
		 * <p>The shock numbers are measured, not guessed. At one shock every 400 economic ticks
		 * costing 15% of a store, a base ran 48,000 ticks, lost 247 tonnes, stranded 75 more and
		 * finished with <b>3.2 tonnes of propellant out of a possible 188</b> — a 98% tax, which
		 * is not difficulty, it is a wall. At one every 3,600 (three Minecraft days, since an
		 * economic tick is 20 game ticks) costing 6%, the same base keeps 156 tonnes and loses 37.
		 * Shocks then cost about a sixth of production: expensive enough to plan around, survivable
		 * enough to plan at all.
		 *
		 * <p>Six deposits of roughly 34 tonnes is about 200 tonnes of ice, which yields close to
		 * <b>one full lunar vehicle's propellant load</b>. A base's whole endowment is one more
		 * Moon rocket — and then the ice is gone.
		 */
		public static Config validation(long seed) {
			return new Config(seed, 6, 40_000.0, 8, 2.5, 60_000.0, 0.02, 3_600, 60);
		}
	}

	/**
	 * The mass audit. {@link #balanced()} is asserted every tick, exactly as warfront's is.
	 *
	 * <p>Tolerance rather than equality because this ledger is in kilograms of a continuous
	 * quantity, not in countable coins. One microgram over a hundred thousand is float noise; one
	 * gram is a bug.
	 */
	public record Ledger(double minedKg, double processedKg, double consumedKg, double lostKg,
	                     double storedKg) {

		/** Everything mined is either still stored, breathed, or lost to vacuum. */
		public double residual() { return minedKg - consumedKg - lostKg - storedKg; }

		/**
		 * Tolerance, RELATIVE to the mass that has moved through the books.
		 *
		 * <p>A fixed 1e-6 kg looked rigorous and was wrong: after 800 tonnes had passed through,
		 * ordinary double rounding exceeded it and the model threw on a leak of one microgram in
		 * eight hundred tonnes. A nanogram per kilogram still catches any real leak - a gram going
		 * missing is a million times this - while ignoring the last bit of a 53-bit mantissa.
		 */
		public double tolerance() { return Math.max(1.0e-6, 1.0e-9 * minedKg); }

		public boolean balanced() { return Math.abs(residual()) < tolerance(); }
	}

	/**
	 * Base viability - the lunar answer to warfront's wealth distribution.
	 *
	 * <p>Warfront asks "who is rich"; a base with one crew asks "can I breathe, and can I leave".
	 * Both are the same question about whether a closed system is net-positive.
	 */
	public record Report(long tick, double icePerDepositKg, double depositsRemaining,
	                     double oxygenKg, double hydroloxKg, double sinterKg,
	                     double oxygenDays, double returnFraction, double lifeSupportShare,
	                     double totalMinedKg, double totalLostKg, double strandedKg,
	                     boolean exhausted) {

		/** Whether the base is producing more air than the crew breathes. */
		public boolean selfSufficient() { return oxygenDays > 1.0; }
	}

	private final Config config;
	private final double[] depositStock;
	private final double[] depositCapacity;
	private final Process[] installationProcess;
	private final int[] installationDeposit;
	private final double[] store = new double[Resource.values().length];

	private double minedKg;
	private double processedKg;
	private double consumedKg;
	private double lostKg;

	/**
	 * Ice buried in the ground and never recovered.
	 *
	 * <p>Deliberately outside the conservation audit: this mass never entered the base's
	 * possession, so counting it would be counting the whole Moon rather than the books.
	 */
	private double strandedKg;

	/**
	 * How much of the available water goes to life support rather than to propellant, 0..1.
	 *
	 * <p>This is warfront's price, wearing lunar clothes. Warfront moves a price when supply and
	 * demand diverge; this moves an allocation when the oxygen reserve diverges from the crew's
	 * needs. Both are a scalar that scarcity pushes around, both are clamped, and both are the
	 * only place the model expresses preference. A base low on air stops making fuel; a base with
	 * air to spare puts everything into leaving.
	 */
	private double lifeSupportShare = 0.5;

	/**
	 * Duty cycle per process, 0..1 — how much of the roster is actually built and running.
	 *
	 * <p>This is the seam between a model and a world. The roster is fixed at construction so a
	 * snapshot always decodes into the same shape, but a player who has placed no electrolyser
	 * should not be producing propellant. The manager sets these from the machines that physically
	 * exist; the headless battery leaves them at 1 and studies the model on its own terms.
	 */
	private final double[] duty = new double[Process.values().length];

	private long tick;
	private long randomState;

	public LunarEconomy(Config config) {
		this.config = config;
		this.randomState = config.seed();

		this.depositStock = new double[config.deposits()];
		this.depositCapacity = new double[config.deposits()];
		for (int i = 0; i < config.deposits(); i++) {
			// Deposits differ in size by a deterministic factor. Where you land matters, and it
			// matters before you can possibly know it does - which is the honest version of siting.
			double factor = 0.4 + (Math.floorMod(mix64(config.seed() ^ (i + 1L) * 0x9E3779B97F4A7C15L),
					1_200L) / 1_000.0);
			depositCapacity[i] = config.depositMassKg() * factor;
			depositStock[i] = depositCapacity[i];
		}

		java.util.Arrays.fill(this.duty, 1.0);

		this.installationProcess = new Process[config.installations()];
		this.installationDeposit = new int[config.installations()];
		for (int i = 0; i < config.installations(); i++) {
			installationProcess[i] = Process.values()[i % Process.values().length];
			installationDeposit[i] = (int) Math.floorMod(mix64(config.seed() + i * 31L),
					config.deposits());
		}
	}

	// ---- the tick -----------------------------------------------------------

	public void advance(long ticks) {
		for (long i = 0; i < ticks; i++) step();
	}

	public void step() {
		tick++;
		mine();
		run();
		breathe();
		sublimate();
		updateAllocation();
		if (config.shockInterval() > 0 && tick % config.shockInterval() == 0) {
			injectShock(Shock.values()[(int) Math.floorMod(nextLong(), Shock.values().length)]);
		}
		assertConservation();
	}

	/** Pull feedstock out of the ground. Ice is finite; regolith is the surface itself. */
	private void mine() {
		for (int i = 0; i < installationProcess.length; i++) {
			if (installationProcess[i] == Process.MELT) {
				int deposit = relocateIfExhausted(i);
				if (deposit < 0) continue;   // every deposit on this base is finished
				double take = Math.min(rate(Process.MELT), depositStock[deposit]);
				take = Math.min(take, space(Resource.ICE));
				if (take <= 0.0) continue;
				depositStock[deposit] -= take;
				store[Resource.ICE.ordinal()] += take;
				minedKg += take;
			} else if (installationProcess[i] == Process.CRACK) {
				int deposit = relocateIfExhausted(i);
				if (deposit < 0) continue;
				double take = Math.min(rate(Process.CRACK), depositStock[deposit]);
				take = Math.min(take, space(Resource.AMMONIA));
				if (take <= 0.0) continue;
				depositStock[deposit] -= take;
				store[Resource.AMMONIA.ordinal()] += take;
				minedKg += take;
			} else if (installationProcess[i] == Process.BAKE) {
				// Regolith is not a deposit. There is no shortage of ground, only of time.
				double take = Math.min(rate(Process.BAKE), space(Resource.REGOLITH));
				if (take <= 0.0) continue;
				store[Resource.REGOLITH.ordinal()] += take;
				minedKg += take;
			}
		}
	}

	/**
	 * Move a mining installation off a dead deposit and onto a live one.
	 *
	 * <p>Without this, a rig pinned to an exhausted crater sits idle while ice remains a hundred
	 * metres away — and, worse, the base reports itself as still producing forever. Warfront can
	 * pin an actor to a node because it has 250 actors across 9 nodes and the coverage comes out
	 * in the wash; a base with two rigs and six craters has no such luxury.
	 *
	 * @return the deposit to mine, or -1 if there is nothing left anywhere
	 */
	private int relocateIfExhausted(int installation) {
		int deposit = installationDeposit[installation];
		if (depositStock[deposit] > 0.0) return deposit;
		// Deterministic search, so a relocation is as replayable as everything else.
		for (int offset = 1; offset <= depositStock.length; offset++) {
			int candidate = (deposit + offset) % depositStock.length;
			if (depositStock[candidate] > 0.0) {
				installationDeposit[installation] = candidate;
				return candidate;
			}
		}
		return -1;
	}

	/** Every installation runs its process. Each conversion balances mass exactly. */
	private void run() {
		for (Process process : installationProcess) {
			double rate = rate(process);
			if (rate <= 0.0) continue;
			switch (process) {
				case MELT -> convert(Resource.ICE, Math.min(rate, store[Resource.ICE.ordinal()]),
						Map.of(Resource.WATER, 1.0));
				case ELECTROLYSE -> {
					// Water splits into hydrogen and oxygen at water's own mass fractions. The
					// allocation decides how much water is spent at all, not how it splits -
					// chemistry is not negotiable, only the throttle is.
					double available = Math.min(rate, store[Resource.WATER.ordinal()]);
					if (available <= 0.0) break;
					convert(Resource.WATER, available, Map.of(
							Resource.HYDROGEN, HYDROGEN_FRACTION,
							Resource.OXYGEN, OXYGEN_FRACTION));
				}
				case MIX -> {
					// Burnable propellant is limited by whichever half runs out first at 6:1.
					double hydrogen = store[Resource.HYDROGEN.ordinal()];
					double oxygen = store[Resource.OXYGEN.ordinal()];
					// Hold back the crew's oxygen: turning the last of the air into fuel is a
					// way to leave that kills you before you go.
					double reserved = oxygen * lifeSupportShare;
					double usableOxygen = Math.max(0.0, oxygen - reserved);
					double fuel = Math.min(hydrogen, usableOxygen / MIXTURE_RATIO);
					fuel = Math.min(fuel, rate / (1.0 + MIXTURE_RATIO));
					if (fuel <= 0.0) break;
					double oxidiser = fuel * MIXTURE_RATIO;
					store[Resource.HYDROGEN.ordinal()] -= fuel;
					store[Resource.OXYGEN.ordinal()] -= oxidiser;
					store[Resource.HYDROLOX.ordinal()] += fuel + oxidiser;
					processedKg += fuel + oxidiser;
				}
				case CRACK -> {
					double feed = Math.min(rate, store[Resource.AMMONIA.ordinal()]);
					if (feed <= 0.0) break;
					convert(Resource.AMMONIA, feed, Map.of(
							Resource.HYDROGEN, AMMONIA_HYDROGEN_FRACTION,
							Resource.NITROGEN, AMMONIA_NITROGEN_FRACTION));
				}
				case BAKE -> {
					double feed = Math.min(rate, store[Resource.REGOLITH.ordinal()]);
					if (feed <= 0.0) break;
					// Regolith gives up a little oxygen; the rest is slag, and slag is a
					// building material. Nothing evaporates - the masses sum to the feed.
					convert(Resource.REGOLITH, feed, Map.of(
							Resource.OXYGEN, REGOLITH_OXYGEN_YIELD,
							Resource.SINTER, 1.0 - REGOLITH_OXYGEN_YIELD));
				}
			}
		}
	}

	/**
	 * Convert one resource into others by mass fraction.
	 *
	 * <p>The fractions must sum to 1. That is not a convention, it is the conservation law: a
	 * process whose outputs weigh more than its inputs is free energy, and free energy is what
	 * this whole model exists to make impossible.
	 */
	private void convert(Resource from, double amount, Map<Resource, Double> into) {
		if (amount <= 0.0) return;
		double sum = 0.0;
		for (double fraction : into.values()) sum += fraction;
		if (Math.abs(sum - 1.0) > 1.0e-9) {
			throw new IllegalStateException("process from " + from + " does not conserve mass: "
					+ "output fractions sum to " + sum);
		}
		double capped = Math.min(amount, store[from.ordinal()]);
		if (capped <= 0.0) return;
		store[from.ordinal()] -= capped;
		for (Map.Entry<Resource, Double> output : into.entrySet()) {
			store[output.getKey().ordinal()] += capped * output.getValue();
		}
		processedKg += capped;
	}

	/** Throughput for a process after the duty cycle the world imposes on it. */
	private double rate(Process process) {
		return config.throughputKgPerTick() * duty[process.ordinal()];
	}

	/** Set how much of a process is actually built and running, 0..1. */
	public void setDuty(Process process, double fraction) {
		duty[process.ordinal()] = clamp(fraction, 0.0, 1.0);
	}

	public double duty(Process process) { return duty[process.ordinal()]; }

	/** The crew breathes. This is the only place oxygen leaves the books as consumed. */
	private void breathe() {
		double need = config.crewOxygenKgPerTick();
		double taken = Math.min(need, store[Resource.OXYGEN.ordinal()]);
		store[Resource.OXYGEN.ordinal()] -= taken;
		consumedKg += taken;
	}

	/** Ticks in a Minecraft day. Boil-off is quoted per day, because that is how it is measured. */
	public static final double TICKS_PER_DAY = 24_000.0;

	/**
	 * Cryogenic boil-off, as a fraction of the stored mass per DAY.
	 *
	 * <p>0.1%/day is the figure real orbital cryogenic stages are designed around. Getting the
	 * unit wrong here was not a rounding error: the first version applied a tenth of a permille
	 * <em>per tick</em>, which is 240% per day, and the ledger duly reported a base losing 70% of
	 * everything it ever mined. The books caught a physics mistake, which is what they are for.
	 */
	public static final double BOIL_OFF_PER_DAY = 0.001;

	/**
	 * Vacuum takes its cut.
	 *
	 * <p>Ice and cryogens are not stable on an airless body in sunlight; they sublimate and boil
	 * off. Small, constant, and the reason a base cannot simply stockpile forever and walk away.
	 */
	private void sublimate() {
		double perTick = BOIL_OFF_PER_DAY / TICKS_PER_DAY;
		for (Resource volatile_ : new Resource[] { Resource.ICE, Resource.HYDROGEN,
				Resource.HYDROLOX }) {
			double loss = store[volatile_.ordinal()] * perTick;
			store[volatile_.ordinal()] -= loss;
			lostKg += loss;
		}
	}

	/**
	 * Move the allocation with scarcity - warfront's price update, in lunar terms.
	 *
	 * <p>Clamped and rate-limited for the same reason theirs is: an allocation that can swing from
	 * 0 to 1 in a tick oscillates instead of converging, and a base that alternates between
	 * suffocating and never leaving is worse than one that does either steadily.
	 */
	private void updateAllocation() {
		double reserveDays = oxygenDays();
		double target;
		if (reserveDays < 1.0) target = 1.0;           // breathing first, always
		else if (reserveDays > 10.0) target = 0.05;    // air is solved; go home
		else target = 1.0 - (reserveDays - 1.0) / 9.0 * 0.95;
		lifeSupportShare = clamp(lifeSupportShare + clamp(target - lifeSupportShare, -0.02, 0.02),
				0.0, 1.0);
	}

	// ---- shocks -------------------------------------------------------------

	/** Apply a shock. Mass leaves the books only through {@code lostKg}, never silently. */
	public void injectShock(Shock shock) {
		switch (shock) {
			case MICROMETEORITE -> {
				// A puncture vents whatever was in the tank it hit.
				Resource hit = Resource.values()[(int) Math.floorMod(nextLong(),
						Resource.values().length)];
				double lost = store[hit.ordinal()] * config.shockPermille() / 1_000.0;
				store[hit.ordinal()] -= lost;
				lostKg += lost;
			}
			case LUNAR_NIGHT -> {
				// Fourteen days without sun. Cryogens boil off hardest because keeping them cold
				// is what the power was for.
				for (Resource cryogen : new Resource[] { Resource.HYDROGEN, Resource.HYDROLOX }) {
					double lost = store[cryogen.ordinal()] * config.shockPermille() * 2.0 / 1_000.0;
					store[cryogen.ordinal()] -= lost;
					lostKg += lost;
				}
			}
			case CRATER_COLLAPSE -> {
				// Part of one crater is buried, permanently - capacity falls, not just stock,
				// because there is no water cycle to refill it.
				//
				// NOTE THE ACCOUNTING. Ice still in the ground was never in the base's books, so
				// burying it must not touch minedKg or lostKg. The first version added the loss
				// to BOTH to keep the ledger balanced, which is exactly the kind of bookkeeping
				// trick that makes a ledger stop being evidence: the sums agreed and the meaning
				// was wrong. Stranded mass gets its own counter and stays out of the audit.
				int deposit = (int) Math.floorMod(nextLong(), depositStock.length);
				double buried = depositStock[deposit] * config.shockPermille() / 1_000.0;
				depositStock[deposit] -= buried;
				depositCapacity[deposit] -= buried;
				strandedKg += buried;
			}
			case SUBLIMATION -> {
				double lost = store[Resource.ICE.ordinal()] * config.shockPermille() / 1_000.0;
				store[Resource.ICE.ordinal()] -= lost;
				lostKg += lost;
			}
		}
	}

	// ---- reporting ----------------------------------------------------------

	public Report report() {
		double remaining = 0.0;
		for (double stock : depositStock) remaining += stock;
		int live = 0;
		for (double capacity : depositCapacity) if (capacity > 0.0) live++;

		return new Report(tick,
				live == 0 ? 0.0 : remaining / live, live,
				store[Resource.OXYGEN.ordinal()], store[Resource.HYDROLOX.ordinal()],
				store[Resource.SINTER.ordinal()],
				oxygenDays(), returnFraction(), lifeSupportShare,
				minedKg, lostKg, strandedKg, remaining <= 0.0);
	}

	public Ledger ledger() {
		double stored = 0.0;
		for (double amount : store) stored += amount;
		return new Ledger(minedKg, processedKg, consumedKg, lostKg, stored);
	}

	/** Days of breathing in the tanks, at 24,000 ticks to a Minecraft day. */
	public double oxygenDays() {
		double perTick = config.crewOxygenKgPerTick();
		if (perTick <= 0.0) return Double.POSITIVE_INFINITY;
		return store[Resource.OXYGEN.ordinal()] / perTick / 24_000.0;
	}

	/**
	 * Progress toward a tank of propellant big enough to matter, 0..1.
	 *
	 * <p>Measured against the lunar lander's own propellant load, because that is the concrete
	 * thing a base is trying to refill. Cosmos does not decide what the number means; the lander
	 * does, and it got it from kinetics.
	 */
	public double returnFraction() {
		return Math.min(1.0, store[Resource.HYDROLOX.ordinal()]
				/ dev.lilkuzco.cosmos.moon.LunarLander.PROPELLANT_KG);
	}

	public double stock(Resource resource) { return store[resource.ordinal()]; }

	/** Ice buried by collapses and never recovered, kg. Outside the audit by design. */
	public double strandedKg() { return strandedKg; }

	public double depositStock(int index) { return depositStock[index]; }

	public long tick() { return tick; }

	public double lifeSupportShare() { return lifeSupportShare; }

	public Map<Resource, Double> stocks() {
		Map<Resource, Double> result = new EnumMap<>(Resource.class);
		for (Resource resource : Resource.values()) result.put(resource, store[resource.ordinal()]);
		return Map.copyOf(result);
	}

	/**
	 * Reconcile the model against what is physically in the world.
	 *
	 * <p>Warfront's {@code setActorGoods} in lunar form, and it exists for the same reason: a
	 * player who mines ice by hand or burns propellant in a rocket has changed the world without
	 * the model's knowledge, and the difference has to enter the books as mined or consumed rather
	 * than appearing from nowhere. This is the seam where a simulation meets a game, and it is
	 * where free energy gets in if nobody is counting.
	 */
	public void reconcile(Resource resource, double actual) {
		double delta = Math.max(0.0, actual) - store[resource.ordinal()];
		if (delta > 0.0) minedKg += delta;
		else consumedKg -= delta;
		store[resource.ordinal()] = Math.max(0.0, actual);
	}

	private double space(Resource resource) {
		return Math.max(0.0, config.storageKg() - store[resource.ordinal()]);
	}

	private void assertConservation() {
		Ledger audit = ledger();
		if (!audit.balanced()) {
			throw new IllegalStateException("lunar mass conservation failed by "
					+ audit.residual() + " kg: " + audit);
		}
	}

	private static double clamp(double value, double low, double high) {
		return value < low ? low : value > high ? high : value;
	}

	// ---- determinism --------------------------------------------------------

	private long nextLong() {
		randomState += 0x9E3779B97F4A7C15L;
		return mix64(randomState);
	}

	private static long mix64(long value) {
		value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
		value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
		return value ^ (value >>> 31);
	}

	// ---- persistence --------------------------------------------------------

	/**
	 * "CLE2" — cosmos lunar economy, version 2.
	 *
	 * <p>Bumped from CLE1 when {@code strandedKg} and the duty cycle joined the format. The first
	 * version did not bump, so an old snapshot decoded misaligned and failed with "deposit count
	 * changed" — a confusing error for a plain version skew. Warfront's magic carries its version
	 * for exactly this reason; a magic number that does not move is not a version check.
	 */
	private static final int MAGIC = 0x434C4532;

	public String encode() {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (DataOutputStream out = new DataOutputStream(bytes)) {
				out.writeInt(MAGIC);
				out.writeLong(config.seed());
				out.writeInt(config.deposits());
				out.writeDouble(config.depositMassKg());
				out.writeInt(config.installations());
				out.writeDouble(config.throughputKgPerTick());
				out.writeDouble(config.storageKg());
				out.writeDouble(config.crewOxygenKgPerTick());
				out.writeInt(config.shockInterval());
				out.writeInt(config.shockPermille());
				out.writeLong(tick);
				out.writeLong(randomState);
				out.writeDouble(minedKg);
				out.writeDouble(processedKg);
				out.writeDouble(consumedKg);
				out.writeDouble(lostKg);
				out.writeDouble(strandedKg);
				out.writeDouble(lifeSupportShare);
				for (double d : duty) out.writeDouble(d);
				for (double amount : store) out.writeDouble(amount);
				out.writeInt(depositStock.length);
				for (int i = 0; i < depositStock.length; i++) {
					out.writeDouble(depositStock[i]);
					out.writeDouble(depositCapacity[i]);
				}
				out.writeInt(installationProcess.length);
				for (int i = 0; i < installationProcess.length; i++) {
					out.writeByte(installationProcess[i].ordinal());
					out.writeInt(installationDeposit[i]);
				}
			}
			return Base64.getEncoder().encodeToString(bytes.toByteArray());
		} catch (IOException impossible) {
			throw new IllegalStateException("could not encode the lunar economy", impossible);
		}
	}

	public static LunarEconomy decode(String encoded) {
		try (DataInputStream in = new DataInputStream(
				new ByteArrayInputStream(Base64.getDecoder().decode(encoded)))) {
			if (in.readInt() != MAGIC) {
				throw new IllegalArgumentException("unknown lunar economy snapshot version");
			}
			Config config = new Config(in.readLong(), in.readInt(), in.readDouble(), in.readInt(),
					in.readDouble(), in.readDouble(), in.readDouble(), in.readInt(), in.readInt());
			LunarEconomy model = new LunarEconomy(config);
			model.tick = in.readLong();
			model.randomState = in.readLong();
			model.minedKg = in.readDouble();
			model.processedKg = in.readDouble();
			model.consumedKg = in.readDouble();
			model.lostKg = in.readDouble();
			model.strandedKg = in.readDouble();
			model.lifeSupportShare = in.readDouble();
			for (int i = 0; i < model.duty.length; i++) model.duty[i] = in.readDouble();
			for (int i = 0; i < model.store.length; i++) model.store[i] = in.readDouble();
			int deposits = in.readInt();
			if (deposits != model.depositStock.length) {
				throw new IllegalArgumentException("lunar deposit count changed");
			}
			for (int i = 0; i < deposits; i++) {
				model.depositStock[i] = in.readDouble();
				model.depositCapacity[i] = in.readDouble();
			}
			int installations = in.readInt();
			if (installations != model.installationProcess.length) {
				throw new IllegalArgumentException("lunar installation count changed");
			}
			for (int i = 0; i < installations; i++) {
				model.installationProcess[i] = Process.values()[in.readUnsignedByte()];
				model.installationDeposit[i] = in.readInt();
			}
			model.assertConservation();
			return model;
		} catch (IOException | IllegalArgumentException exception) {
			throw new IllegalArgumentException("invalid lunar economy snapshot", exception);
		}
	}

	private LunarEconomy() { throw new UnsupportedOperationException(); }
}
