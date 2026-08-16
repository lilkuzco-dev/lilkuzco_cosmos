package dev.lilkuzco.cosmos.satellite;

import dev.lilkuzco.cosmos.Cosmos;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * The planetarium wire protocol.
 *
 * <p>The server owns everything. The console sends a snapshot of the constellation as kinetics
 * currently computes it, and the client draws it; the client never propagates an orbit itself.
 * That is the same server-authoritative arrangement kinetics requires of its bodies, for the same
 * reason - two sides integrating independently would disagree the moment either stuttered.
 */
public final class CosmosNet {

    /** One satellite as the planetarium needs to draw it. */
    public record SatelliteView(
            String id, String name, String payload,
            double altitude, double periodSeconds, double speed,
            double latitude, double longitude,
            double worldX, double worldZ,
            double argumentOfLatitudeDeg, double inclinationDeg,
            boolean decaying, double nextPassSeconds) {}

    /** S2C: open or refresh the planetarium. */
    public record PlanetariumS2C(BlockPos console, List<SatelliteView> satellites,
                                 double worldTime, double planetRadius, boolean openScreen)
            implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<PlanetariumS2C> TYPE =
                new CustomPacketPayload.Type<>(Cosmos.id("planetarium"));

        public static final StreamCodec<RegistryFriendlyByteBuf, PlanetariumS2C> CODEC =
                StreamCodec.of((buf, p) -> {
                    buf.writeBlockPos(p.console());
                    buf.writeVarInt(p.satellites().size());
                    for (SatelliteView v : p.satellites()) {
                        buf.writeUtf(v.id());
                        buf.writeUtf(v.name());
                        buf.writeUtf(v.payload());
                        buf.writeDouble(v.altitude());
                        buf.writeDouble(v.periodSeconds());
                        buf.writeDouble(v.speed());
                        buf.writeDouble(v.latitude());
                        buf.writeDouble(v.longitude());
                        buf.writeDouble(v.worldX());
                        buf.writeDouble(v.worldZ());
                        buf.writeDouble(v.argumentOfLatitudeDeg());
                        buf.writeDouble(v.inclinationDeg());
                        buf.writeBoolean(v.decaying());
                        buf.writeDouble(v.nextPassSeconds());
                    }
                    buf.writeDouble(p.worldTime());
                    buf.writeDouble(p.planetRadius());
                    buf.writeBoolean(p.openScreen());
                }, buf -> {
                    BlockPos console = buf.readBlockPos();
                    int count = buf.readVarInt();
                    List<SatelliteView> views = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        views.add(new SatelliteView(
                                buf.readUtf(), buf.readUtf(), buf.readUtf(),
                                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                                buf.readDouble(), buf.readDouble(),
                                buf.readDouble(), buf.readDouble(),
                                buf.readDouble(), buf.readDouble(),
                                buf.readBoolean(), buf.readDouble()));
                    }
                    return new PlanetariumS2C(console, views, buf.readDouble(), buf.readDouble(),
                            buf.readBoolean());
                });

        @Override
        public CustomPacketPayload.Type<PlanetariumS2C> type() { return TYPE; }
    }

    /** C2S: act on a satellite. */
    public record ConsoleActionC2S(BlockPos console, String satelliteId, String action,
                                   String argument) implements CustomPacketPayload {

        public static final String ACTION_REFRESH = "refresh";
        public static final String ACTION_RENAME = "rename";
        public static final String ACTION_IMAGE = "image";
        public static final String ACTION_DEORBIT = "deorbit";

        public static final CustomPacketPayload.Type<ConsoleActionC2S> TYPE =
                new CustomPacketPayload.Type<>(Cosmos.id("console_action"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ConsoleActionC2S> CODEC =
                StreamCodec.of((buf, p) -> {
                    buf.writeBlockPos(p.console());
                    buf.writeUtf(p.satelliteId());
                    buf.writeUtf(p.action());
                    buf.writeUtf(p.argument());
                }, buf -> new ConsoleActionC2S(buf.readBlockPos(), buf.readUtf(), buf.readUtf(),
                        buf.readUtf()));

        @Override
        public CustomPacketPayload.Type<ConsoleActionC2S> type() { return TYPE; }
    }

    public static void registerCommon() {
        PayloadTypeRegistry.clientboundPlay().register(PlanetariumS2C.TYPE, PlanetariumS2C.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ConsoleActionC2S.TYPE, ConsoleActionC2S.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ConsoleActionC2S.TYPE, (payload, context) ->
                context.server().execute(() ->
                        SatelliteConsoleBlockEntity.handleAction(context.player(), payload)));
    }

    private CosmosNet() {}
}
