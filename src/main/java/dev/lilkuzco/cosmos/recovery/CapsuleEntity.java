package dev.lilkuzco.cosmos.recovery;

import dev.lilkuzco.cosmos.Cosmos;
import dev.lilkuzco.cosmos.CosmosEntities;
import dev.lilkuzco.kinetics.body.KineticBody;
import dev.lilkuzco.kinetics.fabric.KineticsMod;
import dev.lilkuzco.kinetics.fabric.KineticsService;
import dev.lilkuzco.kinetics.math.Vec3;
import dev.lilkuzco.kinetics.phase.FlightDirector;
import dev.lilkuzco.kinetics.phase.FlightPhase;
import dev.lilkuzco.kinetics.profile.Profile;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A returning payload. Like {@link dev.lilkuzco.cosmos.rocket.RocketEntity}, a view of a kinetics
 * body and nothing more.
 *
 * <p>What it adds is the visual consequence of a state kinetics already computes:
 * {@code heatingRate()} drives the plasma. Cosmos does not decide when the capsule glows - it
 * reads the Sutton-Graves figure and draws it. Peak heating on a full orbital entry comes out at
 * about 9.1 × 10⁴ W/m², nicely below the 2.5 × 10⁵ threshold at which kinetics would fire an
 * overheat event, so a well-shielded capsule glows hard and survives.
 */
public class CapsuleEntity extends Entity {

    private static final EntityDataAccessor<Float> GLOW =
            SynchedEntityData.defineId(CapsuleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> CHUTE =
            SynchedEntityData.defineId(CapsuleEntity.class, EntityDataSerializers.BOOLEAN);

    private String bodyId = "";
    private String satelliteId = "";

    public CapsuleEntity(EntityType<? extends CapsuleEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** Hand a kinetics descending body to the world as a capsule. */
    public static CapsuleEntity create(ServerLevel level, Vec3 position, Vec3 velocity,
                                       String satelliteId, Profile profile) {
        KineticsService kinetics = KineticsMod.service();
        if (kinetics == null) return null;

        CapsuleEntity capsule = new CapsuleEntity(CosmosEntities.CAPSULE, level);
        capsule.bodyId = profile.id();
        capsule.satelliteId = satelliteId;
        capsule.setPos(position.x(), position.y(), position.z());

        KineticsService.Handle handle = kinetics.spawn(profile.id(), profile, level.dimension(),
                position, velocity, FlightDirector.Mission.BALLISTIC);
        if (handle == null) return null;

        level.addFreshEntity(capsule);
        return capsule;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(GLOW, 0.0F);
        builder.define(CHUTE, Boolean.FALSE);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) return;

        KineticsService kinetics = KineticsMod.service();
        if (kinetics == null) { discard(); return; }

        KineticsService.Handle handle = kinetics.handle(bodyId);
        if (handle == null) { discard(); return; }

        KineticBody body = handle.body();
        setPos(body.position().x(), body.position().y(), body.position().z());

        // Plasma intensity straight off the kinetics heating field - a state value, never damage.
        double threshold = body.profile().airframe().overheatThreshold();
        float glow = (float) Math.min(1.0, body.heatingRate() / Math.max(threshold, 1.0));
        entityData.set(GLOW, glow);
        entityData.set(CHUTE, body.hasInflatedChute());

        if (glow > 0.05F) {
            int count = (int) (2 + glow * 22);
            server.sendParticles(ParticleTypes.FLAME, getX(), getY(), getZ(),
                    count, 0.4, 0.4, 0.4, 0.03);
            if (glow > 0.3F) {
                server.sendParticles(ParticleTypes.LAVA, getX(), getY(), getZ(),
                        (int) (glow * 4), 0.3, 0.3, 0.3, 0.0);
            }
        }
        if (body.hasInflatedChute() && tickCount % 10 == 0) {
            server.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 2.0, getZ(),
                    6, 0.8, 0.2, 0.8, 0.0);
        }

        // Landing is RecoveryTracker's job, on the server tick - this method does not run when
        // the capsule is over unloaded chunks, which is most of its flight.
        if (!body.phase().isInWorld()) discard();
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
        satelliteId = input.getStringOr("satellite_id", "");
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putString("body_id", bodyId);
        output.putString("satellite_id", satelliteId);
    }

    /** 0..1 plasma intensity, for the client renderer. */
    public float glow() { return entityData.get(GLOW); }

    public boolean chuteOut() { return entityData.get(CHUTE); }
}
