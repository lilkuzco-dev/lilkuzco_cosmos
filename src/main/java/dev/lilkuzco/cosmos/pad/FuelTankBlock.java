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

    /**
     * Pour a bucket into the tank you are standing at, not the one controller you must first find.
     *
     * <p>The propellant still lives in the controller - this only forwards. Making the player walk
     * to the middle of a 13x13 apron to empty every bucket would be a chore invented by the
     * implementation, not by the design.
     */
    @Override
    protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,
                                          BlockState state, Level level, BlockPos pos,
                                          Player player, net.minecraft.world.InteractionHand hand,
                                          BlockHitResult hit) {
        LaunchPadBlockEntity pad = controllerFor(level, pos);
        if (pad != null && net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorageUtil
                .interactWithFluidStorage(pad.tank(), player, hand)) {
            return InteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    /**
     * The controller this tank feeds, or null.
     *
     * <p>Searched outward from the tank rather than cached, because a pad is built and rebuilt and
     * a stale link is worse than a short scan. Bounded by the largest tier's apron plus its tank
     * ring, so the cost does not depend on what else is in the world.
     */
    public static LaunchPadBlockEntity controllerFor(Level level, BlockPos tank) {
        int reach = dev.lilkuzco.cosmos.rocket.RocketTier.LADDER.stream()
                .mapToInt(dev.lilkuzco.cosmos.rocket.RocketTier::padRadius).max().orElse(5) + 1;
        for (BlockPos pos : BlockPos.betweenClosed(tank.offset(-reach, -1, -reach),
                tank.offset(reach, 1, reach))) {
            if (!(level.getBlockEntity(pos) instanceof LaunchPadBlockEntity pad)) continue;
            if (PadStructure.connectedTanks(level, pos, pad.reservoirTier()).contains(tank)) {
                return pad;
            }
        }
        return null;
    }
}
