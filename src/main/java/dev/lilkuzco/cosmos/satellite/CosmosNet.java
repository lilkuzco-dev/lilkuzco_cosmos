package dev.lilkuzco.cosmos.satellite;

import dev.lilkuzco.cosmos.Cosmos;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * S2C: the result of one imaging pass.
     *
     * <p>The report crosses the wire as <b>data</b>, not as pre-rendered chat lines. That is the
     * whole point of this packet: the console screen needs the numbers to lay them out, and a
     * list of {@code Component}s cannot be drawn into a panel. Rendering it as text is now the
     * client's job, which also means the strings localise on the machine that has the language
     * file rather than on the server.
     *
     * <p>It carries {@link ReconImager.Report} verbatim so there is exactly one definition of
     * what a report is, and the chat fallback can keep using {@link ReconImager#render}.
     */
    public record ReconS2C(String satelliteName, ReconImager.Report report)
            implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<ReconS2C> TYPE =
                new CustomPacketPayload.Type<>(Cosmos.id("recon_report"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ReconS2C> CODEC =
                StreamCodec.of((buf, p) -> {
                    ReconImager.Report r = p.report();
                    buf.writeUtf(p.satelliteName());
                    buf.writeUtf(r.satelliteId());
                    buf.writeDouble(r.centreX());
                    buf.writeDouble(r.centreZ());
                    buf.writeDouble(r.footprintRadius());
                    buf.writeVarInt(r.sampled());
                    buf.writeVarInt(r.attempted());
                    buf.writeVarInt(r.artificialBlocks());
                    buf.writeVarInt(r.surfaceComposition().size());
                    for (Map.Entry<String, Integer> e : r.surfaceComposition().entrySet()) {
                        buf.writeUtf(e.getKey());
                        buf.writeVarInt(e.getValue());
                    }
                    buf.writeVarInt(r.strongestSignals().size());
                    for (BlockPos pos : r.strongestSignals()) {
                        buf.writeBlockPos(pos);
                    }
                }, buf -> {
                    String name = buf.readUtf();
                    String id = buf.readUtf();
                    double centreX = buf.readDouble();
                    double centreZ = buf.readDouble();
                    double radius = buf.readDouble();
                    int sampled = buf.readVarInt();
                    int attempted = buf.readVarInt();
                    int artificial = buf.readVarInt();

                    // LinkedHashMap, not HashMap: the server sorted the composition by count and
                    // the screen draws it in that order. A HashMap here would shuffle the rows
                    // every time the packet arrived.
                    int composed = buf.readVarInt();
                    Map<String, Integer> composition = new LinkedHashMap<>();
                    for (int i = 0; i < composed; i++) {
                        composition.put(buf.readUtf(), buf.readVarInt());
                    }
                    int signalCount = buf.readVarInt();
                    List<BlockPos> signals = new ArrayList<>(signalCount);
                    for (int i = 0; i < signalCount; i++) {
                        signals.add(buf.readBlockPos());
                    }
                    return new ReconS2C(name, new ReconImager.Report(id, centreX, centreZ, radius,
                            sampled, attempted, artificial, composition, signals));
                });

        @Override
        public CustomPacketPayload.Type<ReconS2C> type() { return TYPE; }
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
        PayloadTypeRegistry.clientboundPlay().register(ReconS2C.TYPE, ReconS2C.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ConsoleActionC2S.TYPE, ConsoleActionC2S.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ConsoleActionC2S.TYPE, (payload, context) ->
                context.server().execute(() ->
                        SatelliteConsoleBlockEntity.handleAction(context.player(), payload)));
    }

    private CosmosNet() {}
}
