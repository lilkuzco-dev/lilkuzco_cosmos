package dev.lilkuzco.cosmos.isru;

import dev.lilkuzco.cosmos.CosmosBlockEntities;
import dev.lilkuzco.cosmos.CosmosBlocks;
import dev.lilkuzco.cosmos.CosmosItems;
import dev.lilkuzco.cosmos.economy.LunarEconomy;
import dev.lilkuzco.cosmos.economy.LunarEconomyManager;
import dev.lilkuzco.cosmos.life.LifeSupport;
import dev.lilkuzco.cosmos.moon.MoonDimension;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The working end of an ISRU plant.
 *
 * <p>It computes nothing. Production lives in {@link LunarEconomy}, which is pure, deterministic
 * and mass-conserving; this block reads that model's stores and hands them to the world — which is
 * the same arrangement cosmos already has with kinetics, for the same reason. There is no place
 * here for a yield, a rate or a conversion, because the block has no say in them.
 *
 * <p>Two rules of siting, and both are physical:
 *
 * <ul>
 *   <li>An <b>electrolyser</b> only runs in a polar biome, because that is where the ice is. Put
 *       one on a mare and it sits dark and says so.</li>
 *   <li>A <b>kiln</b> runs anywhere on the Moon, because the feedstock is the ground it stands on.
 *       It is the worse deal and the one that always works.</li>
 * </ul>
 */
public class IsruBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity {

	/** How close a plant vents breathable oxygen to. Same reach as an oxygen station. */
	public static final double OXYGEN_RANGE = 8.0;

	/** Kilograms of sinter in one item. A block of sintered regolith is not light. */
	public static final double SINTER_KG_PER_ITEM = 20.0;

	private final IsruRegistry.Kind kind;

	public IsruBlockEntity(BlockPos pos, BlockState state, IsruRegistry.Kind kind) {
		super(CosmosBlockEntities.ISRU, pos, state);
		this.kind = kind;
	}

	public IsruBlockEntity(BlockPos pos, BlockState state) {
		this(pos, state, state.getBlock() instanceof IsruBlock isru
				? isru.kind() : IsruRegistry.Kind.ELECTROLYSER);
	}

	public IsruRegistry.Kind kind() {
		return kind;
	}

	/** Whether this plant can run where it stands. Asked of the block, so there is one rule. */
	public boolean sited(ServerLevel level) {
		return getBlockState().getBlock() instanceof IsruBlock isru
				&& isru.sited(level, worldPosition);
	}

	/**
	 * The propellant tap: an extract-only fluid storage backed by the economy's hydrolox store.
	 *
	 * <p>Extract-only on purpose. You cannot pour propellant back into an electrolyser, and a tap
	 * that accepted insertions would let a player launder any tagged fluid into hydrolox. What
	 * comes out is charged against the model, so a bucket taken here is a bucket the base no
	 * longer has - the same seam, in the same direction, as every other withdrawal.
	 */
	public final class HydroloxTap implements
			net.fabricmc.fabric.api.transfer.v1.storage.Storage<
					net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant> {

		@Override
		public boolean supportsInsertion() {
			return false;
		}

		@Override
		public long insert(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant variant,
		                   long maxAmount,
		                   net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext tx) {
			return 0L;
		}

		@Override
		public long extract(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant variant,
		                    long maxAmount,
		                    net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext tx) {
			if (level == null || level.isClientSide()
					|| !variant.isOf(dev.lilkuzco.cosmos.CosmosFluids.HYDROLOX)) {
				return 0L;
			}
			double wantKg = maxAmount / (double) dev.lilkuzco.cosmos.pad.PropellantTank.DROPLETS_PER_MB
					/ 1000.0 * dev.lilkuzco.cosmos.pad.LaunchPadBlockEntity.KG_PER_BUCKET;
			double gotKg = LunarEconomyManager.withdraw(level.getServer(),
					LunarEconomy.Resource.HYDROLOX, wantKg);
			if (gotKg <= 0.0) return 0L;
			return (long) (gotKg / dev.lilkuzco.cosmos.pad.LaunchPadBlockEntity.KG_PER_BUCKET
					* 1000.0 * dev.lilkuzco.cosmos.pad.PropellantTank.DROPLETS_PER_MB);
		}

		@Override
		public java.util.Iterator<net.fabricmc.fabric.api.transfer.v1.storage.StorageView<
				net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant>> iterator() {
			return java.util.Collections.emptyIterator();
		}
	}

