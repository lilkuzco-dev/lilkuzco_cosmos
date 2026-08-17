package dev.lilkuzco.cosmos.life;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Keeps suits topped up within eight blocks.
 *
 * <p>No block entity, no inventory, no power. A station is a fact about a place, and the only
 * thing that reads it is {@link LifeSupport}'s scan. That is deliberate: the interesting decision
 * on the Moon is <em>where you put your stations</em>, and a machine with a fuel slot would move
 * the interesting decision to feeding it.
 */
public class OxygenStationBlock extends Block {

	public OxygenStationBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hit) {
		if (level.isClientSide()) return InteractionResult.SUCCESS;
		if (player instanceof net.minecraft.server.level.ServerPlayer server) {
			LifeSupport.actionBar(server, Component.translatable("cosmos.life.station_range",
					(int) LifeSupport.STATION_RANGE));
		}
		return InteractionResult.SUCCESS;
	}
}
