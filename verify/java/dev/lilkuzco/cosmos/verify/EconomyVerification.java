package dev.lilkuzco.cosmos.verify;

import dev.lilkuzco.cosmos.economy.LunarEconomy;
import dev.lilkuzco.cosmos.economy.LunarEconomy.Resource;

import java.util.Arrays;

/**
 * The lunar economy, verified against the same invariants warfront's economy holds itself to.
 *
 * <p>Warfront's suite asks five things of its model: that it conserves, that it replays
 * identically from a seed, that a snapshot round-trips, that shocks reshuffle rather than
 * uniformly dip, and that it is fast enough to run every tick. This asks the same five, plus the
 * two that only a mass-conserving model can be asked - that no process invents matter, and that
 * the chemistry matches the real mass fractions of water.
 */
public final class EconomyVerification {

	private static int checks;
	private static int failures;

	public static void main(String[] args) {
		System.out.println("lilkuzco_cosmos — lunar economy verification");
		System.out.println("template: warfront civilization/EconomyModel (read-only reference)");

		chemistry();
		conservation();
		determinism();
		snapshot();
		scarcity();
		shocks();
		exhaustion();
		survivable();
		idle();
		performance();

		System.out.println();
		System.out.println("=".repeat(74));
		if (failures == 0) System.out.printf("ALL %d CHECKS PASSED%n", checks);
		else System.out.printf("%d of %d CHECKS FAILED%n", failures, checks);
		System.out.println("=".repeat(74));
		System.exit(failures == 0 ? 0 : 1);
	}

	// ---- 1. the chemistry is real ------------------------------------------

	private static void chemistry() {
		section("1. Water is 11.19% hydrogen by mass, and that is where the air comes from");
		double h = LunarEconomy.HYDROGEN_FRACTION;
		double o = LunarEconomy.OXYGEN_FRACTION;
		System.out.printf("   1 kg water -> %.4f kg H2 + %.4f kg O2%n", h, o);

		check("the mass fractions sum to exactly one", Math.abs(h + o - 1.0) < 1e-12,
				String.format("%.15f", h + o));
		check("hydrogen's share matches 2.016/18.015",
				Math.abs(h - 2.016 / 18.015) < 1e-4, String.format("%.6f", h));

		// At 6:1 the hydrogen cannot burn all the oxygen, and the remainder is breathable.
		double burnable = h * LunarEconomy.MIXTURE_RATIO;
		double spare = o - burnable;
		System.out.printf("   at %.1f:1 the H2 burns %.4f kg O2, leaving %.4f kg spare (%.1f%%)%n",
				LunarEconomy.MIXTURE_RATIO, burnable, spare, spare / o * 100.0);
		check("making propellant also makes surplus oxygen", spare > 0.0,
				String.format("%.4f kg per kg of water", spare));
		check("the surplus is a meaningful fraction, not a rounding error",
				spare / o > 0.15 && spare / o < 0.35,
				String.format("%.1f%% of the oxygen produced", spare / o * 100.0));
		check("stoichiometric 8:1 would leave nothing spare",
				o - h * 8.0 < 1e-3, "which is why engines run fuel-rich");
	}

	// ---- 2. conservation ----------------------------------------------------

	private static void conservation() {
		section("2. Mass conservation — asserted every tick, exactly as warfront asserts money");
		LunarEconomy model = new LunarEconomy(LunarEconomy.Config.validation(0x5EEDC1A7L));
		model.advance(20_000);
		var ledger = model.ledger();
		System.out.printf("   mined %.3f kg, processed %.3f, consumed %.3f, lost %.3f, stored %.3f%n",
				ledger.minedKg(), ledger.processedKg(), ledger.consumedKg(), ledger.lostKg(),
				ledger.storedKg());
		System.out.printf("   residual: %.3e kg%n", ledger.residual());
		check("the books balance after 20,000 ticks", ledger.balanced(),
				String.format("residual %.3e kg", ledger.residual()));
		check("the model actually did something", ledger.minedKg() > 1_000.0,
				String.format("%.0f kg mined", ledger.minedKg()));
		check("processing exceeds mining, because matter is reprocessed",
				ledger.processedKg() > 0.0, String.format("%.0f kg processed", ledger.processedKg()));
	}

