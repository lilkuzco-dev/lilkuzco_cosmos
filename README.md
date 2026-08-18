# lilkuzco_cosmos

Rockets, orbit, satellites and recovery for Fabric / Minecraft 26.2.

**Cosmos contains no physics.** Every metre of motion is integrated by
[lilkuzco_kinetics](https://github.com/lilkuzco-dev/lilkuzco_kinetics). Cosmos decides what a
rocket is made of, what it costs and what a player can do with it; kinetics decides whether it
flies. There is no gravity constant, no drag term and no velocity update anywhere in this
repository, and that is checkable — grep for one.

---

## The one idea

**A better fuel does not unlock orbit. It raises specific impulse, and Tsiolkovsky does the rest.**

The same 19.6-tonne rocket, the same tanks, the same engines:

| Propellant | Isp (vac) | Δv | Budget | Outcome |
|---|---:|---:|---:|---|
| Crude oil | 232 s | 1790 m/s | 2230 m/s | lifts off, **falls back** |
| Refined kerosene | 311 s | 2400 m/s | 2230 m/s | **orbit**, 170 m/s margin |
| Cryogenic | 450 s | 3473 m/s | 2230 m/s | orbit, comfortably |

Nothing gates the tier on the fuel. There is no flag. The logarithm does it, and the launch pad
shows you the number before you light it.

---

## Building it

A **launch pad** controller with a solid apron of **pad frames** around it and clear airspace
above — 7×7 and 12 blocks for the orbital tier. **Propellant tanks** beside the apron raise how
much fuel the pad holds. Drop an **airframe** and a **payload** into the controller, fill the
tanks, and the readout tells you your thrust-to-weight and your Δv against the orbital budget,
both computed by kinetics' own launch assessment.

Then it counts down from ten and you find out whether you were right.

**The pad will not stop you launching a rocket that cannot make it.** It refuses an incomplete
structure, an empty tank and a missing airframe, because those are assembly errors. An
under-fuelled vehicle it lights happily, having told you exactly how many m/s you are short.

---

## What goes up

| | |
|---|---|
| **Recon satellite** | Images the ground under its track. Reports terrain, and counts *worked ground* — surface blocks that only exist because somebody built them. That count is the intelligence. |
| **Comms relay** | Extends ground-sensor range while overhead, published through `CommsCoverage` for other empire mods to read. |

Orbits live in kinetics' registry, propagated **from epoch**. A satellite advances at the same
rate whether its chunk is loaded, whether the server is lagging, and whether anyone has looked at
it in a week — including while the server was switched off.

Ground tracks are computed in the **rotating** frame, so passes over a fixed point are a real
schedule rather than a guess. At the reference altitude the orbital period equals one Minecraft
day and the track repeats daily; higher up it drifts 134° per revolution and a base is overflown
only every eighth orbit. **Choosing your orbit is choosing your coverage.**

Commanded deorbit brings a payload home. The burn is *aimed*: cosmos flies the entry once,
headlessly, to measure the downrange distance, then places the real entry that far short of your
position. The capsule flies exactly the trajectory kinetics says it flies and lands where you are.

---

## Fuel, and a missing dependency

The campaign brief says to read crude_empire's fuel API. **There isn't one.** crude_empire
v0.1.x is worldgen only — crude oil, reservoirs and seeps — and its own fluid tag is commented
"for future refinery plumbing". Diesel does not exist yet.

So cosmos does not reach into crude_empire. It declares a **propellant ladder** as fluid tags and
specific impulses, and ships datapack tags mapping today's crude oil into the crudest grade. The
kerosene and cryogenic rungs exist, are empty, and are wired to real Isp figures. When
crude_empire's refinery lands, one line in `data/cosmos/tags/fluid/propellant/kerosene.json` makes
every launch pad in the world accept it — no code change in either mod, and crude_empire is never
touched.

The same is true of `warfront:threats`: warfront v0.2.1 has no radar contract to consume. So
`CommsCoverage` is a public static query that warfront can call when it grows one, and cosmos
imports nothing from warfront.

---

## The Moon (Phase B)

`cosmos:moon` is a dimension registered with kinetics as **vacuum at 0.16519 g**, and that single
registration is what makes it the Moon. Everything a player notices follows from those two facts
rather than from lunar special-casing:

- **Parachutes are inert, not disabled.** Kinetics computes `q = 0` where density is zero, so a
  canopy produces exactly zero force. Nothing checks which dimension it is in.
- **Landing is a retro-burn** — kinetics' RD6 powered descent, spending the lander's own
  propellant. A lander that cannot afford its descent arrives at a few hundred metres per second.
- **There is nothing to breathe,** which life support reads from the same atmosphere rather than
  from a dimension whitelist.

### Getting there

| Leg | Cost | Who pays |
|---|---|---|
| ground → orbit | 2,230 m/s | the launch vehicle |
| orbit → trans-lunar injection | 740 m/s | the launch vehicle |
| arrival → lunar orbit | 214 m/s | the lander |
| lunar orbit → surface | 444 m/s | the lander |
| **total** | **3,627 m/s** | **1.63× reaching orbit** |

The **lunar vehicle** is 179 tonnes on the pad against the heavy lifter's 42.5 — four times the
rocket for 33% more delta-v, which is Tsiolkovsky's logarithm doing exactly what it does. It
clears the trans-lunar budget on refined kerosene and **falls 708 m/s short on crude**. Nothing
gates the Moon on the refinery; the arithmetic does.

1,576 buckets of propellant, which is 49 tanks around an 11×11 pad. That is a pipeline, not a
right-click — the launch pad is a Fabric fluid storage precisely so crude_empire's oil pipes can
fill it, and no code in either mod names the other.

### The journey

The transit is **ridden, not loaded**. Kinetics puts the Hohmann transfer at 99,747 s — 27.7 hours
of simulated time, the real 3.0 days scaled by the same factor as everything else. Cosmos plays it
back at 415× so it takes four minutes, and **the compression is the only fiction**: the trajectory,
the budget, the arrival speed and the burn are all real, and the telemetry on the action bar is
read off kinetics' own solution.

Stand on the apron with a lander in the payload slot when the count reaches zero and you are crew.
Walk off it and you are not.

### Haze — the outer moon

The second destination, at **120 planetary radii** — inside the 145 R sphere-of-influence limit, so
it stays a real satellite when the heliocentric frame eventually lands.

**It has an atmosphere, and that is the entire reason it exists.** Everything the Moon taught is
inverted:

| | the Moon | Haze |
|---|---|---|
| air | none | thick, cold, unbreathable |
| gravity | 0.165 g | 0.138 g |
| arriving | retro-burn, 444 m/s of propellant | **parachutes, for free** |
| a canopy at 200 m/s | **0.0 Pa** | **9,871 Pa** |
| coast | 240 s ridden (4.0 min) | 400 s ridden (6.7 min) |
| feedstock | water ice → oxygen **and** fuel | ammonia → fuel and buffer gas, **no oxygen** |

Neither arrival is a special case. They fall out of one boolean — whether kinetics was told the
dimension has an atmosphere — because every force in the model reads the density it is given.

**Departure Δv is almost identical: 2,970 m/s to the Moon, 2,973 to Haze.** That flat ladder is
exactly what `PLANETS.md` measured and why interplanetary distance has to wait for a real
heliocentric frame. What distance buys here is *time* and a different way to arrive, not a bigger
rocket.

**The clock runs faster the further you go**, as the square root of the distance. A single
compression rate is honest right up until it is unplayable: at a flat 415× Haze was **eleven
minutes** of sitting in a capsule with nothing to do, which is long enough that a player alt-tabs —
and a ride you alt-tab out of has become the loading screen it was built to avoid. Square-root
scaling keeps the property that matters, that further is *felt* as longer, while bounding the tail.
The Moon stays at four minutes; Haze is six and a half rather than eleven.

**The payload is the itinerary.** A lunar lander has engines and no heat shield; an entry capsule
has a heat shield and no engines. Neither can do the other's job, so which one sits in the pad
decides where the flight goes.

Ammonia is **17.76% hydrogen by mass** against water's 11.19% — a richer fuel source, and it yields
**no oxygen at all**. A Haze base can make propellant it cannot burn and gas it cannot breathe.
The Moon gives you both halves and runs out; Haze gives you one half and does not.

---

## The lunar economy

Built on **warfront's economic template**, read as a reference and not modified. Everything
structural carries across — a pure model with no Minecraft dependency, a seeded SplitMix64 with
byte-identical replay, finite nodes, shocks that destroy but never create, a Base64 snapshot behind
a magic number, and a conservation audit that throws every tick. Two things differ: the conserved
quantity is **mass** rather than money, because a base has no citizens to hold currency; and
**nothing regenerates**, because there is no water cycle to bring it back.

The chemistry is the design. Water is 11.19% hydrogen by mass and hydrolox burns fuel-rich at 6:1,
so **making propellant necessarily makes 24% more oxygen than the engine can use**. You cannot
solve getting home without also solving breathing.

`cosmos:hydrolox` lights the **cryogenic rung** — the grade that has carried Isp figures and no
fluid since Phase A. The best propellant in the game is the one you have to go and make.

See `ECONOMY.md`.

---

## One law this codebase is built around

**Anything that must progress while nobody is nearby runs on the server tick, never on an entity
or block-entity tick.**

Cosmos learned this three times, each time as a different-looking bug with the same root: launch
insertion resolved from the rocket's tick and silently produced no satellite; capsule recovery
resolved from the capsule's tick and silently dropped no payload; the lunar ISRU roster was built
from block-entity ticks, so a base stopped producing the moment its chunks unloaded. Unloaded
chunks do not tick, and a simulation attached to them does not fail loudly — it does nothing while
everything downstream reports success.

`LaunchTracker`, `RecoveryTracker`, `LunarTransit` and `LunarEconomyManager` all subscribe to
`END_SERVER_TICK`. `RocketEntity`, `CapsuleEntity` and `TransitEntity` are **views**: if one never
ticks, the only thing lost is visuals. Registration happens on `onPlace` and
`affectNeighborsAfterRemoval`, never in `setRemoved`, which also fires on chunk unload.

It is empire law — `mod-installer/CLAUDE.md` rule 7.

---

## The client render battery

```sh
./gradlew runGametest      # boots a real client, screenshots every visual, ~2 min
```

**Headless verification cannot see whether anything is drawn, and two shipped releases proved it.**
Phase A registered no entity renderer at all, so launching a rocket dereferenced a null on the
render thread and hard-crashed the client. The fix was a renderer that drew nothing — correct as a
crash fix, and it left the rocket invisible in flight for another whole release. Every server-side
check passed on both: the physics were right, the satellite deployed, the logs were clean.

So `CosmosRenderTest` boots a client, puts each thing cosmos draws in front of a camera, and
screenshots it into `build/run-gametest/screenshots/`. The screenshots are the evidence and they
are meant to be looked at.

It found the **PNG generator silently truncating both 64x64 entity sheets to their top-left 16x16
corner** — every block texture is 16x16, so a writer that hardcoded the size had been correct by
coincidence for months. The rocket had been wearing a sixteenth of its livery.

It also proves the camera shake fires, by measurement rather than assertion: eight frames a tick
apart from a stationary camera, and the horizon swings five pixels. At the amplitude originally
written it swung one, which is noise — the value shipped is the one the screenshots justified.

It has already earned its keep beyond the rocket: it caught that the **recovery capsule's view
never moved**. Landing resolution had been correctly moved to the server tick, but the entity was
still mirroring its body from its own tick — and a capsule spends nearly all of its 3,300-block
entry over unloaded chunks. The landing was right and nothing ever arrived at the landing site to
watch. Empire law rule 7 again, in the one place nobody thought to apply it: the part whose entire
job is to be looked at.

---

## Verifying it

```sh
tools/verify.sh                         # Phase A and Phase B chains, headless, ~2 s
./gradlew build                         # -> build/libs/lilkuzco-cosmos-0.1.0-B.jar
tools/devserver.sh log.txt "cosmos selftest" "cosmos moon"
tools/devserver.sh log.txt "forceload add 0 0" "cosmos moonland 300" "cosmos moonland 60"
tools/devserver.sh log.txt "cosmos padtest"     # build a pad, fuel it, launch it
tools/devserver.sh log.txt "cosmos isrutest" "cosmos economy 20000"
```

`tools/verify.sh` flies propellant → launch → insertion → passes → deorbit → reentry → recovery
against real kinetics with no Minecraft at all, and prints every number. That is possible because
the launch pipeline is Minecraft-free by construction: a propellant grade is two specific impulses
and a rocket tier is masses and thrusts.

`cosmos padtest` builds a pad, fills it **through the Fabric fluid API exactly as a crude_empire
pipe would**, and launches it. It exists because the fuelling path was rewritten wholesale in vB
and the version it replaced was `acceptFuel` — a method nothing in the game ever called, so no pad
could be fuelled at all. Proving the replacement works needs a real insertion into a real tank on a
real block entity. It also caught ignition destroying every drop of surplus propellant.

`cosmos moonland` drops a real lander into the real Moon dimension and logs the descent tick by
tick. **This is not the same claim as the headless battery** and it earns its keep: the battery
flies over `WorldProbe.flatGround`, and flat ground hid a bug where the retro-burn was solved
against sea level rather than the surface beneath it. Over generated lunar terrain — 78 blocks up
— a correctly fuelled lander was arriving at 15.7 m/s instead of 5.3. The burn now measures to the
ground, and a landing onto 200-block-high terrain is a battery check so the blind spot cannot come
back.

There are no screenshots in this repo and there will not be. Screen Recording permission is not
granted to the terminal in this environment, so `screencapture` returns desktop wallpaper — it
could never be evidence. The empire's standing answer is logs, and the strongest log is the
pipeline actually running.

---

## Licence

MIT. All art original and **generated procedurally** by `tools/gen-textures.py` — nothing traced,
sampled or adapted. See [`provenance/`](provenance/) — the campaign's
asset origins, verified quarry commits, and the quarantine list.