	private final HydroloxTap tap = new HydroloxTap();

	public HydroloxTap tap() {
		return tap;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state,
	                              IsruBlockEntity plant) {
		if (!(level instanceof ServerLevel server)) return;

		boolean live = plant.sited(server);
		if (state.getValue(IsruBlock.LIT) != live) {
			server.setBlock(pos, state.setValue(IsruBlock.LIT, live), Block.UPDATE_ALL);
		}
		if (!live) return;

		// Vent breathable oxygen to anyone nearby. The plant does not decide how much oxygen
		// exists - it withdraws from the model's store, so air given to a suit is air the base
		// no longer has.
		if (server.getGameTime() % 20 == 0) plant.ventOxygen(server);
	}

	private void ventOxygen(ServerLevel level) {
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
					worldPosition.getZ() + 0.5) > OXYGEN_RANGE * OXYGEN_RANGE) {
				continue;
			}
			ItemStack suit = LifeSupport.wornSuit(player);
			if (suit.isEmpty()) continue;
			int missing = suit.getMaxDamage() - LifeSupport.oxygenOf(suit);
			if (missing <= 0) continue;

			// One kilogram of oxygen is a lot of breathing. Charge the model for what it gives.
			double kg = LunarEconomyManager.withdraw(level.getServer(),
					LunarEconomy.Resource.OXYGEN, 0.02);
			if (kg <= 0.0) continue;
			LifeSupport.refill(suit, (int) Math.round(kg * 20_000.0));
		}
	}

	/** Right-click: say what the plant is doing, and hand over what it has made. */
	public void interact(ServerPlayer player) {
		ServerLevel level = player.level();
		if (!MoonDimension.isMoon(level)) {
			player.sendSystemMessage(Component.translatable("cosmos.isru.wrong_world"));
			return;
		}
		if (!sited(level)) {
			player.sendSystemMessage(Component.translatable("cosmos.isru.bad_site"));
			return;
		}

		var report = LunarEconomyManager.report(level.getServer());
		player.sendSystemMessage(Component.translatable("cosmos.isru.status",
				String.format("%.1f", report.oxygenDays()),
				String.format("%.0f", report.hydroloxKg()),
				String.format("%.0f", report.returnFraction() * 100.0)));

		if (kind == IsruRegistry.Kind.KILN) {
			// Hand over sinter, charged against the model so the mass leaves the books.
			double taken = LunarEconomyManager.withdraw(level.getServer(),
					LunarEconomy.Resource.SINTER, SINTER_KG_PER_ITEM * 16);
			int items = (int) Math.floor(taken / SINTER_KG_PER_ITEM);
			if (items > 0) {
				player.getInventory().placeItemBackInInventory(
						new ItemStack(CosmosBlocks.SINTERED_REGOLITH.asItem(), items));
				player.sendSystemMessage(Component.translatable("cosmos.isru.collected", items));
			} else {
				player.sendSystemMessage(Component.translatable("cosmos.isru.nothing_yet"));
			}
		}
	}

	// NOTE: registration deliberately does NOT live here. BlockEntity.setRemoved() fires on chunk
	// unload as well as on breaking the block, so unregistering here would quietly shut a base
	// down every time a player walked away from it. IsruBlock handles placement and removal, both
	// of which happen exactly once.

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
	}
}
