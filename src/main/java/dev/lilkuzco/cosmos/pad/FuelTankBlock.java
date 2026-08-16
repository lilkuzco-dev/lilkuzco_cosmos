package dev.lilkuzco.cosmos.pad;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Tankage. Deliberately dumb: it holds nothing itself and has no block entity.
 *
 * <p>Propellant lives in the launch pad controller, and tanks beside the apron simply raise how
 * much it can hold. That is one saved state instead of nine, one place to fuel instead of hunting
 * for the tank that is not full, and no plumbing to debug. The multiblock still reads correctly to
 * a player — more tanks, bigger rocket — which is the part that matters.
 */
public class FuelTankBlock extends Block {

    /** How much propellant each tank adds to its pad's capacity, in millibuckets. */
    public static final int CAPACITY_PER_TANK_MB = 32_000;

    public FuelTankBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        // Point the player at the controller rather than doing nothing. A tank that silently
        // ignores a right-click is indistinguishable from a broken one.
        player.sendSystemMessage(
                net.minecraft.network.chat.Component.translatable("cosmos.tank.use_controller"));
        return InteractionResult.SUCCESS;
    }
}
