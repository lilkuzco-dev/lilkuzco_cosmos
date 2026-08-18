package dev.lilkuzco.cosmos.client;

import dev.lilkuzco.cosmos.satellite.CosmosNet;
import dev.lilkuzco.cosmos.satellite.ReconImager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The planetarium. <b>Every pixel is drawn, not textured.</b>
 *
 * <p>That is a licensing decision before it is an aesthetic one: the campaign requires all art to
 * be original, and a screen composed entirely of filled rectangles and computed positions cannot
 * be anything else. It also happens to be the right tool - an orbit is a curve whose shape depends
 * on live data, and no texture could show a satellite where it actually is.
 *
 * <p>The left panel is the roster; the right is the display. The display draws the planet in
 * profile with each orbit as an ellipse scaled by its real semi-major axis, so a high orbit
 * visibly sits further out than a low one, and the selected satellite's marker sits at its true
 * argument of latitude. Below it, the ground track: latitude against longitude, with the
 * footprint drawn to scale.
 */
public class PlanetariumScreen extends Screen {

    // A deliberately narrow palette - instrument phosphor, not decoration.
    private static final int BACKDROP = 0xF00A0E14;
    private static final int PANEL = 0xFF11161F;
    private static final int GRID = 0xFF1B2836;
    private static final int PLANET = 0xFF2E4A63;
    private static final int PLANET_RIM = 0xFF4E7FA8;
    private static final int ORBIT = 0xFF2F6E5A;
    private static final int ORBIT_SELECTED = 0xFF57D6A2;
    private static final int MARKER = 0xFFE8F3A0;
    private static final int MARKER_DECAY = 0xFFE8865A;
    private static final int TEXT = 0xFFBFD4E6;
    private static final int TEXT_DIM = 0xFF6C8299;
    private static final int ACCENT = 0xFF57D6A2;

    /**
     * The console is 320x224. The height is set by the widgets, not the panels: every button on
     * this screen must sit OUTSIDE the drawn areas, because the panels are painted after the
     * widgets and an opaque fill over a button leaves it perfectly clickable and completely
     * invisible. The render battery caught exactly that - Image and the two roster arrows were
     * under the roster panel for the whole of 0.2.x.
     */
    private static final int PANEL_W = 320;
    private static final int PANEL_H = 224;

    private final BlockPos console;
    private List<CosmosNet.SatelliteView> satellites;
    private double planetRadius;
    private int selected;
    private float spin;

    /**
     * The most recent imaging pass, or null. Held with the satellite id it belongs to (inside the
     * report itself) rather than with the roster index, so it is impossible for it to end up
     * captioned with the wrong satellite: cycling the selection hides it and cycling back shows
     * it, with no clearing logic to forget to call.
     */
    private ReconImager.Report recon;
    private String reconName;

    // The client receiver needs to know whether a planetarium is already open so a refresh
    // updates it in place instead of reopening it and resetting the selection.
    private static PlanetariumScreen open;

    public static PlanetariumScreen open() { return open; }

    private int panelLeft() { return width / 2 - PANEL_W / 2; }

    private int panelTop() { return height / 2 - PANEL_H / 2; }

    public PlanetariumScreen(BlockPos console, List<CosmosNet.SatelliteView> satellites,
                             double planetRadius) {
        super(Component.translatable("cosmos.planetarium.title"));
        this.console = console;
        this.satellites = satellites;
        this.planetRadius = planetRadius;
    }

    /** Refresh in place when the server sends a new snapshot. */
    public void update(List<CosmosNet.SatelliteView> views, double radius) {
        this.satellites = views;
        this.planetRadius = radius;
        if (selected >= views.size()) selected = Math.max(0, views.size() - 1);
        // A report about something that has deorbited is not history, it is a lie about what is
        // up there. Drop it when its subject leaves the roster.
        if (recon != null && views.stream().noneMatch(v -> v.id().equals(recon.satelliteId()))) {
            recon = null;
            reconName = null;
        }
        rebuildButtons();
    }

