# PLANETS.md — beyond the Moon

> ## ✅ RULED at the v0.1.0-D gate
>
> **In-system bodies under the current single-body frame: APPROVED, building now.**
>
> **The heliocentric patched-conic frame: APPROVED FOR SCOPING as its own kinetics epic — design
> doc first, in `lilkuzco_kinetics/HELIOCENTRIC.md`.**
>
> And the constraint that governs the first half: **do not ship a Mars where distance costs
> nothing.** The flat Δv ladder measured below is the reason the real frame has to come first, so
> no destination that depends on it may be built under the current one.

**Phase C. Design only, except where ruled above.**

## The finding that governs everything else

I measured what the current model says about travelling further, using kinetics' own Hohmann
solution and the same 18.6015× length scale the planet and the Moon already use:

| Destination | Distance | In planetary radii | Injection Δv | Coast |
|---|---|---|---|---|
| Moon *(shipped)* | 20,665 km | 60.3 R | **732 m/s** | 27.7 h |
| Near asteroid | 107,518 km | 314 R | **750 m/s** | 322 h |
| Mars analogue | 4,211,497 km | 12,296 R | **754 m/s** | 78,630 h |
| Venus analogue | 2,225,632 km | 6,498 R | **753 m/s** | 30,211 h |

Read the Δv column. **Every destination costs the same.** Mars is 204× further than the Moon and
costs 3% more to depart for. That is not a bug in the arithmetic — it is exactly right for a
single gravitating body, because once you are near escape velocity the marginal cost of going
further collapses toward zero.

And read the coast column: nine years to Mars.

**The one-body frame cannot express interplanetary travel.** It expressed the Moon beautifully,
because the Moon genuinely is a satellite of the primary. Planets are not. Extending the same maths
outward gives a game where the rocket ladder stops meaning anything past the Moon and the journey
stops being rideable at any compression a player would tolerate.

This is the decision Phase C exists to put in front of you, and I do not think it should be made
by defaulting.

## Three ways forward

### Option A — a real heliocentric frame in kinetics

Add a Sun. Planets orbit it; the primary and its Moon become one system inside it. Transfers become
**patched conics**: escape the primary's sphere of influence, coast on a heliocentric ellipse,
capture at the destination.

**What kinetics would need:** a `System` of gravitating bodies with sphere-of-influence radii; a
heliocentric `OrbitalMechanics`; a transfer planner that answers "when is the next window"; and
capture/aerocapture at arrival. The existing `OrbitalMechanics`, `PoweredDescent` and phase machine
all survive unchanged — they are two-body maths and would simply be applied per patch.

**What it buys:** launch windows. Real ones. A Mars analogue would have a synodic period, so the
Moon is available whenever you want it and Mars is available *sometimes* — which is the single
biggest thing that makes real interplanetary flight feel like what it is. It also restores the Δv
ladder, because a heliocentric transfer to Mars genuinely costs far more than one to the Moon.

**Cost:** this is the largest single change the campaign has contemplated. It is a new kinetics
minor version with its own invariants and its own golden trajectories.

### Option B — more bodies in the primary's system

Keep one gravitating body. Add destinations at lunar-ish distances: a captured asteroid, a second
moon, an artificial station. Everything already works — dimension registration, transfer time,
powered descent, the economy template.

**What it buys:** three or four more places to go, cheaply and correctly, with the physics already
shipped and proven.

**What it costs:** they are all *moons*. There is no Mars, no Venus, no launch window, and the Δv
ladder stays flat past the first one. It is honest and it is small.

### Option C — say the Moon is the edge

Stop here for destinations, and spend Phase C on depth instead of distance: lunar bases, surface
vehicles, orbital infrastructure, the economy extended.

**My recommendation was B now and A when you want interplanetary properly, and that is what was
ruled.** B is a few days and
uses physics already under test. A is a genuine kinetics epic and deserves to be scoped as one
rather than smuggled in as "more planets". C is the fallback if neither appeals — but the Moon
being reachable and finite already makes a good campaign, and I would not treat that as failure.

## If Option A: what a destination needs

Every body in the current model is defined by exactly four things, and this has held for the Moon:

1. **A gravity scalar** — audited in `physics-constants.json` against the real measured value.
2. **An atmosphere, or none** — one boolean that decides drag, lift, parachutes, reentry heating
   and whether you can breathe. Nothing downstream knows which body it is.
3. **A dimension** — datapack worldgen, one biome source, one surface rule.
4. **An economy** — the mass-conserving model, with the local feedstock swapped.

That is the whole contract, and it is why the Moon needed no lunar special cases. A Mars analogue
would be `gravity 0.3794 g`, `atmosphere present but thin`, and — importantly — **the first body
where aerocapture and parachutes work again**, which makes arrival a completely different problem
from the Moon's retro-burn. That contrast is worth more than the distance.

## What must not happen

- **No second energy system.** Whatever powers a planetary base, it is crude_empire's lane.
- **No physics in cosmos.** If a destination needs new mechanics, they belong in kinetics with
  invariants and golden trajectories, exactly as `LANDING` did.
- **No scaled numbers pretending to be derived.** `lunar_orbit_insertion_delta_v` and
  `lunar_descent_delta_v` are *scaled from reality, not derived*, because deriving them needs a
  lunar μ and radius — i.e. a second gravitating body. Option A is precisely the change that would
  let those two constants become derived, and the source notes already say which they are. **If
  Option A ships, those notes are the checklist.**
- **No destination without an economy.** A place you can reach and cannot use is a screenshot.

## Open questions

1. ~~**A, B, or C?**~~ **Ruled: B now, A approved for scoping.**
2. If A: do you want **launch windows** as a hard constraint (you wait for the window) or a soft
   one (you can go anytime, badly)? Hard is more real and more frustrating; it is your server.
3. Should other bodies have their own **finite** resources like lunar ice, or is scarcity the
   Moon's characteristic and elsewhere is abundant?