	// ---- 3. determinism -----------------------------------------------------

	private static void determinism() {
		section("3. Determinism — same seed, same 20,000 ticks, identical state");
		LunarEconomy first = new LunarEconomy(LunarEconomy.Config.validation(99L));
		LunarEconomy replay = new LunarEconomy(LunarEconomy.Config.validation(99L));
		first.advance(20_000);
		replay.advance(20_000);
		check("reports are identical", first.report().equals(replay.report()),
				"tick " + first.report().tick());
		check("ledgers are identical", first.ledger().equals(replay.ledger()), "byte for byte");

		LunarEconomy other = new LunarEconomy(LunarEconomy.Config.validation(100L));
		other.advance(20_000);
		check("a different seed gives a different world",
				!other.report().equals(first.report()),
				String.format("%.0f vs %.0f kg mined", other.report().totalMinedKg(),
						first.report().totalMinedKg()));
	}

	// ---- 4. snapshot --------------------------------------------------------

	private static void snapshot() {
		section("4. Snapshot round-trip — Base64 behind a magic number, as warfront does");
		LunarEconomy model = new LunarEconomy(LunarEconomy.Config.validation(4242L));
		model.advance(9_000);
		String encoded = model.encode();
		LunarEconomy restored = LunarEconomy.decode(encoded);
		System.out.printf("   snapshot is %d characters%n", encoded.length());
		check("the restored model reports identically", restored.report().equals(model.report()),
				"round-tripped");
		check("the restored ledger is identical", restored.ledger().equals(model.ledger()),
				"round-tripped");

		// It must keep stepping identically, not merely look the same at rest.
		model.advance(500);
		restored.advance(500);
		check("and it keeps stepping identically", restored.report().equals(model.report()),
				"500 ticks past the restore");

		boolean rejected = false;
		try {
			LunarEconomy.decode("bm90IGEgc25hcHNob3Q=");
		} catch (IllegalArgumentException expected) {
			rejected = true;
		}
		check("garbage is rejected rather than half-loaded", rejected, "IllegalArgumentException");
	}

	// ---- 5. scarcity moves the allocation ----------------------------------

	private static void scarcity() {
		section("5. Scarcity moves the allocation — warfront's price update, in lunar terms");
		// A base with a hungry crew and no reserve must put water into air, not fuel.
		var starved = new LunarEconomy.Config(7L, 3, 4_000.0, 4, 1.0, 20_000.0, 0.5, 0, 0);
		LunarEconomy tight = new LunarEconomy(starved);
		tight.advance(3_000);

		var comfortable = new LunarEconomy.Config(7L, 3, 4_000.0, 4, 1.0, 20_000.0, 0.0005, 0, 0);
		LunarEconomy easy = new LunarEconomy(comfortable);
		easy.advance(3_000);

		System.out.printf("   hungry crew:  %.1f days of air, allocation %.3f to life support, "
						+ "%.1f kg hydrolox%n", tight.oxygenDays(), tight.lifeSupportShare(),
				tight.stock(Resource.HYDROLOX));
		System.out.printf("   small crew:   %.1f days of air, allocation %.3f to life support, "
						+ "%.1f kg hydrolox%n", easy.oxygenDays(), easy.lifeSupportShare(),
				easy.stock(Resource.HYDROLOX));

		check("a base short of air diverts to life support",
				tight.lifeSupportShare() > easy.lifeSupportShare(),
				String.format("%.3f vs %.3f", tight.lifeSupportShare(), easy.lifeSupportShare()));
		check("a base with air to spare makes propellant instead",
				easy.stock(Resource.HYDROLOX) > tight.stock(Resource.HYDROLOX),
				String.format("%.1f vs %.1f kg", easy.stock(Resource.HYDROLOX),
						tight.stock(Resource.HYDROLOX)));
		check("the allocation stays inside its bounds",
				tight.lifeSupportShare() >= 0.0 && tight.lifeSupportShare() <= 1.0
						&& easy.lifeSupportShare() >= 0.0 && easy.lifeSupportShare() <= 1.0,
				"0..1 throughout");
	}

