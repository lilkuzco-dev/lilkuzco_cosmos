package dev.lilkuzco.cosmos.satellite;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The planetarium. Right-click and the server sends the constellation as kinetics has it right
 * now; the client draws it.
 */
public class SatelliteConsoleBlock extends BaseEntityBlock {

	public static final MapCodec<SatelliteConsoleBlock> CODEC = simpleCodec(SatelliteConsoleBlock::new);

	public SatelliteConsoleBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends SatelliteConsoleBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SatelliteConsoleBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hit) {
		if (level.isClientSide()) return InteractionResult.SUCCESS;
		if (player instanceof ServerPlayer server) {
			SatelliteConsoleBlockEntity.sendSnapshot(server, pos, true);
		}
		return InteractionResult.CONSUME;
	}
}
