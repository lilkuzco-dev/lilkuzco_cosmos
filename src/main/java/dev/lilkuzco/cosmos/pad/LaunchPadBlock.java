package dev.lilkuzco.cosmos.pad;

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

/** The launch pad controller. {@code LIT} is true during a countdown, which drives the visuals. */
public class LaunchPadBlock extends BaseEntityBlock {

    public static final MapCodec<LaunchPadBlock> CODEC = simpleCodec(LaunchPadBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public LaunchPadBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, Boolean.FALSE));
    }

    @Override
    protected MapCodec<? extends LaunchPadBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaunchPadBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, CosmosBlockEntities.LAUNCH_PAD,
                LaunchPadBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof LaunchPadBlockEntity pad) {
            player.openMenu(pad);
        }
        return InteractionResult.CONSUME;
    }

    /**
     * A propellant bucket poured straight into the pad.
     *
     * <p>The by-hand path, for the first sounding rocket before there is any industry. It is
     * deliberately not the only path - {@code FluidStorage.SIDED} is registered on this block, so
     * anything that moves fluid can fill a pad, and for the lunar vehicle's 1,428 buckets it had
     * better.
     */
    @Override
    protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,
                                          BlockState state, Level level, BlockPos pos,
                                          Player player, net.minecraft.world.InteractionHand hand,
                                          BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof LaunchPadBlockEntity pad
                && net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorageUtil
                        .interactWithFluidStorage(pad.tank(), player, hand)) {
            return InteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }
}