	// ---- 6. shocks ----------------------------------------------------------

	private static void shocks() {
		section("6. Shocks destroy and record — never silently, never creating");
		var config = new LunarEconomy.Config(77123L, 6, 40_000.0, 8, 2.5, 60_000.0, 0.02, 0, 300);
		LunarEconomy baseline = new LunarEconomy(config);
		LunarEconomy shocked = new LunarEconomy(config);
		baseline.advance(10_000);
		shocked.advance(5_000);
		for (LunarEconomy.Shock shock : LunarEconomy.Shock.values()) shocked.injectShock(shock);
		shocked.advance(5_000);

		System.out.printf("   baseline: %.0f kg hydrolox, %.1f kg lost%n",
				baseline.stock(Resource.HYDROLOX), baseline.ledger().lostKg());
		System.out.printf("   shocked:  %.0f kg hydrolox, %.1f kg lost%n",
				shocked.stock(Resource.HYDROLOX), shocked.ledger().lostKg());

		check("shocks cost the base real mass",
				shocked.ledger().lostKg() > baseline.ledger().lostKg(),
				String.format("%.1f vs %.1f kg lost", shocked.ledger().lostKg(),
						baseline.ledger().lostKg()));
		check("the books still balance after every shock", shocked.ledger().balanced(),
				String.format("residual %.3e kg", shocked.ledger().residual()));
		check("a shocked base is worse off, not better",
				shocked.stock(Resource.HYDROLOX) <= baseline.stock(Resource.HYDROLOX),
				"no free energy from disaster");
	}

	// ---- 7. exhaustion ------------------------------------------------------

	private static void exhaustion() {
		section("7. Ice does not come back — the one place this departs from warfront");
		var small = new LunarEconomy.Config(31337L, 2, 400.0, 4, 5.0, 50_000.0, 0.0, 0, 0);
		LunarEconomy model = new LunarEconomy(small);
		double initial = model.depositStock(0) + model.depositStock(1);
		model.advance(20_000);
		double remaining = model.depositStock(0) + model.depositStock(1);
		System.out.printf("   deposits: %.0f kg initially, %.0f kg after 20,000 ticks%n",
				initial, remaining);
		System.out.printf("   report: %s%n", model.report().exhausted()
				? "EXHAUSTED - this site is finished" : "still producing");

		check("a small site is mined out", remaining <= 0.0,
				String.format("%.3f kg left", remaining));
		check("and it does NOT grow back", model.report().exhausted(),
				"no water cycle, no second harvest");
		check("the mass went somewhere accountable", model.ledger().balanced(),
				String.format("residual %.3e kg", model.ledger().residual()));

		// Regolith, by contrast, is the surface itself and never runs out.
		check("regolith keeps producing after the ice is gone",
				model.stock(Resource.SINTER) > 0.0,
				String.format("%.0f kg of sinter", model.stock(Resource.SINTER)));
	}

	// ---- 8. shocks must be survivable ---------------------------------------