    /** Receive an imaging pass from the server. */
    public void showRecon(String satelliteName, ReconImager.Report report) {
        this.recon = report;
        this.reconName = satelliteName;
        // Snap the roster to whatever was imaged, so the panel and the highlight agree even if
        // the operator cycled while the packet was in flight.
        for (int i = 0; i < satellites.size(); i++) {
            if (satellites.get(i).id().equals(report.satelliteId())) {
                selected = i;
                break;
            }
        }
    }

    /** The satellite the roster is on, or null when it is empty. For the render battery. */
    String selectedId() {
        return satellites.isEmpty() ? null : satellites.get(selected).id();
    }

    /** Whether the held report describes the satellite currently selected. */
    private boolean hasReconForSelection() {
        return recon != null && !satellites.isEmpty()
                && satellites.get(selected).id().equals(recon.satelliteId());
    }

    @Override
    protected void init() {
        open = this;
        rebuildButtons();
    }

    @Override
    public void removed() {
        if (open == this) open = null;
        super.removed();
    }

    private void rebuildButtons() {
        clearWidgets();
        if (satellites.isEmpty()) return;

        int left = panelLeft();
        int top = panelTop();

        // Selection by button rather than by clicking a row: at this scale the roster rows are
        // 20 px tall and a mis-click would command a deorbit on the wrong satellite. They sit in
        // the strip ABOVE the roster, which is kept clear of every fill for that reason.
        addRenderableWidget(Button.builder(Component.literal("<"),
                b -> cycle(-1)).bounds(left + 4, top + 16, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"),
                b -> cycle(1)).bounds(left + 26, top + 16, 20, 20).build());

        int row = top + 196;
        addRenderableWidget(Button.builder(Component.translatable("cosmos.planetarium.image"),
                b -> send(CosmosNet.ConsoleActionC2S.ACTION_IMAGE))
                .bounds(left + 4, row, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("cosmos.planetarium.deorbit"),
                b -> send(CosmosNet.ConsoleActionC2S.ACTION_DEORBIT))
                .bounds(left + 108, row, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("cosmos.planetarium.refresh"),
                b -> send(CosmosNet.ConsoleActionC2S.ACTION_REFRESH))
                .bounds(left + 212, row, 100, 20).build());
    }

    private void cycle(int delta) {
        if (satellites.isEmpty()) return;
        selected = Math.floorMod(selected + delta, satellites.size());
    }

    private void send(String action) {
        if (satellites.isEmpty()) return;
        ClientPlayNetworking.send(new CosmosNet.ConsoleActionC2S(console,
                satellites.get(selected).id(), action, ""));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY,
                                   float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        spin += partialTick * 0.12F;

        int left = panelLeft();
        int top = panelTop();
        int w = PANEL_W;
        int h = PANEL_H;

        g.fill(left, top, left + w, top + h, BACKDROP);
        g.fill(left, top, left + w, top + 14, PANEL);
        g.text(font, Component.translatable("cosmos.planetarium.title"), left + 6, top + 4, ACCENT);
        g.text(font, Component.translatable("cosmos.planetarium.count", satellites.size()), left + w - 60, top + 4, TEXT_DIM);

        if (satellites.isEmpty()) {
            g.text(font, Component.translatable("cosmos.planetarium.empty"), left + 12, top + 40, TEXT_DIM);
            return;
        }

        drawRoster(g, left + 4, top + 40, 108, 152);
        if (hasReconForSelection()) {
            // The report takes the top row - the orbit ellipse and the telemetry column - and
            // deliberately leaves the ground track below it, because the ground track is the
            // panel that shows WHERE this report was taken.
            drawRecon(g, left + 118, top + 16, 198, 104);
        } else {
            drawOrbitView(g, left + 118, top + 16, 96, 104);
            drawTelemetry(g, left + 220, top + 16, 96, 104);
        }
        drawGroundTrack(g, left + 118, top + 124, 196, 68);
    }

