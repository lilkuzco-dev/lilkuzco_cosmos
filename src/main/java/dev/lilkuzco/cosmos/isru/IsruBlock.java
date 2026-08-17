package dev.lilkuzco.cosmos.isru;

import com.mojang.serialization.MapCodec;
import dev.lilkuzco.cosmos.CosmosBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * An in-situ resource plant: an electrolyser or a regolith kiln.
 *
 * <p>Both are the same block class because they are the same idea — a machine that turns the Moon
 * into something a base needs — and they differ only in which process they drive and where they
 * will run. {@code LIT} tracks whether the plant is actually producing, so a player can see at a
 * glance whether siting it there worked.
 */
public class IsruBlock extends BaseEntityBlock {

	public static final MapCodec<IsruBlock> CODEC = simpleCodec(
			properties -> new IsruBlock(properties, IsruRegistry.Kind.ELECTROLYSER));
	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	private final IsruRegistry.Kind kind;

	public IsruBlock(BlockBehaviour.Properties properties, IsruRegistry.Kind kind) {
		super(properties);
		this.kind = kind;
		registerDefaultState(stateDefinition.any().setValue(LIT, Boolean.FALSE));
	}

	public IsruRegistry.Kind kind() {
		return kind;
	}

	@Override
	protected MapCodec<? extends IsruBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LIT);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new IsruBlockEntity(pos, state, kind);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
			Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide()) return null;
		return createTickerHelper(type, CosmosBlockEntities.ISRU, IsruBlockEntity::serverTick);
	}

	/**
	 * Placement and removal are where the roster changes, and they happen exactly once each.
	 *
	 * <p>Not the block entity's {@code setRemoved}, which also fires on chunk unload — using that
	 * meant a base stopped producing the moment a player walked away from it.
	 */
	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous,
	                       boolean moving) {
		super.onPlace(state, level, pos, previous, moving);
		// Only a plant that can actually RUN counts toward the base's output. An electrolyser
		// standing on a mare produces nothing, says so, and must not quietly contribute a quarter
		// of the duty cycle from a site with no ice under it.
		if (level instanceof net.minecraft.server.level.ServerLevel server && sited(server, pos)) {
			IsruRegistry.add(kind, pos, server.getServer());
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state,
	                                           net.minecraft.server.level.ServerLevel level,
	                                           BlockPos pos, boolean moving) {
		super.affectNeighborsAfterRemoval(state, level, pos, moving);
		IsruRegistry.remove(kind, pos, level.getServer());
	}

	/**
	 * Whether this kind of plant can run at this position.
	 *
	 * <p>On the block rather than the block entity, because siting has to be decided at placement
	 * — before there is a ticking block entity to ask, and without loading one.
	 */
	public boolean sited(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
		return switch (kind) {
			// The ice is at the poles, and nowhere else.
			case ELECTROLYSER -> level.dimension().equals(
					dev.lilkuzco.cosmos.world.CosmosWorlds.MOON)
					&& level.getBiome(pos).is(dev.lilkuzco.cosmos.world.CosmosWorlds.LUNAR_POLAR);
			// The ground is everywhere, which is the kiln's whole appeal.
			case KILN -> level.dimension().equals(dev.lilkuzco.cosmos.world.CosmosWorlds.MOON);
			// The ammonia is on the shelf, and only on the outer moon.
			case CRACKER -> level.dimension().equals(
					dev.lilkuzco.cosmos.world.CosmosWorlds.HAZE)
					&& level.getBiome(pos).is(
							dev.lilkuzco.cosmos.world.CosmosWorlds.AMMONIA_SHELF);
		};
	}

	/** Right-click for a status line, and to take whatever the plant has made. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
	                                           Player player, BlockHitResult hit) {
		if (level.isClientSide()) return InteractionResult.SUCCESS;
		if (level.getBlockEntity(pos) instanceof IsruBlockEntity plant
				&& player instanceof net.minecraft.server.level.ServerPlayer server) {
			plant.interact(server);
		}
		return InteractionResult.SUCCESS;
	}
}
