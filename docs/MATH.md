# How Randar 1.8.9 works

This document records the exact 1.8.9 behaviour the solver depends on. All
constants were checked against the 1.8.9 classes, not against newer versions.

## 1. Java's LCG

`java.util.Random` is a 48-bit linear congruential generator.

```
state(n+1) = (state(n) * 0x5DEECE66D + 0xB) mod 2^48
next(bits) = state(n+1) >>> (48 - bits)
```

`setSeed(seed)` starts at `(seed ^ 0x5DEECE66D) & MASK`. Minecraft passes every
structure seed through `setSeed`, so only the low 48 bits of a world seed can
ever affect structure placement.

Useful constants:

| Name | Value |
|---|---|
| multiplier | `0x5DEECE66D` |
| addend | `0xB` |
| mask | `2^48 - 1` |
| inverse multiplier mod 2^48 | `246154705703781` |
| x coefficient in `World.setRandomSeed` | `341873128712` |
| z coefficient in `World.setRandomSeed` | `132897987541` |
| inverse of z coefficient mod 2^48 | `211541297333629` |

## 2. What a drop leaks

A broken block calls `Block.dropBlockAsItemWithChance`, which rolls the item
count first, then for each item performs four `nextFloat()` calls:

```
1. chance roll        nextFloat()             unconstrained
2. x position         nextFloat()
3. y position         nextFloat()
4. z position         nextFloat()
```

The item is positioned at `block + 0.25 + 0.5 * nextFloat()` per axis. 1.8.9
sends entity positions as fixed-point numbers with five fractional bits
(`floor(pos * 32)`). Subtracting `block * 32 + 8` leaves

```
nibble = floor(nextFloat() * 16)
```

so every item leaks four bits per axis, twelve bits per item. Four items give
48 bits, enough to pin the state in principle. The solver constrains the first
six items and replays all of them as a check.

Quantization only works if the client sees the original entity position. A
plugin that merges drops into a stack destroys both the count and the position
semantics, so stacked entities are rejected.

## 3. Quantity draws

The number of items is itself RNG output, and the draw order differs by block
class. `DropCracker.itemsFor` is the single source of truth.

**`BlockOre`** (diamond, coal, emerald, lapis), with `fortune`:

```
if fortune > 0:
    multiplier = max(0, nextInt(fortune + 2) - 1) + 1
else:
    multiplier = 1
base = baseCount + (nextInt(baseBound) if baseBound > 0 else 0)
count = base * multiplier
```

The Fortune multiplier is drawn **before** the base count. Reversing that order
still passes diamond, coal and emerald tests because their base is fixed at 1,
but it breaks lapis. There is a regression test for exactly this.

**`BlockRedstoneOre`, `BlockMelon`, `BlockGlowstone`, `BlockSeaLantern`:**

```
count = min(clamp, baseCount + nextInt(baseBound) + nextInt(fortune + 1))
```

`nextInt(1)` still advances the generator, so this extra draw happens at
Fortune 0 too. Forgetting it shifts every position call by one and the solve
fails.

**`BlockClay`** returns a fixed four and consumes no RNG for the quantity. That
makes clay the only source that works identically at every Fortune level.

The exact bounds in `OreType`:

| Ore | base | bound | model | clamp |
|---|---|---|---|---|
| diamond, coal, emerald | 1 | 0 | multiplied | none |
| lapis | 4 | 5 | multiplied | none |
| redstone | 4 | 2 | additive | none |
| clay | 4 | 0 | fixed | 4 |
| melon | 3 | 5 | additive | 9 |
| glowstone | 2 | 3 | additive | 4 |
| sea lantern | 2 | 2 | additive | 5 |

Nether quartz is deliberately absent. It only exists in the Nether, and the
Nether does not run the overworld structure checks that reset the RNG this mod
reverses.

## 4. Solving a drop

LattiCG (`JavaRandomReverser`) builds a system of inequalities over the 48-bit
state:

```
for each constrained item:
    consume one unconstrained nextFloat()   # chance
    for each axis:
        add constraint L <= nextFloat() < U  # nibble interval
```

It returns candidate internal states. Each candidate is replayed through the
full drop sequence, including the quantity model and every later item, and
discarded if the item count or any nibble differs. This replay is what removes
lattice solutions that satisfy the first six items but not the rest.

`DropCracker.MIN_ITEMS` is 4. Fewer items cannot constrain 48 bits.

## 5. Walking back to a structure reset

In 1.8.9, `ChunkProviderGenerate.provideChunk` runs structure generators in this
order:

```
mineshaft -> village -> stronghold -> temple -> ocean monument
```

Only village, temple and ocean monument call `World.setRandomSeed`. Mineshafts
and strongholds use their own map-generator random. The monument check therefore
leaves `World.rand` in the state the drop solver sees, unless more world draws
happen afterwards.

The reset argument is:

```
arg = worldSeed + salt + regionX * 341873128712 + regionZ * 132897987541
state = (arg ^ 0x5DEECE66D) & MASK
```

with salts:

| Structure | Salt | Spacing | Placement roll |
|---|---|---|---|
| village | `10387312` | 32 chunks | `nextInt(24)` per axis |
| temple | `14357617` | 32 chunks | `nextInt(24)` per axis |
| ocean monument | `10387313` | 32 chunks | `(nextInt(27) + nextInt(27)) / 2` per axis |

A region is 32 chunks, or 512 blocks across. For a state at lag `L`:

```
s = previous^L(dropState)
arg = (s ^ 0x5DEECE66D) & MASK
seed = arg - salt - regionX * 341873128712 - regionZ * 132897987541  (mod 2^48)
```

Given `seed`, `type` and `regionX`, there is exactly one `regionZ`, because the
z coefficient is odd and therefore invertible modulo 2^48:

```
regionZ = (seed - salt - regionX * 341873128712) * 211541297333629  (mod 2^48)
```

Keep it only if it lies inside the world border region bound, currently
`[-58594, 58594]`.

### The fast region scan

`341873128712` is divisible by 8, so the low three bits of `regionZ` are fixed
by the state. Stepping `regionZ` by 8 makes the matching `regionX` residue
advance by a constant addition, so the scan does not perform a multiply per
region. A full 20,000-lag, three-type scan takes about 0.6 seconds instead of
about 5.6 seconds.

### Why the mod can report decoys

The global search checks roughly `117,189^2` regions per structure type. Three
types give about `1.5 * 10^-4` false hits per lag, or roughly three decoys over
a 20,000-lag window. The true reset is usually found at a small lag because
chunk generation happens shortly before the break, but a decoy is possible.
`StructureLocator.reverseFirstInWindow` avoids this by only accepting resets in
a precomputed window around the player. The mod uses the global variant on
purpose, since the user wants the exploit rather than a local-only guarantee.

## 6. Recovering an unknown seed

One drop gives the state at the break, not the number of draws since the last
structure reset. Walking back `maxLag` states and trying every type and every
region in a small window produces a set of possible seeds. One set is useless on
its own; every state implies some seed.

If a chunk generation event happens between two breaks, the two drops lie on
different orbit segments. The true world seed is in both candidate sets. The
solver sorts and de-duplicates each set and intersects them in linear time.

If there was no reset between the breaks, both sets describe the same orbit and
share far more than one seed. `RandarEngine` rejects intersections larger than
16 candidates and asks for another sample instead of reporting a range as a
"recovered" seed.

Only the low 48 bits are recovered, because `setSeed` discards the top 16 bits.
Every structure, slime and fortress formula in 1.8.9 also works modulo `2^48`
after `setSeed`, so the recovered value is sufficient for all the seed tools.
It is not sufficient to reproduce terrain generation, which hashes the full
64-bit seed.

## 7. Seed tools

With a known seed these are pure functions.

**Slime chunks** (`ChunkProviderGenerate.isSlimeChunk`):

```
mixed = (seed + x*x*4987142 + x*5947611 + z*z*4392871 + z*389711) ^ 987234911
slime = new Random(mixed).nextInt(10) == 0
```

**Nether fortress** (`MapGenNetherBridge`): one candidate per 16 x 16 chunk
region.

```
regionX = chunkX >> 4
regionZ = chunkZ >> 4
rand.setSeed((regionX ^ (regionZ << 4)) ^ seed)
rand.nextInt()                       # MapGenStructure discard
if rand.nextInt(3) != 0: no fortress
placeX = (regionX << 4) + 4 + rand.nextInt(8)
placeZ = (regionZ << 4) + 4 + rand.nextInt(8)
```

The x and z placement checks short-circuit in the same order as vanilla, so the
second `nextInt(8)` is not consumed when x already failed.

**Mineshaft** (`MapGenBase` + `MapGenMineshaft`):

```
j = new Random(seed).nextLong()
k = new Random(seed).nextLong()      # same generator, second draw
chunkSeed = chunkX * j ^ chunkZ * k ^ seed
rand.setSeed(chunkSeed)
rand.nextInt()                       # MapGenStructure discard
if rand.nextDouble() >= 0.004: no mineshaft
if rand.nextInt(80) < max(|chunkX|, |chunkZ|): mineshaft
```

The distance bias is why mineshafts are rare near the origin and common far
away. A chunk at `(0, 0)` can never pass because `nextInt(80)` is non-negative.

**Strongholds are not included.** Their placement uses a separate
`new Random(worldSeed)` and repeated biome checks, so a seed-only answer would
be approximate. The mod does not present approximate locations as exact.

## 8. References

- Java SE `java.util.Random` specification.
- Minecraft 1.8.9 classes: `World.setRandomSeed`,
  `ChunkProviderGenerate.provideChunk`, `MapGenStructure`,
  `MapGenVillage`, `MapGenScatteredFeature`, `StructureOceanMonument`,
  `MapGenNetherBridge`, `MapGenMineshaft`, `MapGenBase`,
  `Block.dropBlockAsItemWithChance`, `BlockOre`, `BlockRedstoneOre`,
  `BlockClay`, `BlockMelon`, `BlockGlowstone`, `BlockSeaLantern`.
- LattiCG: <https://github.com/mjtb49/LattiCG>
- Randar explanation: <https://github.com/spawnmason/randar-explanation>
