package dev.lilkuzco.cosmos.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Cryogenic hydrolox — liquid hydrogen and liquid oxygen, made from lunar ice.
 *
 * <p><b>It has no world form, and that is a physical statement rather than a shortcut.</b> Liquid
 * oxygen boils at 90 K and liquid hydrogen at 20 K; a puddle of either on an airless surface in
 * sunlight is not a puddle, it is a cloud that used to be one. Every other empire fluid — crude,
 * diesel — is a {@code FlowingFluid} with a block, because those things genuinely sit in a pool.
 * This one exists only inside a tank, so it is a bare {@link Fluid}: something the transfer API
 * can name and move, with no source block, no flow and no bucket to spill.
 *
 * <p>That also means it cannot be dumped on the ground and re-collected, which is the correct
 * behaviour for a propellant that costs a base days of production to make.
 */
public final class HydroloxFluid extends Fluid {

	@Override
	public Item getBucket() {
		// No bucket. You do not carry cryogens by hand; you pipe them.
		return Items.AIR;
	}

	@Override
	protected boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos,
	                                    Fluid fluid, net.minecraft.core.Direction direction) {
		return true;
	}

	@Override
	protected Vec3 getFlow(BlockGetter level, BlockPos pos, FluidState state) {
		return Vec3.ZERO;
	}

	@Override
	public int getTickDelay(net.minecraft.world.level.LevelReader level) {
		return 100;
	}

	@Override
	protected float getExplosionResistance() {
		return 100.0F;
	}

	@Override
	public float getHeight(FluidState state, BlockGetter level, BlockPos pos) {
		return 0.0F;
	}

	@Override
	public float getOwnHeight(FluidState state) {
		return 0.0F;
	}

	@Override
	protected BlockState createLegacyBlock(FluidState state) {
		return Blocks.AIR.defaultBlockState();
	}

	@Override
	public boolean isSource(FluidState state) {
		return true;
	}

	@Override
	public int getAmount(FluidState state) {
		return 8;
	}

	@Override
	public VoxelShape getShape(FluidState state, BlockGetter level, BlockPos pos) {
		return Shapes.empty();
	}
}
