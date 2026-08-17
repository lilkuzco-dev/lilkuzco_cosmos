package dev.lilkuzco.cosmos.rocket;

import dev.lilkuzco.cosmos.Cosmos;
import dev.lilkuzco.cosmos.CosmosEntities;
import dev.lilkuzco.cosmos.propellant.Propellant;
import dev.lilkuzco.cosmos.propellant.Propellants;
import dev.lilkuzco.cosmos.satellite.SatellitePayload;
import dev.lilkuzco.kinetics.fabric.KineticsMod;
import dev.lilkuzco.kinetics.fabric.KineticsService;
import dev.lilkuzco.kinetics.math.Vec3;
import dev.lilkuzco.kinetics.phase.FlightDirector;
import dev.lilkuzco.kinetics.phase.FlightPhase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A rocket in flight. <b>A view of a kinetics body, not a physics object.</b>
 *
 * <p>The entity owns nothing about motion. Kinetics integrates the body; this reads its position
 * every tick and moves the entity there. That is the whole reason cosmos can claim to add no
 * physics of its own - there is no place here for a velocity, a drag term or a gravity constant,
 * because the entity has no say in where it goes.
 *
 * <p>The consequence worth noticing: the rocket flies far above the build limit during the upper
 * stage burn, which is correct and is why orbit is a registry rather than an entity. The entity's
 * job ends at insertion.
 */
public class RocketEntity extends Entity {

