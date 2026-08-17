package dev.lilkuzco.cosmos.moon;

import dev.lilkuzco.cosmos.CosmosEntities;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The transfer vehicle: what the crew are inside during the trans-lunar coast.
 *
 * <p>It exists because of a bug, and the bug is worth recording. The first version left the crew
 * riding the launch vehicle after trans-lunar injection — but that entity is a view of a kinetics
 * body, and kinetics retires a body the moment it reaches ORBIT. The entity discarded itself
 * correctly, dismounted its passenger correctly, and <b>dropped the player out of the sky</b> at
 * the top of an ascent. The launch vehicle's job genuinely ends at injection; something else has
 * to take custody of the crew, and this is it.
 *
 * <p>Like every other entity in cosmos it owns no motion. {@link LunarTransit} sets its position
 * from the coast fraction; the entity only carries somebody.
 */
public class TransitEntity extends Entity {

	public TransitEntity(EntityType<? extends TransitEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	/** Put a crew member aboard a new transfer vehicle in the given level. */
	public static TransitEntity board(ServerLevel level, double x, double y, double z,
	                                  ServerPlayer crew) {
		TransitEntity vehicle = new TransitEntity(CosmosEntities.TRANSIT, level);
		vehicle.setPos(x, y, z);
		if (!level.addFreshEntity(vehicle)) return null;
		crew.teleportTo(level, x, y, z, java.util.Set.of(), crew.getYRot(), crew.getXRot(), false);
		return crew.startRiding(vehicle, true, true) ? vehicle : null;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public void tick() {
		super.tick();
		// Passengers ride the position LunarTransit sets, not their own motion. Without this the
		// client's own physics wins and the crew slowly falls out of a stationary spacecraft.
		for (Entity passenger : getPassengers()) {
			passenger.setPos(getX(), getY() + 0.6, getZ());
			passenger.fallDistance = 0.0F;
			passenger.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
		}
		// Nobody aboard: the transit is over or was interrupted. Do not leave a ghost in orbit.
		if (!level().isClientSide() && getPassengers().isEmpty() && tickCount > 20) discard();
	}

	/**
	 * Invulnerable, and non-collidable. Whether an arrival hurt is decided once, by
	 * {@link LunarTransit}, from the speed kinetics reports at contact.
	 */
	@Override
	public boolean hurtServer(ServerLevel level,
	                          net.minecraft.world.damagesource.DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean canBeCollidedWith(Entity by) {
		return false;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengers().isEmpty();
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}
}
