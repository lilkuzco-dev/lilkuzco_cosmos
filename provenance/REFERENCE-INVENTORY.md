# REFERENCE-INVENTORY.md — the "minecraft space" quarry

Campaign: Empire Aerospace Program. Pass run 2026-08-16, before any code was written,
per the LOCAL REFERENCE FOLDER rule.

## Finding in one line

**The folder contains no first-party material. It contains no source code of any kind.
Every one of its 130 files is third-party, and every one matches a pattern the campaign
prompt itself marks as tampered. The quarry yielded nothing and is quarantined.**

## What was actually inspected

`~/Desktop/minecraft space` **does not exist.** The only artifact matching it is:

| Path | Size | SHA-256 | Created |
|---|---|---|---|
| `~/Desktop/Minecraft-Spacefixed.zip` | 9,685,766 B | `a8aedf52c2f67f57939227c5d47ba97d327da08e926feac3c7dbecd2104460ae` | 2026-08-16 16:37 |

It expands to a single root folder `Minecraft Space/`, which is what the prompt refers to.
The archive was inspected **in place** (`unzip -l` / `unzip -p` into the session scratchpad);
nothing was extracted to the Desktop and nothing in the archive was modified — the
read-only rule was honoured even though the folder had to be read out of an archive.

## Complete inventory — 130 files, 2 top-level entries

### 1. `Minecraft Space/stellaris-1.21-neoforge-1.4.24-Unlicense.jar`

| Field | Value |
|---|---|
| Size | 9,256,886 B (86% of the whole quarry) |
| Type | Compiled NeoForge mod binary — **530 `.class` files**, no sources |
| Declared mod id | `stellaris` v1.4.24 |
| Upstream | `github.com/st0x0ef/Stellaris` (from its own `issueTrackerURL`) |
| Loader / MC | NeoForge `[4,)`, Minecraft `[1.21,)` |
| License field in `META-INF/neoforge.mods.toml` | **absent — stripped** |
| Real upstream license | **CC-BY-NC-SA-4.0** |

**Disposition: DO-NOT-USE.** Three independent reasons, any one sufficient:
Stellaris is on the campaign's explicit FORBIDDEN list; the filename carries the
`-Unlicense` tamper marker; the license declaration has been removed from its metadata.
It is also NeoForge 1.21, so it is not even loadable by this campaign's Fabric 26.2 target.

### 2. `Minecraft Space/GalactiCircle/` — 129 files

A **third-party resource pack**, not our artwork. Its own `pack.mcmeta` states its purpose
in its own words:

> `"Pack changes square planets to round, real-life like planets for Galacticraft"`

`pack_format: 3` dates it to the Minecraft 1.11–1.12 era; file mtimes run 2023-07 to
2024-06, years before this campaign existed.

| Contents | Count |
|---|---|
| `.png` textures | 128 |
| `.pdn` (Paint.NET working file) | 1 — `assets/galacticraftcore/textures/gui/planets/atmosphericsun.pdn` |
| `pack.mcmeta` | 1 |
| **Source code** | **0** |

Every texture is filed under a third-party mod's asset namespace. There is no namespace
in this pack that belongs to us:

| Asset namespace | Files | Note |
|---|---|---|
| `galaxyspace` | 53 | third-party addon |
| `galacticraftcore` | 23 | **named tampered pattern in the prompt** |
| `exoplanets` | 20 | third-party addon |
| `starsources` | 14 | third-party addon |
| `moreplanets` | 8 | **named tampered pattern in the prompt** |
| `asmodeuscore` | 8 | **named tampered pattern in the prompt** |
| `minecraft` | 2 | vanilla namespace override |

A texture written into `assets/galacticraftcore/textures/gui/celestialbodies/venus.png` is
by construction a replacement for Galacticraft's own asset — it is a derivative keyed to
that mod's asset tree. The `.pdn` working file does not establish authorship either; a
layered source file for a retexture of someone else's asset is still a derivative of it.

**Disposition: DO-NOT-USE, in full.** The prompt quarantines `galacticraftcore`,
`moreplanets` and `asmodeuscore` by name and quarantines "ripped asset dirs" as a class.
This pack is nothing but those.

## Adjacent artifacts inspected in the same pass

Not part of the named folder, but they are the same operation and the campaign's Legal
Doctrine reaches them. Both are laundered — see `DO-NOT-USE.md` for the evidence.

| Path | Marker |
|---|---|
| `~/Desktop/Custom Mods/TechReborn-6.1.1-Unlicense.jar` | metadata forged MIT → Unlicense |
| `~/Desktop/Custom Mods/Modern-Industrialization-2.5.6-Unlicense.jar` | bundled LICENSE swapped for Unlicense text |
| `~/Desktop/ICBM-Advanced-Arsenal-1.0.0.jar` | 13,025 B — too small to be a functioning mod; not inspected further, not used |
| `~/Desktop/Modern_Industrialization_Copyright_Assignment_Agreement.pdf` | a document purporting to assign copyright in a mod authored by others |

## Consequence for the campaign

The prompt's **FIRST-PARTY QUARRY** clause resolves to the empty set. Concretely:

- **Kinetics is unaffected.** It is a physics library containing no art and no ported code.
  Newtonian integration, the drag equation, proportional navigation, Tsiolkovsky and
  vis-viva are textbook physics, not anyone's intellectual property. Part One proceeds at
  full scope from Section R alone.
- **Cosmos art has no head start.** When Part Two is approved, every texture must be
  original work. The "art first from the minecraft space folder where present" instruction
  has no valid input to draw on.
- **`ASSETS-ORIGIN.md` records zero takings**, which is the correct and complete result of
  this pass, not an omission.

## Method note

Provenance was established from the artifacts themselves — archive listings, `pack.mcmeta`,
`neoforge.mods.toml`, bundled `LICENSE` text, `fabric.mod.json` — not from filenames and
not from any claim made about the folder. That is the point of the pass: a filename is an
assertion, and in this quarry every filename asserting a license was asserting a false one.
