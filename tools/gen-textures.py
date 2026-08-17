#!/usr/bin/env python3
"""
Generate every cosmos texture procedurally.

All art in this campaign must be original, and the surest way to guarantee that is to compute
it. Nothing here is traced, sampled or adapted: each texture is a small program describing
plate, panel, stripe and rivet, evaluated over a 16x16 grid. Re-run it and you get byte-identical
files, so the textures are reproducible as well as original.

Writes PNGs with nothing but the standard library (zlib + struct), so it needs no dependencies.
"""
import struct, zlib, os, math

S = 16
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "cosmos", "textures")

def png(path, px):
    raw = b"".join(b"\x00" + b"".join(bytes(px[y][x]) for x in range(S)) for y in range(S))
    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
    ihdr = struct.pack(">IIBBBBB", S, S, 8, 6, 0, 0, 0)   # 8-bit RGBA
    blob = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(blob)
    return path

def blank(c=(0, 0, 0, 0)):
    return [[list(c) for _ in range(S)] for _ in range(S)]

def shade(c, f):
    return [max(0, min(255, int(c[0] * f))), max(0, min(255, int(c[1] * f))),
            max(0, min(255, int(c[2] * f))), c[3]]

# A deterministic value hash, so "noise" is reproducible rather than random.
def h(x, y, salt=0):
    n = (x * 374761393 + y * 668265263 + salt * 1442695040888963407) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0

# ---- blocks -------------------------------------------------------------

def plated(base, rivet, seam, seam_every=8):
    """Riveted steel plate: the shared language of every cosmos block."""
    p = blank()
    for y in range(S):
        for x in range(S):
            f = 0.92 + h(x, y, 1) * 0.16
            c = shade(base, f)
            if x % seam_every == 0 or y % seam_every == 0:
                c = shade(seam, 0.95 + h(x, y, 2) * 0.1)
            if (x % seam_every, y % seam_every) in ((2, 2), (5, 5)):
                c = list(rivet)
            p[y][x] = c
    return p

def launch_pad():
    p = plated((92, 98, 108, 255), (150, 158, 168, 255), (60, 66, 74, 255))
    # A hazard chevron across the middle: this is the block you stand clear of.
    for y in range(6, 10):
        for x in range(S):
            band = ((x + y) // 3) % 2 == 0
            p[y][x] = [214, 176, 62, 255] if band else [38, 42, 50, 255]
    for x in range(S):
        p[5][x] = [46, 52, 60, 255]
        p[10][x] = [46, 52, 60, 255]
    return p

def pad_frame():
    p = plated((104, 110, 120, 255), (156, 164, 174, 255), (72, 78, 86, 255), 4)
    # Grating: the apron is a walkway.
    for y in range(S):
        for x in range(S):
            if y % 4 == 2:
                p[y][x] = shade(p[y][x], 0.82)
    return p

def fuel_tank():
    p = blank()
    for y in range(S):
        for x in range(S):
            # Cylindrical shading: bright down the centre, falling to the edges.
            t = abs(x - 7.5) / 7.5
            f = 1.18 - t * t * 0.55
            p[y][x] = shade((66, 70, 78, 255), f + h(x, y, 3) * 0.06)
    for x in range(S):
        p[1][x] = shade((40, 44, 50, 255), 1.0)
        p[14][x] = shade((40, 44, 50, 255), 1.0)
    # A level gauge, because a tank should read as holding something.
    for y in range(3, 13):
        p[y][3] = [26, 30, 36, 255]
        p[y][4] = [92, 190, 140, 255] if y > 6 else [30, 38, 44, 255]
    return p

def satellite_console():
    p = plated((44, 50, 60, 255), (90, 100, 114, 255), (30, 34, 42, 255), 16)
    # A screen showing a planet and one orbit - the block advertises what it does.
    for y in range(3, 13):
        for x in range(3, 13):
            p[y][x] = [12, 18, 26, 255]
    cx, cy = 8, 8
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - cx + 0.5, y - cy + 0.5)
            if 2.2 < d < 3.0 and 3 <= x < 13 and 3 <= y < 13:
                p[y][x] = [72, 132, 168, 255]
            if 4.0 < d < 4.6 and 3 <= x < 13 and 3 <= y < 13:
                p[y][x] = [87, 214, 162, 255]
    p[4][12] = [232, 243, 160, 255]
    return p