    private static final EntityDataAccessor<Integer> PHASE =
            SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> THROTTLE =
            SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.FLOAT);

    private String bodyId = "";
    private String tierId = RocketTier.ORBITAL.id();
    private String propellantId = Propellants.CRUDE.id();
    private String payloadName = "";
    private double fuelKg;
    private BlockPos padPos = BlockPos.ZERO;

    public RocketEntity(EntityType<? extends RocketEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;   // kinetics owns motion; vanilla must not also apply gravity
    }

    /**
     * Light a rocket. Returns null if kinetics refuses the profile, which should not happen -
     * the pad quotes from the same code.
     */
    public static RocketEntity launch(ServerLevel level, BlockPos pad, RocketTier tier,
                                      Propellant propellant, double fuelKg,
                                      SatellitePayload payload, boolean crewed,
                                      java.util.List<net.minecraft.server.level.ServerPlayer> crew,
                                      BlockState padState) {
        KineticsService kinetics = KineticsMod.service();
        if (kinetics == null) return null;

        RocketEntity rocket = new RocketEntity(CosmosEntities.ROCKET, level);
        rocket.bodyId = "cosmos:rocket-" + rocket.getUUID();
        rocket.tierId = tier.id();
        rocket.propellantId = propellant.id();
        rocket.payloadName = payload == null ? "" : payload.name();
        rocket.fuelKg = fuelKg;
        rocket.padPos = pad.immutable();
        rocket.setPos(pad.getX() + 0.5, pad.getY() + 1.0, pad.getZ() + 0.5);

        LaunchPipeline pipeline = new LaunchPipeline(kinetics.constants());
        var profile = pipeline.profileFor(rocket.bodyId, tier, propellant, fuelKg);

        KineticsService.Handle handle = kinetics.spawn(rocket.bodyId, profile,
                level.dimension(),
                new Vec3(rocket.getX(), rocket.getY(), rocket.getZ()),
                Vec3.ZERO,
                FlightDirector.Mission.LAUNCH);
        if (handle == null) {
            Cosmos.LOG.error("kinetics refused a launch in an unregistered dimension: {}",
                    level.dimension().identifier());
            return null;
        }
        // Fly the gravity turn downrange along +x. A future pad could face this.
        handle.director().downrange(new Vec3(1, 0, 0));

        level.addFreshEntity(rocket);

        // Board before the flight is tracked, so the tracker records who is actually aboard
        // rather than who was standing nearby a moment ago.
        net.minecraft.server.level.ServerPlayer aboard = null;
        for (net.minecraft.server.level.ServerPlayer player : crew) {
            if (player.startRiding(rocket, true, true)) { aboard = player; break; }
        }

        LaunchTracker.track(rocket.bodyId, tier, payload, rocket.padPos,
                kinetics.worldTimeSeconds(), crewed, propellant, aboard);
        return rocket;
    }

    /**
     * A view of a body that already exists, with a player aboard.
     *
     * <p>Used for the lunar descent, where kinetics is already flying the lander before there is
     * anything to look at. The entity's contract is unchanged - it mirrors a body and owns no
     * motion - the only difference is that this one has someone in it.
     */
    public static RocketEntity ride(ServerLevel level, String bodyId, double x, double y, double z,
                                    net.minecraft.server.level.ServerPlayer crew) {
        RocketEntity entity = new RocketEntity(CosmosEntities.ROCKET, level);
        entity.bodyId = bodyId;
        entity.tierId = RocketTier.LUNAR.id();
        entity.setPos(x, y, z);
        if (!level.addFreshEntity(entity)) return null;
        crew.teleportTo(level, x, y, z, java.util.Set.of(), crew.getYRot(), crew.getXRot(), false);
        crew.startRiding(entity, true, true);
        return entity;
    }

    /**
     * Crewed. One seat, and it is the payload bay.
     *
     * <p>A rocket that cannot be ridden is a rocket the Moon is unreachable from - the whole of
     * Phase B is a journey somebody takes, not a satellite they launch.
     */
    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty();
    }

    /**
     * Passengers ride the body's motion, not their own.
     *
     * <p>Vanilla would otherwise apply its own fall damage to a player descending at 400 m/s
     * inside a vehicle that is about to cancel exactly that velocity. Whether the arrival hurt is
     * decided once, by cosmos, from the speed kinetics reports at contact - two authorities on the
     * same impact is precisely the split-brain the server-authoritative arrangement avoids.
     */
    @Override
    public boolean canBeCollidedWith(Entity by) {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(PHASE, FlightPhase.RAIL.ordinal());
        builder.define(THROTTLE, 0.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) {
            clientTick();
            return;
        }

        KineticsService kinetics = KineticsMod.service();
        if (kinetics == null) { discard(); return; }

        KineticsService.Handle handle = kinetics.handle(bodyId);
        if (handle == null) {
            // The body finished and kinetics dropped it. Nothing left to mirror.
            discard();
            return;
        }

        // Mirror. This is the entity's entire physical contribution.
        Vec3 p = handle.body().position();
        setPos(p.x(), p.y(), p.z());
        entityData.set(PHASE, handle.body().phase().ordinal());
        entityData.set(THROTTLE, (float) handle.body().throttle());

        emitPlume(server, handle);

        // Passengers ride at the body's position. Vanilla moves a rider from the vehicle's own
        // velocity, which this entity does not have - it teleports each tick - so the position has
        // to be pushed explicitly or the crew is left behind at the pad.
        for (Entity passenger : getPassengers()) {
            passenger.setPos(getX(), getY() + 0.6, getZ());
            passenger.fallDistance = 0.0F;
        }

        // Outcome resolution is LaunchTracker's job, on the server tick. It has to be, because
        // this method does not run when nobody is nearby - see LaunchTracker's class comment.
        if (!handle.body().phase().isInWorld()) discard();
    }

    /** Engine plume and the shock of a first-stage burn, from the server so everyone sees it. */
    private void emitPlume(ServerLevel server, KineticsService.Handle handle) {
        if (handle.body().phase() != FlightPhase.BOOST) return;

        server.sendParticles(ParticleTypes.FLAME, getX(), getY() - 1.0, getZ(),
                12, 0.35, 0.2, 0.35, 0.02);
        server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() - 1.5, getZ(),
                8, 0.5, 0.2, 0.5, 0.01);

        if (tickCount % 4 == 0) {
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(),
                    SoundSource.BLOCKS, 3.0F, 0.6F);
        }
    }

    private void clientTick() {
        // Everything visual is server-driven; the client only interpolates the position it is
        // given, which is the arrangement kinetics' determinism guarantee assumes.
    }

    /**
     * Invulnerable. Kinetics owns this body's state, and letting a vanilla damage source alter or
     * remove it would put a second authority on the same object - which is exactly the split-brain
     * the server-authoritative arrangement exists to prevent.
     */
    @Override
    public boolean hurtServer(net.minecraft.server.level.ServerLevel level,
                              net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        bodyId = input.getStringOr("body_id", "");
        tierId = input.getStringOr("tier", RocketTier.ORBITAL.id());
        propellantId = input.getStringOr("propellant", Propellants.CRUDE.id());
        payloadName = input.getStringOr("payload", "");
        fuelKg = input.getDoubleOr("fuel_kg", 0.0);
        padPos = new BlockPos(input.getIntOr("pad_x", 0), input.getIntOr("pad_y", 0),
                input.getIntOr("pad_z", 0));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putString("body_id", bodyId);
        output.putString("tier", tierId);
        output.putString("propellant", propellantId);
        output.putString("payload", payloadName);
        output.putDouble("fuel_kg", fuelKg);
        output.putInt("pad_x", padPos.getX());
        output.putInt("pad_y", padPos.getY());
        output.putInt("pad_z", padPos.getZ());
    }

    /** Flight phase, for the client renderer. */
    public FlightPhase phase() {
        int ordinal = entityData.get(PHASE);
        FlightPhase[] values = FlightPhase.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : FlightPhase.RAIL;
    }

    public float throttle() { return entityData.get(THROTTLE); }

    public String bodyId() { return bodyId; }
}
