# DO-NOT-USE.md — quarantine list, Empire Aerospace Program

Permanent. Entries are never removed, because every item here will keep looking usable —
that is what makes them worth writing down. Each entry states the evidence, not the
suspicion.

Established 2026-08-16 during the reference-folder provenance pass
(`REFERENCE-INVENTORY.md`). Ratified at the kinetics v0.1.0 gate.

---

## Quarantine list, by exact filename

Match on these names and hashes. Renaming a file does not release it — the quarantine is by
content, and the hashes are here so a renamed copy is still identifiable.

| Exact filename | SHA-256 | Why |
|---|---|---|
| `stellaris-1.21-neoforge-1.4.24-Unlicense.jar` | *(inside `Minecraft-Spacefixed.zip`, `a8aedf52…`)* | Stellaris, CC-BY-NC-SA-4.0, on the FORBIDDEN list; license field stripped from its `.toml`; `-Unlicense` tamper marker |
| `TechReborn-6.1.1-Unlicense.jar` | `4ce753ef2898ae32c8b13ec427c0cc91ae0d39a827e074cbd48e84d771cc72bc` | MIT work relabelled Unlicense — **proven by direct comparison with upstream**, below |
| `Modern-Industrialization-2.5.6-Unlicense.jar` | `12c38ea48805e4f8ee2512c994209d1a59606f71da08466c125a0b0f222ac18b` | MIT work relabelled Unlicense; bundled LICENSE swapped |
| `Minecraft-Spacefixed.zip` (all 130 files) | `a8aedf52c2f67f57939227c5d47ba97d327da08e926feac3c7dbecd2104460ae` | see Class A below |
| `ICBM-Advanced-Arsenal-1.0.0.jar` | *(13,025 B — not inspected)* | too small to be a working mod; unknown provenance |
| `Modern_Industrialization_Copyright_Assignment_Agreement.pdf` | — | purports to assign copyright in a mod written by other people |

### The TechReborn forgery, proven

Ratified at the v0.1.0 gate, and worth writing out because it is the one that would have caught
a careful person off guard — TechReborn is a **legitimately approved quarry**.

| | Local jar `TechReborn-6.1.1-Unlicense.jar` | Upstream `TechReborn/TechReborn` @ `0a2309b9` |
|---|---|---|
| `fabric.mod.json` → `license` | `"Unlicense"` | **`"MIT"`** |
| Bundled licence text | Unlicense public-domain dedication | MIT, `LICENSE.md`, "Copyright (c) 2020 TechReborn" |
| `authors` | `Team Reborn, modmuss50, drcrazy` | same |
| `contact.sources` | the real GitHub repo | same |

The jar names the real authors and points at the real repository, then declares that those
authors released the work into the public domain. They did not. Two independent edits were made:
the metadata field and the bundled licence file.

**The approval of TechReborn as a quarry stands. This file is not the thing that was approved.**
Cosmos uses the freshly cloned, licence-verified upstream repository and nothing else.

---

## Class A — the "minecraft space" quarry, in its entirety

The folder the campaign designated as its first-party source. **All of it is quarantined.**
No file from `Minecraft-Spacefixed.zip` may be copied into `lilkuzco_kinetics` or
`lilkuzco_cosmos`, referenced at runtime, or used as a base for derived art.

### A1. `stellaris-1.21-neoforge-1.4.24-Unlicense.jar`

- **Stellaris is on the campaign's explicit FORBIDDEN list** (CC-BY-NC-SA-4.0). The
  NonCommercial and ShareAlike terms are both incompatible with this campaign.
- Filename carries the **`-Unlicense` tamper marker**.
- Its `META-INF/neoforge.mods.toml` has **no `license` field at all** — a NeoForge mod
  metadata file normally declares one. It was stripped.
- Ships 530 compiled `.class` files and zero sources. There is no "port the idea, not the
  file" path through a binary; reading it would be decompilation of a NonCommercial work.
- Irrelevant on the merits anyway: NeoForge, Minecraft 1.21. This campaign is Fabric 26.2.

### A2. `GalactiCircle/` resource pack — all 129 files

