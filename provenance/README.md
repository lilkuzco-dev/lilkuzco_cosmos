# provenance — where the campaign's assets came from, and what was refused

These three documents are the **legal record for the whole aerospace campaign**: what was used,
what was verified, and what was quarantined and why.

They lived in `~/Desktop/aerospace/` — a plain folder, not a repository. Unversioned, unbacked-up,
on one disk, with no history. Both `lilkuzco_kinetics` and `lilkuzco_cosmos` linked to
`../ASSETS-ORIGIN.md`, a path that exists on exactly one machine and resolves for nobody who
clones either repo.

That is a bad place for the only copy of a licensing record, so this is now the authoritative one.

| Document | What it is |
|---|---|
| `ASSETS-ORIGIN.md` | Verified quarries with recorded commits (TechReborn MIT `0a2309b`, Galacticraft-Legacy `acec442`), plus the origin sections for kinetics and cosmos and the procedural-art declaration |
| `DO-NOT-USE.md` | The quarantine list: exact filenames and SHA-256s, including the TechReborn forgery proof where a local jar substitutes an Unlicense text over upstream's MIT |
| `REFERENCE-INVENTORY.md` | The inventory of the local reference folder that turned out to contain 130 files and zero usable source |

## Why these matter more than they look

`ASSETS-ORIGIN.md` is what makes the 63 MB of `quarry/` **disposable**. Both clones are pinned to
recorded commits, so they can be re-fetched at any time — as long as this file survives. It is the
only thing standing between "reproducible" and "gone".

`DO-NOT-USE.md` is a standing refusal, not a note. The jars it names are never to be opened, and
the reason is recorded so no future session re-derives it from scratch or talks itself out of it.

## The working copies

The originals remain in `~/Desktop/aerospace/` because other sessions may be reading them and
deleting another session's files is not this session's call. **This copy is authoritative.** If the
two ever disagree, that folder is a stale local scratch copy and this one is the record.