    /** Left panel: the roster, selectable. */
    private void drawRoster(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL);
        int row = 0;
        for (CosmosNet.SatelliteView v : satellites) {
            int ry = y + 3 + row * 20;
            if (ry + 18 > y + h) break;
            boolean isSelected = row == selected;
            if (isSelected) g.fill(x + 1, ry - 1, x + w - 1, ry + 17, 0xFF1E3A2E);

            g.text(font, Component.literal(v.name()), x + 5, ry + 1, isSelected ? ACCENT : TEXT);
            g.text(font, Component.literal(String.format("%s  %.0fm", v.payload().charAt(0) == 'R' ? "REC" : "COM",
                    v.altitude())), x + 5, ry + 9, TEXT_DIM);
            if (v.decaying()) g.fill(x + w - 6, ry + 2, x + w - 3, ry + 14, MARKER_DECAY);
            row++;
        }
    }

    /**
     * Centre panel: the planet in profile with the orbits around it.
     *
     * <p>Orbit radii are scaled against the largest orbit present rather than an absolute scale,
     * so a lone low satellite still fills the display. The planet is drawn at its true relative
     * size, which is why the reference orbit hugs it so closely - 5 km of altitude around a
     * 342.5 km body really is that thin a shell.
     */
    private void drawOrbitView(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL);
        int cx = x + w / 2;
        int cy = y + h / 2;

        double maxRadius = planetRadius;
        for (CosmosNet.SatelliteView v : satellites) {
            maxRadius = Math.max(maxRadius, planetRadius + v.altitude());
        }
        double scale = (Math.min(w, h) * 0.42) / maxRadius;
        int planetPx = Math.max(6, (int) (planetRadius * scale));

        drawDisc(g, cx, cy, planetPx, PLANET);
        drawCircle(g, cx, cy, planetPx, PLANET_RIM);
        // A terminator line, so the planet reads as a body rather than a dot.
        for (int i = -planetPx; i <= planetPx; i++) {
            int span = (int) Math.sqrt(Math.max(0, planetPx * planetPx - i * i));
            g.fill(cx, cy + i, cx + span, cy + i + 1, 0xFF24384C);
        }

        for (int i = 0; i < satellites.size(); i++) {
            CosmosNet.SatelliteView v = satellites.get(i);
            int r = (int) ((planetRadius + v.altitude()) * scale);
            boolean isSelected = i == selected;
            drawCircle(g, cx, cy, r, isSelected ? ORBIT_SELECTED : ORBIT);

            double u = Math.toRadians(v.argumentOfLatitudeDeg() + spin * 0.0);
            int mx = cx + (int) (Math.cos(u) * r);
            int my = cy + (int) (Math.sin(u) * r * Math.cos(Math.toRadians(v.inclinationDeg())));
            int colour = v.decaying() ? MARKER_DECAY : MARKER;
            g.fill(mx - 1, my - 1, mx + 2, my + 2, colour);
            if (isSelected) drawCircle(g, mx, my, 4, colour);
        }
    }

    /** Bottom panel: the ground track, latitude against longitude. */
    private void drawGroundTrack(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL);

        // Graticule: the equator and the prime meridian, plus quarter marks.
        int eqY = y + h / 2;
        g.fill(x, eqY, x + w, eqY + 1, GRID);
        g.fill(x + w / 2, y, x + w / 2 + 1, y + h, GRID);
        for (int i = 1; i < 4; i++) {
            int gx = x + w * i / 4;
            g.fill(gx, y, gx + 1, y + h, 0xFF162230);
        }

        CosmosNet.SatelliteView v = satellites.get(selected);
        int px = x + (int) ((v.longitude() + 180.0) / 360.0 * w);
        int py = y + (int) ((90.0 - v.latitude()) / 180.0 * h);

        // Footprint, to scale against the longitude axis.
        double footprintDeg = Math.toDegrees(Math.atan2(
                v.altitude() * Math.tan(Math.toRadians(30.0)), planetRadius));
        int fr = Math.max(2, (int) (footprintDeg / 360.0 * w));
        drawCircle(g, px, py, fr, 0xFF2F6E5A);

        g.fill(px - 1, py - 1, px + 2, py + 2, MARKER);
        g.text(font, Component.literal(String.format("%+.1f  %+.1f", v.latitude(), v.longitude())),
                x + 3, y + h - 10, TEXT_DIM);
        g.text(font, Component.translatable("cosmos.planetarium.ground_track"), x + 3, y + 2, TEXT_DIM);
    }

    /**
     * Top row, when a pass has been taken: what the satellite saw.
     *
     * <p>Two columns of the same label-over-value rows the telemetry panel uses, because this is
     * the same kind of information and inventing a second visual language for it would only make
     * the console harder to read. Nothing wraps and nothing grows the panel - the ground track
     * below it is fixed - so a list that does not fit reports how much it could not show rather
     * than ending quietly at the panel edge.
     */
    private void drawRecon(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL);
        g.fill(x, y, x + w, y + 11, 0xFF16202C);
        g.text(font, Component.translatable("cosmos.recon.panel.title"), x + 4, y + 2, ACCENT);
        if (reconName != null) {
            // Right-aligned. Butting it up against the title at a fixed offset produced
            // "RECON PASSEye 1" the moment the title was as wide as the offset.
            int nameX = x + w - 4 - font.width(reconName);
            g.text(font, Component.literal(reconName), Math.max(x + 68, nameX), y + 2, TEXT_DIM);
        }

        ReconImager.Report r = recon;

        // Nothing under the track was loaded. Say so loudly and stop - every other field would
        // be a zero that reads like a measurement.
        if (r.sampled() == 0) {
            g.text(font, Component.translatable("cosmos.recon.panel.no_coverage"),
                    x + 4, y + 20, MARKER_DECAY);
            g.text(font, Component.translatable("cosmos.recon.panel.no_coverage_hint"),
                    x + 4, y + 32, TEXT_DIM);
            return;
        }

        int colA = x + 4;
        int colB = x + w / 2 + 2;
        int bottom = y + h;

        reconRow(g, colA, y + 14, "cosmos.recon.panel.centre",
                String.format("%.0f, %.0f", r.centreX(), r.centreZ()), TEXT);
        reconRow(g, colA, y + 32, "cosmos.recon.panel.footprint",
                String.format("r %.0f m", r.footprintRadius()), TEXT);

        boolean partial = r.partial();
        reconRow(g, colB, y + 14, "cosmos.recon.panel.coverage",
                String.format("%.0f%%", r.coverage() * 100.0), partial ? MARKER_DECAY : TEXT);
        reconRow(g, colB, y + 32, "cosmos.recon.panel.worked",
                r.foundConstruction() ? String.valueOf(r.artificialBlocks()) : "--",
                r.foundConstruction() ? ACCENT : TEXT_DIM);

        // Left: what the ground is made of. Right: where the interesting bits are.
        g.text(font, Component.translatable("cosmos.recon.panel.surface"), colA, y + 50, TEXT_DIM);
        List<String> surface = new ArrayList<>();
        r.surfaceComposition().forEach((name, count) -> surface.add(clip(name, 11) + " " + count));
        drawList(g, colA, y + 59, bottom, surface, TEXT);

        g.text(font, Component.translatable("cosmos.recon.panel.returns"), colB, y + 50, TEXT_DIM);
        if (r.strongestSignals().isEmpty()) {
            g.text(font, Component.translatable("cosmos.recon.panel.none"), colB, y + 59, TEXT_DIM);
        } else {
            List<String> returns = new ArrayList<>();
            for (BlockPos pos : r.strongestSignals()) {
                returns.add(pos.getX() + " " + pos.getY() + " " + pos.getZ());
            }
            drawList(g, colB, y + 59, bottom, returns, MARKER);
        }
    }

    /**
     * Draw a list into whatever room is left, and <b>say so</b> when it does not all fit.
     *
     * <p>The panel is 96 px tall and the imager can return five returns against room for four.
     * Ending the column early would read as "that is all there was", which is the one thing a
     * reconnaissance display must never imply - so the last usable line reports the count it
     * could not show instead of quietly being the tail.
     */
    private void drawList(GuiGraphicsExtractor g, int x, int y, int bottom, List<String> rows,
                          int colour) {
        for (int i = 0; i < rows.size(); i++) {
            if (y + 8 > bottom) return;
            int remaining = rows.size() - i;
            if (y + 17 > bottom && remaining > 1) {
                g.text(font, Component.translatable("cosmos.recon.panel.more", remaining),
                        x, y, TEXT_DIM);
                return;
            }
            g.text(font, Component.literal(rows.get(i)), x, y, colour);
            y += 9;
        }
    }

    /** One label-over-value pair, matching the telemetry column's rhythm. */
    private void reconRow(GuiGraphicsExtractor g, int x, int y, String key, String value,
                          int colour) {
        g.text(font, Component.translatable(key), x, y, TEXT_DIM);
        g.text(font, Component.literal(value), x, y + 9, colour);
    }

    /** Block ids are longer than a 93 px column. Cut rather than overrun the panel. */
    private static String clip(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "\u2026";
    }

    /** Right panel: the numbers. */
    private void drawTelemetry(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL);
        CosmosNet.SatelliteView v = satellites.get(selected);

        int line = y + 3;
        g.text(font, Component.literal(v.name()), x + 4, line, ACCENT);
        line += 12;
        g.text(font, Component.literal(v.payload()), x + 4, line, TEXT_DIM);
        line += 13;

        line = telemetry(g, x, line, "cosmos.planetarium.altitude",
                String.format("%.0f m", v.altitude()));
        line = telemetry(g, x, line, "cosmos.planetarium.period",
                String.format("%.0f s", v.periodSeconds()));
        line = telemetry(g, x, line, "cosmos.planetarium.speed",
                String.format("%.0f m/s", v.speed()));
        line = telemetry(g, x, line, "cosmos.planetarium.next_pass",
                v.nextPassSeconds() < 0 ? "--" : String.format("%.0f s", v.nextPassSeconds()));

        if (v.decaying()) {
            g.text(font, Component.translatable("cosmos.planetarium.decaying"), x + 4, line + 1, MARKER_DECAY);
        }
    }

    /**
     * One label-over-value pair. The 17 px pitch is load-bearing: at the 21 px it used to be,
     * four rows put NEXT PASS's value 14 px below the bottom of its own panel, where the ground
     * track was painted over it. The value was drawn every frame and never once seen.
     */
    private int telemetry(GuiGraphicsExtractor g, int x, int y, String key, String value) {
        g.text(font, Component.translatable(key), x + 4, y, TEXT_DIM);
        g.text(font, Component.literal(value), x + 4, y + 9, TEXT);
        return y + 17;
    }

    // ---- primitives -------------------------------------------------------

    /** Midpoint circle, one pixel thick. */
    private static void drawCircle(GuiGraphicsExtractor g, int cx, int cy, int r, int colour) {
        if (r <= 0) return;
        int x = r;
        int y = 0;
        int err = 1 - r;
        while (x >= y) {
            plot(g, cx + x, cy + y, colour); plot(g, cx + y, cy + x, colour);
            plot(g, cx - y, cy + x, colour); plot(g, cx - x, cy + y, colour);
            plot(g, cx - x, cy - y, colour); plot(g, cx - y, cy - x, colour);
            plot(g, cx + y, cy - x, colour); plot(g, cx + x, cy - y, colour);
            y++;
            if (err < 0) {
                err += 2 * y + 1;
            } else {
                x--;
                err += 2 * (y - x) + 1;
            }
        }
    }

    private static void drawDisc(GuiGraphicsExtractor g, int cx, int cy, int r, int colour) {
        for (int dy = -r; dy <= r; dy++) {
            int span = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
            g.fill(cx - span, cy + dy, cx + span + 1, cy + dy + 1, colour);
        }
    }

    private static void plot(GuiGraphicsExtractor g, int x, int y, int colour) {
        g.fill(x, y, x + 1, y + 1, colour);
    }

    @Override
    public boolean isPauseScreen() {
        // A pass window is a few seconds wide. Pausing would make the display a lie.
        return false;
    }
}