# ---- items --------------------------------------------------------------

def rocket(stages, body, accent):
    """A side-on rocket: nose cone, one band per stage, fins."""
    p = blank()
    top = 1
    height = 12
    for y in range(top, top + height):
        halfw = 2 if y > top + 1 else (1 if y > top else 0)
        for x in range(8 - halfw, 8 + halfw + 1):
            f = 1.15 - abs(x - 8) * 0.16
            p[y][x] = shade(body, f)
    # Stage bands, evenly spaced down the body.
    for i in range(stages):
        by = top + 3 + i * (height - 4) // max(stages, 1)
        for x in range(6, 11):
            if p[by][x][3]:
                p[by][x] = list(accent)
    # Nose.
    p[top][8] = [226, 232, 240, 255]
    # Fins.
    for i, y in enumerate(range(top + height - 3, top + height)):
        for x in range(5 - i, 6):
            p[y][x] = shade(accent, 0.8)
        for x in range(10, 11 + i):
            p[y][x] = shade(accent, 0.8)
    # Exhaust glow.
    for x in range(7, 10):
        p[top + height][x] = [232, 150, 70, 255]
    return p

def satellite(colour):
    p = blank()
    # Bus.
    for y in range(6, 10):
        for x in range(6, 10):
            p[y][x] = shade((150, 158, 168, 255), 1.0 - abs(x - 7.5) * 0.06)
    # Solar wings.
    for y in range(6, 10):
        for x in list(range(1, 6)) + list(range(10, 15)):
            band = (x % 2 == 0)
            p[y][x] = list(colour) if band else shade(colour, 0.62)
    # Dish.
    for x in range(7, 9):
        p[4][x] = [226, 232, 240, 255]
    p[5][7] = [200, 208, 218, 255]
    return p

# ---- the Moon ------------------------------------------------------------

