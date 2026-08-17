# ECONOMY.md — the lunar economy

**Status: SHIPPED.** Proposals 1 and 2 are built. Proposal 3 (helium-3) is not, and I still
recommend against it — see the end.

Built on **warfront's economic template**, which is the empire's existing answer to "how do you
simulate an economy without inventing wealth". Warfront was read as a reference and not modified.

## What was carried across from warfront

Everything structural:

| Warfront | Cosmos |
|---|---|
| pure `EconomyModel`, no Minecraft dependency | pure `LunarEconomy`, same |
| money conserved exactly, `assertConservation()` every tick | **mass** conserved, asserted every tick |
| finite nodes; some ore veins never regenerate | finite ice deposits; **none** regenerate |
| seeded SplitMix64, byte-identical replay | same generator, same guarantee |
| prices move on supply/demand imbalance, clamped | allocation moves on air scarcity, clamped |
| shocks destroy stock, recovery contracts *move* money | shocks destroy stock, never create it |
| `Distribution` metrics record | `Report` viability record |
| Base64 snapshot behind a magic number, in `SavedData` | same, `CLE2` |
| headless test suite | `EconomyVerification`, 36 checks |

## The two things that are different, and why

**The conserved quantity is mass, not money.** Warfront conserves coins among citizens. The Moon
has no citizens and no currency, and inventing one would be cargo-culting the template rather than
applying it. What a lunar base actually conserves is kilograms — which is also what kinetics
already polices in its own mass ledger, so the two agree by construction.

**Nothing regenerates.** Warfront's farms and forests grow back. On the Moon there is no water
cycle to bring anything back: polar ice is billions of years of cometary delivery sitting in
permanent shadow, and a mined-out crater stays mined out. That single fact is what makes a base
somewhere you site carefully rather than a tap you turn on.

## The chemistry is the design

Water is **11.19% hydrogen and 88.81% oxygen** by mass. Hydrolox engines burn fuel-rich at about
**6:1**, not the stoichiometric 8:1. So electrolysing a kilogram of lunar water gives 0.1119 kg of
hydrogen, which can only burn 0.6714 kg of oxygen — leaving **0.2167 kg spare, 24.4% of the
oxygen produced**.

That surplus is not a concession. It is why every serious lunar architecture proposes ISRU for
propellant and life support together: **you cannot make the fuel without also making more air than
you need**. A player who builds an electrolyser to get home discovers they have also solved
breathing, and they discover it from arithmetic rather than from generosity.

## What shipped

**Ice Electrolyser** — melts polar ice, splits the water, mixes propellant at 6:1. **Only runs in
a `lunar_polar` biome**, because that is where the ice is; put one on a mare and it stands dark and
says so. Exposes an extract-only fluid tap, so a launch pad or a crude_empire pipe can draw
propellant straight out of it.

**Regolith Kiln** — bakes the ground for oxygen and building material. Works **anywhere** on the
Moon and works badly: 3% oxygen recovery against ice's near-total yield. It is the mundane option
and the one that never runs out.

**Sintered Regolith** — what a base is built out of: the ground it stands on, baked until it holds
together.

**`cosmos:hydrolox`** — and this is the payoff. **It lights the cryogenic rung**, the grade that has
carried hydrolox Isp figures and no fluid since Phase A. The best propellant in the game is the one
you cannot buy, cannot refine on Earth, and have to go and make. It has no world form: liquid
oxygen boils at 90 K and liquid hydrogen at 20 K, so a puddle on an airless surface in sunlight is
not a puddle. It exists only in tanks.

## The numbers

Six deposits of roughly 34 tonnes — about **200 tonnes of ice**, which yields close to **one full
lunar vehicle's propellant load**. A base's entire endowment is one more Moon rocket, and then the
ice is gone.

A four-electrolyser polar base, measured in-world: **132 tonnes of hydrolox, 92 days of air,
self-sufficient**, ledger balanced to 1.4e-7 kg.

## Four bugs the ledger caught

It earned its keep before it ever shipped:

1. **Boil-off was a per-tick figure that should have been per-day** — 240%/day instead of 0.1%.
   A base lost 70% of everything it ever mined, and the books balanced perfectly while it happened.
2. **Shocks were hostile, not hard.** One every 400 economic ticks costing 15% left a base with
   3.2 tonnes of a possible 188 — a 98% tax. Now one every three Minecraft days at 6%, which costs
   about a sixth of production.
3. **The audit tolerance was absolute.** A fixed 1e-6 kg looked rigorous and threw on a microgram
   in eight hundred tonnes; it is now relative to the mass that has moved.
4. **A shock destroyed a whole crater** and booked the loss to both `mined` and `lost` to keep the
   sums agreeing — which is exactly the trick that stops a ledger being evidence. Buried ice was
   never in the base's possession, so it now has its own counter, outside the audit.

## Helium-3 — still proposed, still not built

Real, genuinely lunar-exclusive, and with **no consumer**. Shipping it would mean a currency with
nothing to buy. It belongs to whatever phase brings a fusion reactor, not to this one. Listed
because leaving it out silently would be the wrong kind of tidy.