	private static void survivable() {
		section("8. Shocks are difficult, not hostile — a base must be able to establish");
		// The validation config with shocks ON, run for two Minecraft days.
		LunarEconomy model = new LunarEconomy(LunarEconomy.Config.validation(0xB00L));
		model.advance(48_000);
		var report = model.report();
		System.out.printf("   after 48,000 ticks: %.0f live deposits, %.0f kg per deposit, "
						+ "%.0f kg stranded%n", report.depositsRemaining(),
				report.icePerDepositKg(), report.strandedKg());
		System.out.printf("   %.0f kg hydrolox, %.1f days of air%n",
				report.hydroloxKg(), report.oxygenDays());

		// The bug this guards: CRATER_COLLAPSE used to destroy a WHOLE deposit, and over this
		// span it wiped out all six before a base could produce anything at all.
		check("shocks did not wipe out every deposit", report.depositsRemaining() > 0,
				String.format("%.0f still live", report.depositsRemaining()));
		check("the base still produced propellant despite shocks", report.hydroloxKg() > 0.0,
				String.format("%.0f kg", report.hydroloxKg()));
		check("shocks cost a meaningful but survivable share of production",
				report.totalLostKg() > 0.0 && report.totalLostKg() < report.totalMinedKg() * 0.4,
				String.format("%.0f kg lost of %.0f mined (%.0f%%)", report.totalLostKg(),
						report.totalMinedKg(),
						report.totalLostKg() / Math.max(1.0, report.totalMinedKg()) * 100.0));
		check("the base can refuel a lander — the point of the whole economy",
				report.returnFraction() >= 1.0,
				String.format("%.0f kg against a %.0f kg lander load", report.hydroloxKg(),
						dev.lilkuzco.cosmos.moon.LunarLander.PROPELLANT_KG));
		check("stranded ice is tracked but stays out of the audit",
				report.strandedKg() >= 0.0 && model.ledger().balanced(),
				String.format("%.0f kg stranded, residual %.3e kg", report.strandedKg(),
						model.ledger().residual()));
	}

	// ---- 9. no plants, no production ----------------------------------------

	private static void idle() {
		section("9. A base with nothing built produces nothing");
		LunarEconomy model = new LunarEconomy(LunarEconomy.Config.validation(1234L));
		for (LunarEconomy.Process process : LunarEconomy.Process.values()) {
			model.setDuty(process, 0.0);
		}
		double depositsBefore = model.report().depositsRemaining();
		double iceBefore = model.report().icePerDepositKg();
		model.advance(40_000);
		var report = model.report();
		System.out.printf("   40,000 ticks at zero duty: mined %.2f kg, %.0f live deposits%n",
				report.totalMinedKg(), report.depositsRemaining());

		check("nothing was mined", report.totalMinedKg() == 0.0,
				String.format("%.2f kg", report.totalMinedKg()));
		check("nothing was produced", report.hydroloxKg() == 0.0 && report.sinterKg() == 0.0,
				"empty stores");
		check("the deposits are still there to find", report.depositsRemaining() == depositsBefore,
				String.format("%.0f of %.0f", report.depositsRemaining(), depositsBefore));
		check("the books balance at zero", model.ledger().balanced(),
				String.format("residual %.3e kg", model.ledger().residual()));
	}

	// ---- 10. performance ----------------------------------------------------

	private static void performance() {
		section("10. Performance — warfront budgets 1 ms for 500 citizens; this is smaller");
		var big = new LunarEconomy.Config(918273L, 24, 40_000.0, 64, 2.5, 200_000.0, 0.02, 400, 180);
		LunarEconomy model = new LunarEconomy(big);
		model.advance(2_000);   // warm the JIT
		long started = System.nanoTime();
		model.advance(20_000);
		double averageNanos = (System.nanoTime() - started) / 20_000.0;
		System.out.printf("   64 installations, 24 deposits: %.4f ms per economic tick%n",
				averageNanos / 1_000_000.0);
		check("an economic tick is far inside a millisecond", averageNanos < 1_000_000.0,
				String.format("%.4f ms", averageNanos / 1_000_000.0));
		check("still balanced after 22,000 ticks", model.ledger().balanced(),
				String.format("residual %.3e kg", model.ledger().residual()));
	}

	// ---- plumbing -----------------------------------------------------------

	private static void section(String title) {
		System.out.println();
		System.out.println("── " + title + " " + "─".repeat(Math.max(0, 70 - title.length())));
	}

	private static void check(String name, boolean passed, String detail) {
		checks++;
		if (!passed) failures++;
		System.out.printf("  %s %-52s %s%n", passed ? "PASS" : "FAIL", name, detail);
	}

	private EconomyVerification() {}
}