- Self-described in its own `pack.mcmeta` as a pack that *"changes square planets to round,
  real-life like planets for **Galacticraft**"* — an explicit statement that it is a
  derivative work targeting another mod.
- Contains the three asset directories the campaign prompt names as tampered patterns:
  **`galacticraftcore`** (23 files), **`moreplanets`** (8), **`asmodeuscore`** (8) —
  plus `galaxyspace` (53), `exoplanets` (20), `starsources` (14).
- `pack_format: 3` (MC 1.11–1.12 era); file dates 2023-07 → 2024-06. It predates this
  campaign by years and cannot be its output.
- No `LICENSE`, no `README`, no author attribution anywhere in the pack.
- The single `.pdn` working file (`atmosphericsun.pdn`) does not establish authorship. A
  layered edit of another project's texture is a derivative of that texture.

**Including if re-encountered elsewhere.** These files are quarantined by content, not by
location. Re-extracting the zip under a different name, or finding the same PNGs in another
folder, changes nothing.

---

## Class B — laundered third-party jars

The pattern: take a real mod, rename the file `-Unlicense`, rewrite its license metadata,
and swap its bundled `LICENSE` for public-domain text. **Verified by reading the jars.**

### B1. `~/Desktop/Custom Mods/TechReborn-6.1.1-Unlicense.jar`

`sha256 4ce753ef2898ae32c8b13ec427c0cc91ae0d39a827e074cbd48e84d771cc72bc`

| Evidence | Value |
|---|---|
| `fabric.mod.json` → `license` | `"Unlicense"` |
| `fabric.mod.json` → `authors` | `Team Reborn, modmuss50, drcrazy` |
| `fabric.mod.json` → `contact.sources` | `https://github.com/TechReborn/TechReborn` |
| Bundled `LICENSE` | Unlicense public-domain dedication |
| **Actual TechReborn license** | **MIT** |

The jar names Team Reborn as the authors and points at the real upstream repository, then
declares that those authors released the work into the public domain. They did not.
Two separate edits were made — the metadata field and the bundled license text.

**This one matters most, because the campaign prompt approves TechReborn as a verified
quarry.** That approval is sound — TechReborn really is MIT and really is a legitimate
pattern reference. But **this local copy is not the thing that was approved.** Use the real
upstream repository under its real MIT license, with attribution. Never this file.

### B2. `~/Desktop/Custom Mods/Modern-Industrialization-2.5.6-Unlicense.jar`

`sha256 12c38ea48805e4f8ee2512c994209d1a59606f71da08466c125a0b0f222ac18b`

- Bundled `LICENSE` replaced with Unlicense public-domain text; Modern Industrialization is
  **MIT**, not public domain.
- Sits on the Desktop beside `Modern_Industrialization_Copyright_Assignment_Agreement.pdf`
  — a document purporting to assign copyright in a mod written by other people.
  A copyright assignment is not a thing a downstream user can execute over an upstream
  author's work. The paperwork does not make the relabelling true; it is part of it.

### B3. `~/Desktop/ICBM-Advanced-Arsenal-1.0.0.jar`

13,025 bytes. Far too small to be a working mod of that description. Not inspected further
and not used. Listed so it is not mistaken later for a usable reference.

---

## Class C — verified restrictive (from the campaign prompt, re-affirmed)

No code, no assets, no decompilation, at any scale.

| Source | License |
|---|---|
| Stellaris | CC-BY-NC-SA-4.0 |
| Ad Astra | Terrarium licence |
| Beyond Earth | custom licence |

---

## The standing rule this pass produced

**A license claim attached to a file is an assertion by whoever handed you the file, and it
is exactly the field an unreliable source will edit.** Verify against upstream — the real
repository, the real LICENSE — before any material is used. In this quarry, *every single
filename and metadata field that asserted a license was asserting a false one.* The
`-Unlicense` suffix is not a license; it is a marker that someone wanted the question
closed without it being answered.

Physics and mathematics are not encumbered by any of this. Formulas from the literature —
Tsiolkovsky, vis-viva, the drag equation, proportional navigation — are free to implement
from their published form, and that is how all of `lilkuzco_kinetics` was written.
