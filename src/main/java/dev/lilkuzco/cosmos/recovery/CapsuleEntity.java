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
    /**
     * A view for a body that is already flying, spawned where it can actually be seen.
     *
     * <p>The capsule used to be spawned at the entry point, three thousand blocks downrange. That
     * chunk is not loaded and nobody is standing in it, so the entity was unloaded almost
     * immediately and never travelled — and the entity tracker, which decides who to send an
     * entity to from its chunk section, had nothing to send. The descent was invisible from the
     * landing site, intermittently and confusingly: occasionally a section update fired for an
     * unrelated reason and the capsule appeared for a frame, which looked like a rendering bug.
     *
     * <p>A view belongs where there is somebody to view it.
     */
    public static CapsuleEntity viewFor(ServerLevel level, String bodyId, String satelliteId,
                                        Vec3 position) {
        CapsuleEntity capsule = new CapsuleEntity(CosmosEntities.CAPSULE, level);
        capsule.bodyId = bodyId;
        capsule.satelliteId = satelliteId;
        capsule.setPos(position.x(), position.y(), position.z());
        return level.addFreshEntity(capsule) ? capsule : null;
    }

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

        // NOT added to the world here. The body flies; the view is created by RecoveryTracker
        // once the descent is close enough for anyone to see it.
        return capsule;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(GLOW, 0.0F);
        builder.define(CHUTE, Boolean.FALSE);
    }

    /**
     * The view no longer drives itself.
     *
     * <p>It used to mirror its body from here, and the comment below this method admitted the
     * problem without anyone acting on it: a capsule enters thousands of blocks downrange and
     * spends nearly all of its flight over chunks nobody has loaded, so this method does not run
     * and the entity never moves. The landing resolved correctly on the server tick and
     * <b>nothing ever arrived at the landing site to watch</b> — sixteen frames of the render
     * battery caught an empty sky.
     *
     * <p>{@link RecoveryTracker} now drives the position, the synced state and the particles from
     * the server tick, which is where the physics already lives. Empire law, rule 7: anything that
     * must happen while nobody is nearby cannot hang off an entity tick — and that includes the
     * part whose entire job is to be looked at.
     */
    @Override
    public void tick() {
        super.tick();
    }

    /**
     * Force the canopy on, for the render battery's model board.
     *
     * <p>A parachute is only deployed for the last few seconds of a four-thousand-block entry, on
     * an object ten pixels tall. Verifying it by photographing a real descent took six runs and
     * two hours and never produced a legible frame - so the battery gets a capsule it can stand
     * next to instead. The flight is verified elsewhere; this verifies the MODEL.
     */
    public void showChuteForDisplay() {
        entityData.set(CHUTE, Boolean.TRUE);
    }

    /** Called by {@link RecoveryTracker} every server tick, loaded chunks or not. */
    public void follow(ServerLevel server, KineticBody body) {
        // teleportTo, NOT setPos.
        //
        // setPos moves the entity and its bounding box and tells nobody. The entity tracker
        // decides who to send an entity to from its chunk section, and that membership is
        // refreshed on the entity's own tick - which does not run out here. So the tracker went on
        // believing the capsule was still at the entry point 3,300 blocks downrange and never sent
        // it to any client. It was on screen occasionally, when a section update happened to
        // fire for another reason, which is worse than never: it looked like a rendering problem.
        teleportTo(server, body.position().x(), body.position().y(), body.position().z(),
                java.util.Set.of(), getYRot(), getXRot(), false);

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
        if (body.hasInflatedChute() && server.getGameTime() % 10 == 0) {
            server.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 2.0, getZ(),
                    6, 0.8, 0.2, 0.8, 0.0);
        }
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
