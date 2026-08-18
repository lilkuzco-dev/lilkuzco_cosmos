# ASSETS-ORIGIN.md — Empire Aerospace Program

Every asset and every ported code idea used by `lilkuzco_kinetics` or `lilkuzco_cosmos` is
logged here with its source. An empty section is a claim, not an oversight: it asserts that
nothing was taken.

Last updated 2026-08-16 (kinetics v0.1.0).

---

## Taken from `~/Desktop/minecraft space`

**Nothing. Zero files, zero code patterns, zero art.**

The folder was inventoried before any code was written. It contains no source code at all,
and all 130 of its files are third-party material matching the campaign's own tampered
patterns. It is quarantined in full — see `REFERENCE-INVENTORY.md` for the inventory and
`DO-NOT-USE.md` for the per-item evidence.

The campaign's "first-party quarry" therefore resolved to the empty set, and the
instruction to prefer it as a source for cosmos art and kinetics code patterns had no valid
input to draw on.

---

## Verified external quarries

Cloned fresh from their real repositories at the v0.1.0 gate, licence read **in-repo** rather
than taken on trust. Held read-only at `~/Desktop/aerospace/quarry/`; never edited, never
depended on at runtime.

| Repository | Commit | Licence file | Verified | Standing |
|---|---|---|---|---|
| `github.com/TechReborn/TechReborn` | `0a2309b947782fc0c43cc196b28c41dbc5289e54` | `LICENSE.md` | **MIT** — full text present; upstream `fabric.mod.json` also declares `"license": "MIT"` | Pattern reference for cosmos machine/block code |
| `github.com/TeamGalacticraft/Galacticraft-Legacy` | `acec4429525eb30452298c2034d1e5d5d4b41046` | `LICENSE` | **MIT** — "Copyright (c) 2022 Team Galacticraft" | **Design reference only.** Ancient code; port ideas, never files |

Reading upstream also settled the tampering question definitively: upstream TechReborn declares
`"license": "MIT"`, so the local jar's `"Unlicense"` is a forgery by direct comparison, not by
inference. See `DO-NOT-USE.md`.

**Nothing has been taken from either yet.** Entries appear below as they are.

### Taken from TechReborn

*(nothing yet)*

### Taken from Galacticraft-Legacy

*(nothing yet)*

---

## `lilkuzco_kinetics` v0.1.0 — origin of contents

| Category | Origin |
|---|---|
| Source code | **100% original**, written for this campaign |
| Art / textures / audio | **None exist.** Kinetics is a headless physics library with no player-facing content by design |
| Third-party runtime dependencies | **None.** `kinetics-core` is pure Java 25 on the standard library; the Fabric adapter depends only on Fabric Loader + Fabric API |

### Physics: implemented from published science, not from anyone's code

Every model in `physics-constants.json` and the sim core was implemented from its published
mathematical form. No implementation was consulted, decompiled or adapted. Mathematical
formulas are not copyrightable subject matter, and each constant carries a `source_note`
recording the physical origin of its value.

| Model | Published form implemented | Where |
|---|---|---|
| Semi-implicit (symplectic) Euler | standard numerical-integration literature | `Integrator.java` |
| Standard gravity `g0 = 9.80665 m/s²` | CGPM 1901 datum | constants |
| Exponential atmosphere `ρ = ρ₀·e^(−h/H)` | US Standard Atmosphere 1976 | `Atmosphere.java` |
| Quadratic drag `F_d = ½ρv²C_dA` | Rayleigh drag equation | `Aerodynamics.java` |
| Lift `F_L = ½ρv²C_LA`, thin-airfoil `2π/rad` | thin-airfoil theory | `Aerodynamics.java` |
| Induced drag `C_D = C_D0 + C_L²/(πARe)` | Prandtl lifting-line theory | `DragPolar.java` |
| Transonic drag rise | Prandtl–Glauert, as documented inspiration only | `Compressibility.java` |
| Proportional navigation `a = N·V_c·λ̇` | standard guidance literature | `ProportionalNavigation.java` |
| Tsiolkovsky `Δv = Isp·g0·ln(m0/mf)` | Tsiolkovsky 1903 | `Propulsion.java` |
| Vis-viva `v² = μ(2/r − 1/a)` | classical two-body mechanics | `OrbitalMechanics.java` |
| Radar range `R ∝ σ^(1/4)` | radar range equation | `Radar.java` |
| Reentry heating `q̇ ∝ ρ·v³` | Sutton–Graves form, simplified | `Reentry.java` |

Reference coefficient values (C_d of a sphere ≈ 0.47, Isp of kerolox ≈ 311 s, LEO Δv budget
≈ 9,400 m/s, and so on) are published physical measurements of the real world. They are
recorded in `physics-constants.json` with SI units and a source note each, per invariant I9.

---

## `lilkuzco_cosmos` v0.1.0-A — origin of contents

| Category | Origin |
|---|---|
| Source code | **100% original**, written for this campaign |
| Textures | **100% original, generated procedurally** by `tools/gen-textures.py` |
| GUI art | **100% original, drawn at runtime** — no texture files at all |
| Third-party runtime dependencies | kinetics (own), Fabric API. Nothing else |

### Textures — computed, not drawn

All nine textures (4 blocks, 5 items) are produced by a ~150-line pure-Python program that
evaluates plate, panel, stripe, rivet and cylinder-shading functions over a 16×16 grid and writes
the PNG with `zlib` and `struct` alone. Nothing is traced, sampled or adapted from any source, and
re-running the generator reproduces the files byte for byte. Originality here is structural rather
than asserted: the art is a program, and the program is in the repository.

### The planetarium — drawn at runtime

`PlanetariumScreen` and `LaunchPadScreen` use **no texture files whatsoever**. Every pixel is a
filled rectangle or a computed circle: the planet is a shaded disc, orbits are midpoint circles
scaled by real semi-major axes, the ground track is a graticule with a footprint drawn to scale.
That is partly a licensing decision — a screen composed entirely of primitives cannot be anyone
else's work — and partly the right tool, since an orbit's shape depends on live data no texture
could carry.

### Taken from the verified quarries

**Nothing.** TechReborn and Galacticraft-Legacy were cloned and licence-verified at the v0.1.0
gate and remain available as references, but Phase A needed neither: cosmos' machines are two
block entities and a container menu, and its space mechanics come from kinetics. If Phase B takes
a pattern from either, it is logged here with the file and the commit.

---

## Log format for future entries

```
### <asset or pattern>
- Source path: <exact path or upstream URL>
- License: <verified how, from what file>
- Used in: <repo/file>
- Modification: <verbatim copy | derived | idea only>
```
