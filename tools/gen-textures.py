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

written = []
written.append(png(os.path.join(OUT, "block", "launch_pad.png"), launch_pad()))
written.append(png(os.path.join(OUT, "block", "pad_frame.png"), pad_frame()))
written.append(png(os.path.join(OUT, "block", "fuel_tank.png"), fuel_tank()))
written.append(png(os.path.join(OUT, "block", "satellite_console.png"), satellite_console()))
written.append(png(os.path.join(OUT, "item", "rocket_sounding.png"), rocket(1, (188, 196, 206, 255), (214, 96, 72, 255))))
written.append(png(os.path.join(OUT, "item", "rocket_orbital.png"), rocket(2, (196, 204, 214, 255), (87, 214, 162, 255))))
written.append(png(os.path.join(OUT, "item", "rocket_heavy.png"), rocket(3, (172, 180, 192, 255), (214, 176, 62, 255))))
written.append(png(os.path.join(OUT, "item", "satellite_recon.png"), satellite((72, 132, 200, 255))))
written.append(png(os.path.join(OUT, "item", "satellite_comms.png"), satellite((87, 190, 150, 255))))

for w in written:
    print("wrote", os.path.relpath(w, os.path.join(os.path.dirname(__file__), "..")))
