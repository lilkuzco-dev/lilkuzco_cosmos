package dev.lilkuzco.cosmos.client;

import dev.lilkuzco.cosmos.satellite.CosmosNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;

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

    private final BlockPos console;
    private List<CosmosNet.SatelliteView> satellites;
    private double planetRadius;
    private int selected;
    private float spin;

    // The client receiver needs to know whether a planetarium is already open so a refresh
    // updates it in place instead of reopening it and resetting the selection.
    private static PlanetariumScreen open;

    public static PlanetariumScreen open() { return open; }

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
        rebuildButtons();
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

        int x = width / 2 - 158;
        int y = height / 2 + 78;

        addRenderableWidget(Button.builder(Component.translatable("cosmos.planetarium.image"),
                b -> send(CosmosNet.ConsoleActionC2S.ACTION_IMAGE)).bounds(x, y, 96, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("cosmos.planetarium.deorbit"),
                b -> send(CosmosNet.ConsoleActionC2S.ACTION_DEORBIT))
                .bounds(x + 100, y, 96, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("cosmos.planetarium.refresh"),
                b -> send(CosmosNet.ConsoleActionC2S.ACTION_REFRESH))
                .bounds(x + 200, y, 96, 20).build());

        // Selection by button rather than by clicking a row: at this scale the roster rows are
        // 20 px tall and a mis-click would command a deorbit on the wrong satellite.
        addRenderableWidget(Button.builder(Component.literal("<"),
                b -> cycle(-1)).bounds(x, y - 24, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"),
                b -> cycle(1)).bounds(x + 22, y - 24, 20, 20).build());
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

        int left = width / 2 - 160;
        int top = height / 2 - 100;
        int w = 320;
        int h = 200;

        g.fill(left, top, left + w, top + h, BACKDROP);
        g.fill(left, top, left + w, top + 14, PANEL);
        g.text(font, Component.translatable("cosmos.planetarium.title"), left + 6, top + 4, ACCENT);
        g.text(font, Component.translatable("cosmos.planetarium.count", satellites.size()), left + w - 60, top + 4, TEXT_DIM);

        if (satellites.isEmpty()) {
            g.text(font, Component.translatable("cosmos.planetarium.empty"), left + 12, top + 40, TEXT_DIM);
                return;
        }

        drawRoster(g, left + 4, top + 18, 108, h - 26);
        drawOrbitView(g, left + 118, top + 18, 96, 96);
        drawGroundTrack(g, left + 118, top + 118, 196, 62);
        drawTelemetry(g, left + 220, top + 18, 96, 96);
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

    /** Right panel: the numbers. */
    private void drawTelemetry(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL);
        CosmosNet.SatelliteView v = satellites.get(selected);

        int line = y + 4;
        g.text(font, Component.literal(v.name()), x + 4, line, ACCENT);
        line += 12;
        g.text(font, Component.literal(v.payload()), x + 4, line, TEXT_DIM);
        line += 14;

        line = telemetry(g, x, line, "cosmos.planetarium.altitude",
                String.format("%.0f m", v.altitude()));
        line = telemetry(g, x, line, "cosmos.planetarium.period",
                String.format("%.0f s", v.periodSeconds()));
        line = telemetry(g, x, line, "cosmos.planetarium.speed",
                String.format("%.0f m/s", v.speed()));
        line = telemetry(g, x, line, "cosmos.planetarium.next_pass",
                v.nextPassSeconds() < 0 ? "--" : String.format("%.0f s", v.nextPassSeconds()));

        if (v.decaying()) {
            g.text(font, Component.translatable("cosmos.planetarium.decaying"), x + 4, line + 4, MARKER_DECAY);
        }
    }

    private int telemetry(GuiGraphicsExtractor g, int x, int y, String key, String value) {
        g.text(font, Component.translatable(key), x + 4, y, TEXT_DIM);
        g.text(font, Component.literal(value), x + 4, y + 9, TEXT);
        return y + 21;
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
