package dev.lilkuzco.cosmos.propellant;

import java.util.List;

/**
 * The propellant ladder without its fluid tags, for the headless verifier.
 *
 * <p>{@link Propellants} in the main source set carries the same three grades plus the Minecraft
 * fluid tags that fill them; the tags are what pull in a server. The FIGURES here are the ones
 * that matter to physics and they are identical - if these two ever disagree, the verifier is
 * measuring a rocket nobody can fly, so {@code assertMatchesShipped} exists to say so.
 */
public final class Propellants {

	public static final Propellant CRUDE =
			new Propellant("cosmos:crude", 210.0, 232.0, 1000, "cosmos.propellant.crude");
	public static final Propellant KEROSENE =
			new Propellant("cosmos:kerosene", 283.0, 311.0, 1000, "cosmos.propellant.kerosene");
	public static final Propellant CRYOGENIC =
			new Propellant("cosmos:cryogenic", 380.0, 450.0, 1000, "cosmos.propellant.cryogenic");

	public static final List<Propellant> LADDER = List.of(CRUDE, KEROSENE, CRYOGENIC);

	public static Propellant byId(String id) {
		for (Propellant p : LADDER) if (p.id().equals(id)) return p;
		return null;
	}

	private Propellants() {
	}
}
