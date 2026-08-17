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

### The lunar economy

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
sampled or adapted. See `../ASSETS-ORIGIN.md`.
