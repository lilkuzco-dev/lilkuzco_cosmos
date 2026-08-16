package dev.lilkuzco.cosmos.recovery;

import dev.lilkuzco.cosmos.Cosmos;
import dev.lilkuzco.cosmos.CosmosItems;
import dev.lilkuzco.cosmos.satellite.SatellitePayload;
import dev.lilkuzco.kinetics.fabric.KineticsMod;
import dev.lilkuzco.kinetics.fabric.KineticsService;
import dev.lilkuzco.kinetics.math.Vec3;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves capsule landings on the server tick.
 *
 * <p>The same lesson as {@link dev.lilkuzco.cosmos.rocket.LaunchTracker}, learned twice. A capsule
 * enters four kilometres from where it will land, and for most of that flight it is over chunks
 * nobody has loaded - so the entity is not ticked, and anything that depended on the entity's tick
 * simply never happened. The first version dropped no payload and printed no message: the flight
 * ran perfectly inside kinetics and the result went nowhere.
 *
 * <p>Landing resolution therefore lives beside the physics, on the server tick. The entity remains
 * a view: it shows the plasma and the canopy if anyone is close enough to see them.
 *
 * <p>The payload lands where it was aimed, and a deorbit is aimed at the operator - so by the time
 * the capsule touches down its chunk is loaded and the drop is safe.
 */
public final class RecoveryTracker {

    /**
     * {@code lastSpeed} is carried because kinetics zeroes a body's velocity on impact - by the
     * time the phase reports LANDED the arrival speed is already gone. The touchdown figure has
     * to be remembered from the last tick the body was still flying.
     */
    private static final class Pending {
        final String bodyId;
        final String satelliteId;
        final SatellitePayload payload;
        final ServerLevel level;
        double lastSpeed;

        Pending(String bodyId, String satelliteId, SatellitePayload payload, ServerLevel level) {
            this.bodyId = bodyId;
            this.satelliteId = satelliteId;
            this.payload = payload;
            this.level = level;
        }

        String bodyId() { return bodyId; }
        String satelliteId() { return satelliteId; }
        SatellitePayload payload() { return payload; }
        ServerLevel level() { return level; }
    }

    private static final Map<String, Pending> PENDING = new LinkedHashMap<>();

    private RecoveryTracker() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(RecoveryTracker::tick);
    }

    /** Record a capsule on its way down. Called at the deorbit handoff. */
    public static void track(String bodyId, String satelliteId, SatellitePayload payload,
                             ServerLevel level) {
        PENDING.put(bodyId, new Pending(bodyId, satelliteId, payload, level));
    }

    public static int pendingCount() { return PENDING.size(); }

    private static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        KineticsService kinetics = KineticsMod.service();
        if (kinetics == null) { PENDING.clear(); return; }

        List<String> done = null;
        for (Pending pending : List.copyOf(PENDING.values())) {
            KineticsService.Handle handle = kinetics.handle(pending.bodyId());

            // Kinetics drops a body once it is out of the world, so the LAST tick on which the
            // position is readable is the one where the phase stops being in-world. Catch it
            // there rather than after, or the landing site is lost with the body.
            if (handle == null) {
                Cosmos.LOG.warn("capsule {} vanished before its landing could be read",
                        pending.bodyId());
                if (done == null) done = new ArrayList<>();
                done.add(pending.bodyId());
                continue;
            }

            if (handle.body().phase().isInWorld()) {
                pending.lastSpeed = handle.body().speed();
                continue;
            }

            land(server, pending, handle.body().position(), pending.lastSpeed);
            if (done == null) done = new ArrayList<>();
            done.add(pending.bodyId());
        }
        if (done != null) done.forEach(PENDING::remove);
    }

    private static void land(MinecraftServer server, Pending pending, Vec3 position,
                             double impactSpeed) {
        ServerLevel level = pending.level();
        BlockPos pos = BlockPos.containing(position.x(), position.y(), position.z());

        ItemStack recovered = pending.payload() == SatellitePayload.COMMS
                ? new ItemStack(CosmosItems.SATELLITE_COMMS)
                : new ItemStack(CosmosItems.SATELLITE_RECON);

        if (level.hasChunkAt(pos)) {
            level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.0,
                    pos.getZ() + 0.5, recovered));
            level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1.0F, 1.4F);
        } else {
            // Nobody is there to receive it. Say where it is rather than dropping an item into
            // a chunk that will not remember it.
            Cosmos.LOG.info("capsule {} landed in an unloaded chunk at ({}, {}) - payload not "
                            + "dropped", pending.satelliteId(), pos.getX(), pos.getZ());
        }

        Cosmos.LOG.info("recovery: {} touched down at ({}, {}, {}) at {} m/s",
                pending.satelliteId(), pos.getX(), pos.getY(), pos.getZ(),
                String.format("%.2f", impactSpeed));

        for (var player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(Component.translatable("cosmos.recovery.landed",
                    pos.getX(), pos.getY(), pos.getZ(), String.format("%.1f", impactSpeed)));
        }
    }
}