def regolith():
    """Fine grey dust. Almost no structure - the Moon is not interesting up close."""
    p = blank()
    for y in range(S):
        for x in range(S):
            n = h(x, y, 11) * 0.5 + h(x // 2, y // 2, 12) * 0.5
            c = 128 + int(n * 26)
            p[y][x] = [c, c - 2, c - 6, 255]
    # A scatter of darker grains, because a perfectly uniform texture reads as plastic.
    for i in range(18):
        gx = int(h(i, 3, 13) * S) % S
        gy = int(h(i, 7, 14) * S) % S
        p[gy][gx] = shade(p[gy][gx], 0.82)
    return p

def mare_basalt():
    """The dark seas. Basalt: cooler, bluer and much darker than regolith."""
    p = blank()
    for y in range(S):
        for x in range(S):
            n = h(x, y, 21) * 0.6 + h(x // 3, y // 3, 22) * 0.4
            c = 52 + int(n * 22)
            p[y][x] = [c, c + 1, c + 6, 255]
    for i in range(10):
        gx = int(h(i, 5, 23) * S) % S
        gy = int(h(i, 9, 24) * S) % S
        p[gy][gx] = shade(p[gy][gx], 1.35)
    return p

def lunar_ice():
    """Polar ice: dirty, not the glassy blue of a frozen ocean."""
    p = blank()
    for y in range(S):
        for x in range(S):
            n = h(x, y, 31)
            c = 176 + int(n * 34)
            p[y][x] = [c - 8, c - 2, c + 12, 255]
    for i in range(14):
        gx = int(h(i, 11, 32) * S) % S
        gy = int(h(i, 13, 33) * S) % S
        p[gy][gx] = shade(p[gy][gx], 0.86)
    return p

def oxygen_station():
    """A life-support block: white plate, a green level bar, a valve."""
    p = plated((196, 200, 206, 255), (232, 236, 242, 255), (150, 156, 164, 255), 16)
    for y in range(4, 12):
        p[y][2] = [40, 46, 52, 255]
        p[y][3] = [92, 206, 148, 255]
        p[y][4] = [40, 46, 52, 255]
    # Valve wheel.
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - 10.5, y - 7.5)
            if 2.4 < d < 3.2:
                p[y][x] = [120, 128, 138, 255]
            if d < 1.1:
                p[y][x] = [70, 78, 88, 255]
    return p

def oxygen_tank_item():
    """A portable tank: cylinder with a valve and a charge stripe."""
    p = blank()
    for y in range(3, 14):
        for x in range(5, 11):
            t = abs(x - 7.5) / 2.5
            p[y][x] = shade((104, 176, 214, 255), 1.16 - t * t * 0.5)
    for x in range(5, 11):
        p[3][x] = [58, 96, 122, 255]
        p[13][x] = [58, 96, 122, 255]
    for y in range(5, 12):
        p[y][6] = [232, 244, 250, 255]
    p[2][7] = [150, 158, 168, 255]
    p[2][8] = [150, 158, 168, 255]
    p[1][7] = [110, 118, 128, 255]
    return p

def rocket_entity_sheet():
    """The rocket's own 64x64 entity sheet.

    A ModelPart model addresses its own sheet, not a slot on the block atlas - the second
    half of the lesson crude_empire paid for. Every UV region here matches RocketModel
    exactly; a mismatch is a silent smear rather than an error.
    """
    W = H = 64
    p = [[[0, 0, 0, 0] for _ in range(W)] for _ in range(H)]

    def rect(x0, y0, w, h, c):
        for y in range(y0, min(y0 + h, H)):
            for x in range(x0, min(x0 + w, W)):
                p[y][x] = list(c)

    HULL = (222, 226, 233, 255)
    SHADE = (170, 176, 188, 255)
    DARK = (96, 102, 114, 255)
    BAND = (198, 62, 48, 255)
    NOSE = (238, 241, 247, 255)
    BELL = (70, 74, 84, 255)
    SOOT = (40, 42, 48, 255)

    # Body 8x40x8 at (0,0): unwrap 2*(8+8)=32 wide, 8+40=48 tall. Sides occupy rows 8..47.
    rect(0, 0, 32, 48, HULL)
    rect(0, 0, 32, 8, SHADE)                 # cap row
    for x in range(0, 32, 8):                # panel seams between the four faces
        rect(x, 8, 1, 40, SHADE)
    rect(0, 12, 32, 4, BAND)                 # upper stripe - makes the roll visible
    rect(0, 30, 32, 2, DARK)
    rect(0, 40, 32, 3, BAND)                 # lower stripe
    rect(0, 44, 32, 4, DARK)                 # scorched skirt above the bell

    # Interstage 9x3x9 at (0,48): 36 wide, 12 tall.
    rect(0, 48, 36, 12, DARK)
    rect(0, 51, 36, 4, SOOT)

    # Nose shoulder 5x7x5 at (36,48): 20 wide, 12 tall.
    rect(36, 48, 20, 12, NOSE)
    rect(36, 48, 20, 5, SHADE)

    # Nose tip 2x5x2 at (32,0): 8 wide, 7 tall.
    rect(32, 0, 8, 7, BAND)

    # Bell 5x4x5 at (32,8): 20 wide, 9 tall.
    rect(32, 8, 20, 9, BELL)
    rect(32, 13, 20, 4, SOOT)

    # Fin 1x12x6 at (32,20): 14 wide, 18 tall.
    rect(32, 20, 14, 18, SHADE)
    rect(32, 26, 14, 12, HULL)
    rect(32, 33, 14, 5, BAND)
    return p


def capsule_entity_sheet():
    """The capsule's own 64x64 sheet. UV regions match CapsuleModel exactly."""
    W = H = 64
    p = [[[0, 0, 0, 0] for _ in range(W)] for _ in range(H)]

    def rect(x0, y0, w, h, c):
        for y in range(y0, min(y0 + h, H)):
            for x in range(x0, min(x0 + w, W)):
                p[y][x] = list(c)

    SHIELD = (58, 48, 44, 255)
    CHAR = (34, 28, 26, 255)
    HULL = (206, 210, 218, 255)
    SHADE = (158, 164, 176, 255)
    COLLAR = (110, 116, 128, 255)
    CANOPY_A = (232, 236, 244, 255)
    CANOPY_B = (214, 84, 66, 255)
    CORD = (176, 168, 150, 255)

    # Heat shield 10x3x10 at (0,0): 40 wide, 13 tall. Charred, because it has been used.
    rect(0, 0, 40, 13, SHIELD)
    rect(0, 0, 40, 10, CHAR)

    # Hull 8x5x8 at (0,13): 32 wide, 13 tall.
    rect(0, 13, 32, 13, HULL)
    rect(0, 13, 32, 8, SHADE)
    for x in range(0, 32, 8):
        rect(x, 21, 1, 5, SHADE)

    # Collar 6x2x6 at (0,26): 24 wide, 8 tall.
    rect(0, 26, 24, 8, COLLAR)

    # Canopy 16x4x16 at (0,34): 64 wide, 20 tall. Alternating gores, as real chutes are.
    rect(0, 34, 64, 20, CANOPY_A)
    for x in range(0, 64, 8):
        rect(x, 34, 4, 20, CANOPY_B)

    # Riser 1x12x1 at (0,54): 4 wide, 13 tall.
    rect(0, 54, 4, 13, CORD)
    return p


def electrolyser():
    """A cryo plant: pale metal, a blue cell, and frost at the base."""
    p = blank()
    for y in range(16):
        for x in range(16):
            p[y][x] = [176, 184, 196, 255]
    for y in range(2, 12):
        for x in range(3, 13):
            p[y][x] = [140, 150, 164, 255]
    # The electrolysis cell, glowing cold.
    for y in range(4, 10):
        for x in range(5, 11):
            p[y][x] = [96, 168, 220, 255]
    for y in range(5, 9):
        for x in range(6, 10):
            p[y][x] = [150, 210, 244, 255]
    # Bubbles rising: hydrogen on one side, oxygen on the other.
    p[5][6] = [232, 244, 252, 255]
    p[7][9] = [232, 244, 252, 255]
    # Frost line.
    for x in range(2, 14):
        p[13][x] = [214, 230, 240, 255]
    return p


def regolith_kiln():
    """A bake oven: grey shell, a hot orange mouth."""
    p = blank()
    for y in range(16):
        for x in range(16):
            p[y][x] = [150, 146, 140, 255]
    for y in range(2, 14):
        for x in range(2, 14):
            p[y][x] = [124, 120, 114, 255]
    for y in range(6, 12):
        for x in range(4, 12):
            p[y][x] = [196, 104, 42, 255]
    for y in range(7, 11):
        for x in range(5, 11):
            p[y][x] = [236, 160, 62, 255]
    for x in range(6, 10):
        p[9][x] = [250, 214, 130, 255]
    # Flue.
    for y in range(2, 5):
        p[y][12] = [96, 92, 88, 255]
    return p


def sintered_regolith():
    """Baked dust, holding together: pale blocks with dark mortar."""
    p = blank()
    for y in range(16):
        for x in range(16):
            p[y][x] = [186, 182, 174, 255]
    for x in range(16):
        p[5][x] = [148, 144, 138, 255]
        p[11][x] = [148, 144, 138, 255]
    for y in range(0, 5):
        p[y][7] = [148, 144, 138, 255]
    for y in range(6, 11):
        p[y][12] = [148, 144, 138, 255]
    for y in range(12, 16):
        p[y][3] = [148, 144, 138, 255]
    # A little grain so it does not read as flat.
    for (y, x) in ((2, 3), (3, 11), (8, 5), (9, 14), (13, 8), (14, 1)):
        p[y][x] = [204, 200, 192, 255]
    return p


def lander_item():
    """A descent stage: a squat body on four splayed legs, with a nozzle underneath."""
    p = blank()
    # Body.
    for y in range(4, 10):
        for x in range(4, 12):
            p[y][x] = [188, 194, 204, 255]
    for y in range(5, 9):
        for x in range(5, 11):
            p[y][x] = [214, 220, 230, 255]
    # A gold-foil band, because every lander ever built has one.
    for x in range(4, 12):
        p[9][x] = [206, 168, 60, 255]
    # Nozzle.
    for y in range(10, 12):
        for x in range(7, 9):
            p[y][x] = [96, 100, 110, 255]
    p[12][7] = [70, 74, 82, 255]
    p[12][8] = [70, 74, 82, 255]
    # Legs, splayed out and down.
    for i, (lx, rx) in enumerate(((3, 12), (2, 13), (1, 14))):
        y = 10 + i
        p[y][lx] = [150, 156, 166, 255]
        p[y][rx] = [150, 156, 166, 255]
    for x in (0, 1, 14, 15):
        p[13][x] = [130, 136, 146, 255]
    return p


def suit_item():
    """A pressure suit: helmet with a gold visor over a white torso."""
    p = blank()
    for y in range(2, 7):
        for x in range(5, 11):
            p[y][x] = [226, 230, 236, 255]
    for y in range(3, 6):
        for x in range(6, 10):
            p[y][x] = [214, 176, 62, 255]
    p[3][6] = [244, 220, 150, 255]
    for y in range(7, 14):
        for x in range(4, 12):
            p[y][x] = [206, 212, 220, 255]
    for y in range(8, 13):
        p[y][4] = [150, 158, 168, 255]
        p[y][11] = [150, 158, 168, 255]
    for x in range(6, 10):
        p[9][x] = [92, 206, 148, 255]
    return p

written = []
written.append(png(os.path.join(OUT, "block", "launch_pad.png"), launch_pad()))
written.append(png(os.path.join(OUT, "block", "pad_frame.png"), pad_frame()))
written.append(png(os.path.join(OUT, "block", "fuel_tank.png"), fuel_tank()))
written.append(png(os.path.join(OUT, "block", "satellite_console.png"), satellite_console()))
written.append(png(os.path.join(OUT, "item", "rocket_sounding.png"), rocket(1, (188, 196, 206, 255), (214, 96, 72, 255))))
written.append(png(os.path.join(OUT, "item", "rocket_orbital.png"), rocket(2, (196, 204, 214, 255), (87, 214, 162, 255))))
written.append(png(os.path.join(OUT, "item", "rocket_heavy.png"), rocket(3, (172, 180, 192, 255), (214, 176, 62, 255))))
written.append(png(os.path.join(OUT, "item", "rocket_lunar.png"), rocket(3, (222, 228, 236, 255), (108, 148, 224, 255))))
written.append(png(os.path.join(OUT, "item", "satellite_recon.png"), satellite((72, 132, 200, 255))))
written.append(png(os.path.join(OUT, "item", "satellite_comms.png"), satellite((87, 190, 150, 255))))
written.append(png(os.path.join(OUT, "block", "regolith.png"), regolith()))
written.append(png(os.path.join(OUT, "block", "mare_basalt.png"), mare_basalt()))
written.append(png(os.path.join(OUT, "block", "lunar_ice.png"), lunar_ice()))
written.append(png(os.path.join(OUT, "block", "oxygen_station.png"), oxygen_station()))
written.append(png(os.path.join(OUT, "item", "oxygen_tank.png"), oxygen_tank_item()))
written.append(png(os.path.join(OUT, "item", "pressure_suit.png"), suit_item()))
written.append(png(os.path.join(OUT, "item", "lunar_lander.png"), lander_item()))
os.makedirs(os.path.join(OUT, "entity"), exist_ok=True)
written.append(png(os.path.join(OUT, "entity", "rocket.png"), rocket_entity_sheet()))
written.append(png(os.path.join(OUT, "entity", "capsule.png"), capsule_entity_sheet()))
written.append(png(os.path.join(OUT, "block", "electrolyser.png"), electrolyser()))
written.append(png(os.path.join(OUT, "block", "regolith_kiln.png"), regolith_kiln()))
written.append(png(os.path.join(OUT, "block", "sintered_regolith.png"), sintered_regolith()))

for w in written:
    print("wrote", os.path.relpath(w, os.path.join(os.path.dirname(__file__), "..")))
