package dev.lilkuzco.cosmos.pad;

import dev.lilkuzco.cosmos.propellant.Propellant;
import dev.lilkuzco.cosmos.propellant.Propellants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The launch pad's propellant reservoir, as a Fabric fluid storage.
 *
 * <p><b>This is how propellant gets into a rocket, and Phase A shipped without it.</b> The pad had
 * an {@code acceptFuel} method and nothing in the world ever called it: no bucket path, no pipe
 * path, no hopper. Every pad ever placed sat at NO_FUEL forever. The physics verification could
 * not catch that - it computed delta-v from a fuel mass handed to it directly, which is exactly
 * the flight the pad could never actually assemble.
 *
 * <p>Being a {@code Storage} rather than a private integer is what makes the fuelling scale. A
 * sounding rocket is a few buckets poured by hand; the lunar vehicle wants 1,428 of them, which is
 * not a right-click chore, it is a pipeline. crude_empire's oil pipes already speak this API, so
 * they fill a launch pad with no code in either mod naming the other.
 *
 * <p>Grades are enforced here rather than checked later: a fluid outside the propellant tags is
 * refused, and so is a second grade on top of a first. Half crude and half kerosene has no honest
 * specific impulse, and averaging them would either flatter or cheat the player.
 */
public class PropellantTank extends SingleVariantStorage<FluidVariant> {

	/** Droplets per millibucket. Fabric counts in droplets; the pad's UI counts in millibuckets. */
	public static final long DROPLETS_PER_MB = FluidConstants.BUCKET / 1000L;

	private final LaunchPadBlockEntity pad;

	public PropellantTank(LaunchPadBlockEntity pad) {
		this.pad = pad;
	}

	@Override
	protected FluidVariant getBlankVariant() {
		return FluidVariant.blank();
	}

	@Override
	protected long getCapacity(FluidVariant ignored) {
		return pad.capacityMb() * DROPLETS_PER_MB;
	}

	@Override
	protected boolean canInsert(FluidVariant variant) {
		// A countdown is a commitment. Topping up a vehicle mid-count would change the flight the
		// player agreed to, and the pad already aborts when that happens - better to refuse.
		if (pad.countdown() >= 0) return false;
		return gradeOf(variant) != null;
	}

	@Override
	protected void onFinalCommit() {
		pad.setChanged();
	}

	/** Contents in millibuckets, which is what the readout and the mass conversion use. */
	public int millibuckets() {
		return (int) (amount / DROPLETS_PER_MB);
	}

	/** The grade in the tank, or the lowest grade when empty so the readout has something to say. */
	public Propellant grade() {
		Propellant g = gradeOf(variant);
		return g == null ? Propellants.CRUDE : g;
	}

	public boolean isEmpty() {
		return amount <= 0 || variant.isBlank();
	}

	public void empty() {
		variant = FluidVariant.blank();
		amount = 0L;
		pad.setChanged();
	}

	/**
	 * Burn what the vehicle actually loaded, and leave the rest in the tanks.
	 *
	 * <p>Ignition used to call {@link #empty()}, which <b>annihilated the surplus</b>. A pad ringed
	 * for a Moon rocket holds 1,040 buckets; an orbital vehicle takes 172 of them, so launching one
	 * from a big pad destroyed 868 buckets of propellant with no message and no way to notice
	 * except by counting. The reservoir belongs to the pad, not to the flight.
	 */
	public void consume(double kilograms, double kgPerBucket) {
		if (kilograms <= 0.0 || kgPerBucket <= 0.0) return;
		long droplets = (long) Math.ceil(kilograms / kgPerBucket * 1000.0) * DROPLETS_PER_MB;
		amount = Math.max(0L, amount - droplets);
		if (amount == 0L) variant = FluidVariant.blank();
		pad.setChanged();
	}

	/**
	 * Which propellant grade a fluid counts as, or null if it is not propellant.
	 *
	 * <p>Walked best-grade-first so a fluid tagged into two rungs is credited at the higher one.
	 * Cosmos never names another mod's fluid - the tags do, and an absent mod leaves a rung empty
	 * rather than breaking the load.
	 */
	public static Propellant gradeOf(FluidVariant variant) {
		if (variant == null || variant.isBlank()) return null;
		var state = variant.getFluid().defaultFluidState();
		for (int i = Propellants.LADDER.size() - 1; i >= 0; i--) {
			Propellant grade = Propellants.LADDER.get(i);
			var tag = Propellants.tagFor(grade);
			if (tag != null && state.is(tag)) return grade;
		}
		return null;
	}

	public void save(ValueOutput output) {
		output.store("propellant_fluid", FluidVariant.CODEC, variant);
		output.putLong("propellant_droplets", amount);
	}

	public void load(ValueInput input) {
		variant = input.read("propellant_fluid", FluidVariant.CODEC).orElse(FluidVariant.blank());
		amount = input.getLongOr("propellant_droplets", 0L);
		if (variant.isBlank()) amount = 0L;
	}
}
