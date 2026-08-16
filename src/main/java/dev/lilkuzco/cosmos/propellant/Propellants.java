package dev.lilkuzco.cosmos.propellant;

import dev.lilkuzco.cosmos.Cosmos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;

import java.util.List;
import java.util.Map;

/**
 * The propellant ladder, lowest grade first.
 *
 * <p>Three grades ship. Only the first has any fluid in it today, and that is the point: the
 * ladder is the contract, and crude_empire fills its rungs as its refinery arrives. A tank of
 * something better is not a different code path, it is a higher Isp in the rocket's kinetics
 * profile - which is the only place performance is ever decided.
 *
 * <p>Isp values are real, and chosen so the ladder means something. Going from crude to
 * refined kerosene is a 22% gain in exhaust velocity, which through Tsiolkovsky's logarithm is
 * the difference between a two-stage vehicle and a much larger one.
 */
public final class Propellants {

    /**
     * Grade 1 - raw crude. What crude_empire actually ships today.
     *
     * <p>Isp 210/232 s is deliberately poor: unrefined hydrocarbon burned with an oxidiser is a
     * genuinely bad rocket propellant, and a vehicle flying on it needs to be enormous. That is
     * the intended early-game experience, and it is why the refinery is worth building.
     */
    public static final Propellant CRUDE = new Propellant(
            "cosmos:crude",
            210.0, 232.0, 1000,
            "cosmos.propellant.crude");

    /**
     * Grade 2 - refined kerosene. The campaign's "diesel-tier early rockets".
     *
     * <p><b>Empty until crude_empire ships its refinery.</b> The tag exists, the grade exists,
     * and the moment a fluid is tagged into it every launch pad in the world accepts it.
     */
    public static final Propellant KEROSENE = new Propellant(
            "cosmos:kerosene",
            283.0, 311.0, 1000,
            "cosmos.propellant.kerosene");

    /**
     * Grade 3 - cryogenic. The hook the brief asks for: "a future high-Isp fuel, reflected as
     * higher Isp in the rocket profile". Hydrolox figures, empty tag, no code waiting on it.
     */
    public static final Propellant CRYOGENIC = new Propellant(
            "cosmos:cryogenic",
            380.0, 450.0, 1000,
            "cosmos.propellant.cryogenic");

    /** Lowest grade first. Tank matching walks this in reverse so the best grade present wins. */
    public static final List<Propellant> LADDER = List.of(CRUDE, KEROSENE, CRYOGENIC);

    /**
     * The fluid tag that fills each grade. This is the only Minecraft-facing part of the
     * propellant system, kept apart from {@link Propellant} so the grades themselves stay
     * headlessly testable.
     */
    private static final Map<String, TagKey<Fluid>> TAGS = Map.of(
            CRUDE.id(), tag("propellant/crude"),
            KEROSENE.id(), tag("propellant/kerosene"),
            CRYOGENIC.id(), tag("propellant/cryogenic"));

    public static TagKey<Fluid> tagFor(Propellant grade) { return TAGS.get(grade.id()); }

    /** Whether a fluid counts as a grade. */
    public static boolean accepts(Propellant grade, Fluid fluid) {
        TagKey<Fluid> tag = TAGS.get(grade.id());
        return tag != null && fluid.builtInRegistryHolder().is(tag);
    }

    /** The grade a fluid belongs to, or null. Best grade wins if a fluid is in several. */
    public static Propellant match(Fluid fluid) {
        for (int i = LADDER.size() - 1; i >= 0; i--) {
            if (accepts(LADDER.get(i), fluid)) return LADDER.get(i);
        }
        return null;
    }

    public static Propellant byId(String id) {
        for (Propellant p : LADDER) if (p.id().equals(id)) return p;
        return null;
    }

    private static TagKey<Fluid> tag(String path) {
        return TagKey.create(Registries.FLUID, Cosmos.id(path));
    }

    private Propellants() {}
}
