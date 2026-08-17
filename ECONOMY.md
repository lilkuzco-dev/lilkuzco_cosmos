# ECONOMY.md — lunar resources, proposed

**Status: PROPOSAL. Nothing in this document is implemented.**

Phase B ships the Moon as a place: a dimension, vacuum physics, life support, a journey with a
fuel bill, and a landing that can fail. It ships **no lunar economy at all** — no ore, no
processing, no recipe that consumes anything lunar, no reason beyond the trip itself.

That is deliberate, and it is what this document is for. The campaign brief requires lunar
resources to be proposed and approved before they are built, so they are proposed here and the
code does not anticipate the answer.

## What already exists, and why it is not an economy

Three lunar blocks generate today. All three are terrain.

| Block | Where | What it does today |
|---|---|---|
| `cosmos:regolith` | most of the surface | drops itself; no recipe consumes it |
| `cosmos:mare_basalt` | the dark seas, and everything below the regolith | drops itself; no recipe consumes it |
| `cosmos:lunar_ice` | shallow discs in polar crater floors | drops itself; no recipe consumes it |

`lunar_ice` is the one worth calling out explicitly. It is placed by a worldgen feature because
the polar craters would be visibly wrong without it — permanently shadowed floors holding volatiles
is the single most-reported fact about the lunar poles. But it has **no use, no processing path and
no recipe**. It is scenery that happens to be true. If the proposal below is rejected, it stays
scenery, and nothing has to be unwound.

## The design constraint I would ask you to hold me to

A lunar resource has to justify a 3,627 m/s round of delta-v, 1,576 buckets of propellant and a
one-way trip. There are only two honest ways to do that, and one of them is a trap:

- **Good: it enables something that cannot be done on the ground.** The Moon becomes a place you
  build *from*.
- **Trap: it is the same thing as an overworld resource, but better.** That makes the Moon a more
  annoying quarry. Every player who does the trip once will conclude, correctly, that automating
  the overworld version was the better play.

So none of the proposals below is an ore that smelts into a familiar ingot.

## Proposal

### 1. Lunar ice → water → propellant (the one that matters)

**What:** an electrolyser that turns `lunar_ice` into propellant, on the Moon.

**Why it is the right first resource:** it is the answer to "how do I get home", and it makes that
answer a *build* rather than a gift. Phase B currently strands you — a stated scope limit — and the
honest fix is not a return ticket in the lander's recipe, it is in-situ propellant production, which
is what every real lunar architecture has proposed for sixty years for exactly the same reason:
hauling return propellant up from the surface costs more than the payload is worth.

**What it would touch:** one new block, one recipe, and a fluid tagged into
`#cosmos:propellant/cryogenic` — the grade that already exists, is already empty, and already
carries hydrolox figures waiting for a fluid. **No new physics and no new grade.** The rung is
already built.

**Open question for you:** should ice be finite per crater? Finite makes a base a location decision;
infinite makes it a build-once utility. I lean finite, but this is a taste call about your server,
not a physics one.

### 2. Regolith → oxygen, and regolith → construction

**What:** baking regolith yields breathable oxygen (refilling suits without a station's power) and
a sintered building block.

**Why:** it is the mundane one, and mundane is the point. It gives the surface itself a use, so a
base is built out of the ground it stands on instead of out of iron flown up from home. It also
takes the pressure off `lunar_ice` being the only reason to land anywhere in particular.

**What it would touch:** one furnace-class recipe, one new block. No physics.

### 3. Helium-3 — proposed, and I recommend against it

**What:** the famous one. Solar-wind-implanted He-3 in mature regolith, as a fusion fuel.

**Why I recommend against it:** it is real, it is genuinely lunar-exclusive, and it has no
consumer. It would be a resource that exists to be a resource, whose only purpose is a fusion
system nothing in this empire has. Adding it now means shipping a currency with nothing to buy.

It is listed because leaving it out silently would be the wrong kind of tidy — if you want it,
it belongs to whatever phase brings the reactor, not to this one.

## What I would not propose

- **Any overworld ore on the Moon.** Iron on the Moon is a worse iron mine.
- **A lunar power source.** That is crude_empire's lane, and a second energy system is fenced.
- **Anything that makes lunar gravity a mining bonus.** Physics is kinetics' to decide, and 0.165 g
  already changes how the place feels without being turned into a yield multiplier.

## What happens on each answer

| Your call | What I do |
|---|---|
| Approve 1 | Build ISRU propellant; the Moon stops being one-way. Cryogenic grade lights up. |
| Approve 1 + 2 | The above, plus regolith oxygen and sintered building blocks. |
| Approve 3 as well | He-3 ships as an inert resource with a stated note that its consumer does not exist yet. |
| Reject all | `lunar_ice`, `regolith` and `mare_basalt` stay exactly as they are — terrain that drops itself. Nothing is unwound, because nothing was built ahead of the answer. |

Phase B is complete and shippable under any of these, including the last one.
