package dev.lilkuzco.cosmos.satellite;

import dev.lilkuzco.cosmos.Cosmos;
import dev.lilkuzco.kinetics.orbit.GroundTrack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ground-track imaging: what is under the satellite right now.
 *
 * <p>The interesting output is not the terrain, it is the <b>worked ground</b> - the count of
 * surface blocks that only exist because somebody put them there. A recon pass over wilderness
 * reports biomes; a pass over a base reports a number, and that number is the intelligence.
 * Which blocks count is a datapack tag ({@code #cosmos:artificial_surface}), so a server can
 * decide whether, say, farmland is a signature.
 *
 * <p><b>Unloaded chunks are not imaged, and the report says so.</b> Forcing chunk loads across a
 * 2.9 km footprint would be a loading storm on every pass. A satellite reports what it could
 * actually see and states its coverage, which is also the more honest answer - orbital
 * reconnaissance of somewhere nobody is standing is exactly the case where you get partial data.
 */
public final class ReconImager {

    /** Surface blocks that imply construction. Datapack-driven so a server sets the threshold. */
    public static final TagKey<Block> ARTIFICIAL_SURFACE =
            TagKey.create(Registries.BLOCK, Cosmos.id("artificial_surface"));

    /** Points sampled across the footprint. A grid, not random, so a report is reproducible. */
    private static final int SAMPLE_GRID = 15;

    private ReconImager() {}

    /** The result of one imaging pass. */
    public record Report(
            String satelliteId,
            double centreX,
            double centreZ,
            double footprintRadius,
            int sampled,
            int attempted,
            int artificialBlocks,
            Map<String, Integer> surfaceComposition,
            List<BlockPos> strongestSignals) {

        public boolean partial() { return sampled < attempted; }

        public double coverage() { return attempted == 0 ? 0.0 : (double) sampled / attempted; }

        /** Whether anything built showed up at all. */
        public boolean foundConstruction() { return artificialBlocks > 0; }
    }

    /**
     * Image the ground under a satellite.
     *
     * @param track          the sub-satellite point from kinetics
     * @param halfAngleDeg   the payload's sensor cone
     */
    public static Report image(ServerLevel level, String satelliteId, GroundTrack track,
                               double halfAngleDeg) {
        double radius = track.footprintRadius(halfAngleDeg);
        double centreX = track.worldX();
        double centreZ = track.worldZ();

        Map<String, Integer> composition = new LinkedHashMap<>();
        List<BlockPos> signals = new ArrayList<>();
        int sampled = 0;
        int attempted = 0;
        int artificial = 0;

        double step = (radius * 2.0) / (SAMPLE_GRID - 1);
        for (int ix = 0; ix < SAMPLE_GRID; ix++) {
            for (int iz = 0; iz < SAMPLE_GRID; iz++) {
                double dx = -radius + ix * step;
                double dz = -radius + iz * step;
                // A circular footprint, not the square the grid would give.
                if (dx * dx + dz * dz > radius * radius) continue;

                attempted++;
                int bx = (int) Math.floor(centreX + dx);
                int bz = (int) Math.floor(centreZ + dz);

                // Read only what is already loaded. hasChunkAt does not generate.
                if (!level.hasChunkAt(new BlockPos(bx, level.getMinY(), bz))) continue;
                sampled++;

                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz);
                BlockPos pos = new BlockPos(bx, Math.max(level.getMinY(), surfaceY - 1), bz);
                BlockState state = level.getBlockState(pos);
                if (state.isAir()) continue;

                String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                        .getKey(state.getBlock()).getPath();
                composition.merge(name, 1, Integer::sum);

                if (state.is(ARTIFICIAL_SURFACE)) {
                    artificial++;
                    if (signals.size() < 5) signals.add(pos);
                }
            }
        }

        Map<String, Integer> top = new LinkedHashMap<>();
        composition.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .limit(4)
                .forEach(e -> top.put(e.getKey(), e.getValue()));

        return new Report(satelliteId, centreX, centreZ, radius, sampled, attempted,
                artificial, top, signals);
    }

    /** Render a report as chat lines. */
    public static List<Component> render(Report report, String satelliteName) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("cosmos.recon.header", satelliteName));
        lines.add(Component.translatable("cosmos.recon.footprint",
                String.format("%.0f", report.centreX()), String.format("%.0f", report.centreZ()),
                String.format("%.0f", report.footprintRadius())));

        if (report.sampled() == 0) {
            lines.add(Component.translatable("cosmos.recon.no_coverage"));
            return lines;
        }
        if (report.partial()) {
            lines.add(Component.translatable("cosmos.recon.partial",
                    String.format("%.0f", report.coverage() * 100.0)));
        }

        StringBuilder terrain = new StringBuilder();
        report.surfaceComposition().forEach((name, count) -> {
            if (!terrain.isEmpty()) terrain.append(", ");
            terrain.append(name).append(" ×").append(count);
        });
        lines.add(Component.translatable("cosmos.recon.terrain", terrain.toString()));

        if (report.foundConstruction()) {
            lines.add(Component.translatable("cosmos.recon.construction",
                    report.artificialBlocks()));
            for (BlockPos pos : report.strongestSignals()) {
                lines.add(Component.translatable("cosmos.recon.signal",
                        pos.getX(), pos.getY(), pos.getZ()));
            }
        } else {
            lines.add(Component.translatable("cosmos.recon.wilderness"));
        }
        return lines;
    }
}
