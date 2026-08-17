package dev.lilkuzco.cosmos.pad;

import dev.lilkuzco.cosmos.CosmosBlocks;
import dev.lilkuzco.cosmos.rocket.RocketTier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Multiblock validation for the launch pad.
 *
 * <p>The rule that shapes everything: <b>a failed check reports the first block that is wrong and
 * where it is.</b> "Structure incomplete" is a puzzle; "pad frame missing at (12, 64, -8)" is an
 * instruction. Building a 9×9 pad and being told only that it does not work is the worst possible
 * multiblock experience, and it is entirely avoidable.
 *
 * <p>The pad is a solid square of frame at the controller's own level, plus clear airspace above
 * it. Airspace is not decoration - it is the volume the rocket occupies at ignition, and a roof
 * over a launch pad should stop a launch.
 */
public final class PadStructure {

    private PadStructure() {}

    /** The outcome of a structure check. */
    public record Check(boolean valid, RocketTier tier, String failureKey,
                        BlockPos failurePos, int frameBlocks, int clearance) {

        public static Check ok(RocketTier tier, int frameBlocks, int clearance) {
            return new Check(true, tier, null, null, frameBlocks, clearance);
        }

        public static Check fail(RocketTier tier, String key, BlockPos pos) {
            return new Check(false, tier, key, pos, 0, 0);
        }
    }

    /**
     * Check the pad for a given tier.
     *
     * @param controller position of the launch pad controller
     */
    public static Check check(Level level, BlockPos controller, RocketTier tier) {
        int radius = tier.padRadius();
        int frameBlocks = 0;

        // The apron: a solid square of frame at the controller's level, controller included.
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                BlockPos pos = controller.offset(dx, 0, dz);
                if (pos.equals(controller)) continue;
                BlockState state = level.getBlockState(pos);
                if (!state.is(CosmosBlocks.PAD_FRAME)) {
                    return Check.fail(tier, "cosmos.pad.missing_frame", pos);
                }
                frameBlocks++;
            }
        }

        // The airspace: clear to the tier's height over the whole apron. Checked from the
        // bottom up so the reported obstruction is the lowest one, which is the one to break.
        int height = tier.padHeight();
        for (int dy = 1; dy <= height; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = controller.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir() && !state.canBeReplaced()) {
                        return Check.fail(tier, "cosmos.pad.obstructed", pos);
                    }
                }
            }
        }

        return Check.ok(tier, frameBlocks, height);
    }

    /**
     * The largest tier this pad can fly, or null. Checked largest first so a big pad is not
     * reported as a small one.
     */
    public static RocketTier largestSupported(Level level, BlockPos controller) {
        for (int i = RocketTier.LADDER.size() - 1; i >= 0; i--) {
            RocketTier tier = RocketTier.LADDER.get(i);
            if (check(level, controller, tier).valid()) return tier;
        }
        return null;
    }

    /**
     * Players standing on the apron, i.e. the crew.
     *
     * <p>The volume checked is exactly the volume the structure check already demands be clear -
     * the apron and the airspace above it - so "somewhere the rocket is about to be" and
     * "somewhere you become crew" are the same place. There is no seat to click and no confirm
     * dialog: the countdown is the confirmation, and standing under a rocket while it counts is
     * an unambiguous statement of intent in either direction.
     */
    public static java.util.List<net.minecraft.server.level.ServerPlayer> playersOnPad(
            net.minecraft.server.level.ServerLevel level, BlockPos controller, RocketTier tier) {
        int radius = tier.padRadius();
        net.minecraft.world.phys.AABB volume = new net.minecraft.world.phys.AABB(
                controller.getX() - radius, controller.getY(), controller.getZ() - radius,
                controller.getX() + radius + 1, controller.getY() + tier.padHeight() + 1,
                controller.getZ() + radius + 1);
        return level.getEntitiesOfClass(net.minecraft.server.level.ServerPlayer.class, volume,
                player -> !player.isSpectator());
    }

    /** Fuel tanks feeding this pad: any tank orthogonally touching the apron. */
    public static List<BlockPos> connectedTanks(Level level, BlockPos controller,
                                                RocketTier tier) {
        int radius = tier.padRadius();
        List<BlockPos> tanks = new ArrayList<>();
        // One ring beyond the apron, at apron level and one above - tanks stand beside the pad,
        // not on it, because the apron has to stay clear for the frame check.
        for (int dx = -radius - 1; dx <= radius + 1; dx++) {
            for (int dz = -radius - 1; dz <= radius + 1; dz++) {
                boolean onRing = Math.abs(dx) == radius + 1 || Math.abs(dz) == radius + 1;
                if (!onRing) continue;
                for (int dy = 0; dy <= 1; dy++) {
                    BlockPos pos = controller.offset(dx, dy, dz);
                    if (level.getBlockState(pos).is(CosmosBlocks.FUEL_TANK)) tanks.add(pos);
                }
            }
        }
        return tanks;
    }
}
