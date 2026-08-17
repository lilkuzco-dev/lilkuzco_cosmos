# ORBITAL-ARMS.md — weapons in orbit

> # ⛔ SEALED — HELD BY RULING, DO NOT BUILD
>
> **Ruled at the v0.1.0-D gate: HOLD. Do not build. Keep this as the sealed design.**
>
> This is an escalation to be ruled on after the server has lived a while. The aerospace program is
> complete without it.
>
> **No part of this document may be implemented without an explicit new ruling** — not the orbital
> platform, not the impactor, not the targeting integration, not "just the event contract". A
> sealed design is sealed at its cheapest end too, because that is the end that gets built by
> accident.
>
> It is kept because the analysis is worth keeping: most of the capability already exists, and
> knowing exactly how close it is, is itself the reason to be deliberate about it.

**Phase C. Design only: nothing here is implemented, and by ruling nothing here may be.**

This is the document where cosmos and warfront meet, and it is worth saying up front that **most
of it already exists**. Kinetics was built as the empire's motion authority, not as a space
library, and its guidance chain has been sitting there since v0.1.0 waiting for something to point
at the ground.

## What is already built and tested

| Capability | Where | Status |
|---|---|---|
| True 3D proportional navigation, `a = N·V_c·(ω × û)` | kinetics RC1–RC3 | shipped, golden-hashed |
| Seeker FOV, lock state machine, terrain occlusion | kinetics RC6 | shipped |
| Radar with the fourth-root range law `R ∝ σ^(1/4)` | kinetics RF1 | shipped |
| Proximity fuse solving closest approach *within* a tick | kinetics | shipped |
| Reentry heating, blunt-body deceleration | kinetics RE7 | shipped |
| Orbital registry, pass prediction, ground tracks | kinetics RE1–RE5 | shipped |
| **Aimed deorbit to a chosen ground point** | cosmos `RecoveryCapsule` | **shipped in Phase A** |

That last row is the important one. Cosmos already flies a payload from orbit to a **chosen
latitude and longitude**, by rehearsing the entry headlessly and placing the deorbit burn so the
capsule lands where it was aimed. A recovery capsule and a kinetic weapon differ in what is inside
them and in nothing else.

## The one law this must not break

**I10: kinetics never applies damage.** It reports a `Proximity` or an `Impact` — a position, a
velocity, a mass — and the consumer decides what that means. The sealed event hierarchy is a
structural guarantee, not a convention: there is no handle in `WorldProbe` through which damage
could be applied even by accident.

So an orbital weapon is not a kinetics feature. It is a **cosmos consumer of a kinetics impact**,
and warfront is the mod that already knows what an explosion should do to a base.

## Kinetic bombardment: the numbers

A deorbited mass arrives at the Karman line at **1,637 m/s** — measured, not assumed, by the Phase A
deorbit verification.

| Impactor | Arrival energy | TNT equivalent |
|---|---|---|
| 100 kg | 1.34 × 10⁸ J | **32 kg** |
| 1,000 kg | 1.34 × 10⁹ J | **320 kg** |
| 10,000 kg | 1.34 × 10¹⁰ J | **3.2 tonnes** |

For scale, a Minecraft TNT block is on the order of a kilogram of TNT. **A one-tonne rod arrives
with the energy of roughly three hundred TNT.** That is a strategic weapon, and it should be
priced like one: the orbital tier lifts 100 kg, the heavy 400 kg, and the lunar vehicle 500 kg. A
ten-tonne impactor is not something you launch — it is something you *assemble in orbit* over many
flights, which is a far better story than a recipe.

**The delivery is already honest.** The rod does not teleport; it is a kinetics body that must be
deorbited, survive reentry heating, and arrive. It can be aimed and it can miss.

## Design sketch

### 1. The orbital platform

A satellite payload alongside `RECON` and `COMMS`. It holds impactors and nothing else — no
sensors, no comms — so a weapons platform is a distinct thing in the planetarium and a distinct
thing to shoot at.

**It obeys the same orbital mechanics as everything else**, which means it is *overhead sometimes*.
Pass prediction already exists and already tells a player when. A weapon you can only use during a
57-second window that arrives on a schedule anyone can compute is far more interesting than one on
a cooldown.

### 2. Targeting, and its limits

Aiming reuses `RecoveryCapsule.deorbitTo` unchanged. What should be *added* is the honest error:
a real deorbit has dispersion, and cosmos already has `Profile.cep` sitting unused. Accuracy should
improve with:

- a **comms satellite** with the target in its cone (you can see what you are hitting), and
- a **recon satellite** that has imaged it recently.

That makes the constellation the weapon, not the rod — and it means warfront's radar, which can
already detect things, has something to detect.

### 3. Warning, and why it matters

An impactor spends real time in flight. During reentry it is **hot, fast, and visible**, and
kinetics already tracks the heating rate that makes it so. Anyone under it should get to see it
coming. A weapon nobody can react to is not a weapon, it is a cutscene.

The natural counter is a warfront concern, not a cosmos one: point defence already exists in
kinetics' guidance battery (`point_defence_intercept` is a committed golden trajectory).
**Intercepting an inbound rod with a warfront interceptor is a capability that already works** —
it has simply never been pointed upward.

### 4. Damage

Cosmos should not decide it. The impact event carries position, velocity and mass; **warfront owns
what happens to a base**, and it already models bases. The right shape is an event cosmos publishes
and warfront may consume — a declared contract, exactly as cosmos declares propellant tags for
crude_empire to fill, with neither mod naming the other.

## What must not happen

- **No damage inside kinetics.** I10 is structural. If this design ever needs it, the design is
  wrong.
- **No instant orbital strike.** Everything flies. If it cannot be intercepted, it should not ship.
- **No targeting players who are offline**, and no mechanism that makes a base indefensible while
  its owner sleeps. This is the one item on this page that is a *server-social* decision rather
  than a physics one, and it is yours.
- **No editing warfront.** If this needs something from warfront, it needs a contract warfront's
  own session chooses to honour.

## Open questions — deferred by the hold

Question 1 has been answered: **not yet.** The rest stay open for whenever the hold is lifted.

1. ~~**Do you want this at all?**~~ **Ruled: HOLD.** The aerospace program is complete without it,
   and this will be revisited after the server has lived a while.
2. If yes: should orbital weapons be **PvE only** (structures, hostile factions) or usable between
   players?
3. Should an impactor require **orbital assembly** — many launches to build one large rod — or
   should the heavy lifter's 400 kg be the practical ceiling?
4. Should warfront's interceptors be given an explicit **anti-orbital** role, or should the
   existing point-defence profile simply be allowed to engage what it can already reach?
