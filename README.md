# Randar 1.8.9

A client-side Forge mod that reads the server's world RNG out of ordinary block
drops, then points at the 512 x 512 region where chunks were last generated.
On a multiplayer server where the seed is hidden, two breaks separated by fresh
chunk generation also recover the low 48 bits of the world seed.

Built for Minecraft 1.8.9 (Forge 11.15.1.2318, Java 8). It does not modify the
server and does not need server plugins.

## What it gives you

- **Structure region from a drop.** Four or more item entities from one broken
  block leak enough bits to recover `World.rand`. Walking that state backwards
  finds the last village, temple or ocean monument check, which identifies the
  structure region around the player.
- **Seed recovery on multiplayer.** Break an ore, move until new chunks load,
  break another. The two independent candidate sets intersect at exactly the
  world seed. No seed value needs to be typed in.
- **Seed tools.** Once a seed is known, list structure placement rolls, slime
  chunks, nether fortress chunks and mineshaft chunks around you.
- **Honest attribution.** A hit is a region where someone generated chunks.
  The hit keeps distance, direction, recent-movement and tab-list context so you
  can judge whether it was you or another player. RNG alone never yields a name.

## Requirements

| | |
|---|---|
| Minecraft | 1.8.9 only |
| Forge | 11.15.1.2318 or compatible 1.8.9 build |
| Java | 8 |
| Side | client |

## Install

1. Install Forge for 1.8.9.
2. Put `randar-189-1.0.0.jar` in `.minecraft/mods/`.
3. Launch the Forge profile. The mod is client-only and does nothing on a
   dedicated server.

The release jar bundles the core solver and LattiCG, so it is the only file you
need.

## Quick start

**Singleplayer:** the integrated server exposes the seed, so Randar picks it up
automatically. Break a block that drops four or more items and open the GUI
(default keybind: `R`) or run `/randar last`.

**Multiplayer, seed unknown:**

1. Break clay (always four clay balls, no Fortune needed), redstone, lapis, or
   an ore that happens to drop four or more items.
2. Travel until new chunks generate. A region boundary is ideal.
3. Break another qualifying block.
4. Randar intersects the two samples and reports the recovered seed. Confirm
   with `/randar status` and start using the seed tools.

If the first two samples do not intersect, travel further and break a third.
Two breaks with no chunk generation between them describe the same RNG orbit
and are deliberately rejected as ambiguous.

Recovery assumes the last structure reset came from a chunk near you. On a busy
server another player can reset the RNG between your chunk generation and your
break, and then a local sample contains no candidate for the true seed. Cross
into fresh terrain and break within a second or two, or recover on a quiet
server / when you are the main source of chunk generation. If you already know
the seed, `/randar seed <value>` skips all of this.

## Which drops work

The solver needs at least four item entities from one block. The first six are
used as constraints; later items are replayed as a check.

| Block | Items before Fortune | Enough at Fortune 0? |
|---|---|---|
| Clay | 4 (fixed, no draw) | always |
| Redstone ore | 4 to 5 | always |
| Lapis ore | 4 to 8 | always |
| Melon | 3 to 7 | 4 out of 5 breaks |
| Glowstone | 2 to 4 | only a 4-drop |
| Sea lantern | 2 to 3 | never (needs Fortune) |
| Diamond / coal / emerald ore | 1 | never (Fortune III can reach 4) |

Diamond, coal and emerald at Fortune III reach four items in roughly one break
in five. Clay is the reliable choice because its quantity consumes no RNG.

Silk Touch produces one item and cannot be solved. Items stacked by a plugin
are ignored because their count no longer describes one vanilla break. Drops
outside the overworld are ignored: the Nether and End do not run the overworld
structure checks this mod reverses.

## Commands

`/randar` and `/rdr` are equivalent.

| Command | Effect |
|---|---|
| `/randar seed <value>` | Set the world seed manually. Decimal or `0x` hex. |
| `/randar seed clear` | Forget the seed and return to drop recovery. |
| `/randar recover` | Show recovery progress, or `clear` to reset samples. |
| `/randar recoverlag <draws>` | Backward window per sample (default 40000). |
| `/randar lag <draws>` | Structure search limit (default 20000). |
| `/randar status` | Seed source, recovery state, solver state. |
| `/randar last` | Most recent hit with placement and attribution. |
| `/randar structures [radius]` | Nearest placement rolls, default 3 regions. |
| `/randar slime [radius]` | Slime chunks, default 8 chunks. |
| `/randar fortress [radius]` | Nether fortress chunks, default 32 chunks. |
| `/randar mineshaft [radius]` | Mineshaft chunks, default 16 chunks. |
| `/randar hud [on\|off]` | Toggle the HUD. |
| `/randar auto [on\|off]` | Toggle automatic capture and solving. |
| `/randar gui` | Open the settings and history screen. |
| `/randar clear` | Clear saved hits. |

`/randar structures` prints the RNG placement roll. Vanilla still checks the
biome before building, so a listed desert village candidate in an ocean is only
a candidate. Monuments use the averaged two-roll placement; villages and
temples use one roll per axis.

## Does a hit identify a specific player?

No. A hit identifies a **512 x 512 block region where chunks were generated**.
That is the information the RNG contains. The mod records three hints:

- whether the region is the one you are standing in;
- distance and compass direction from you to the region centre;
- whether you entered a new chunk in the last ten seconds;
- how many other players are in the tab list, with up to six names.

If the region is far away, you have not moved recently, and exactly one other
player is online, that player is a reasonable lead. It is still inference.
Another player, a portal, a farm, a chunk loader, or your own earlier path can
all produce the same hit. The HUD and `/randar last` word it that way on
purpose.

## Compatibility and limits

- Works against vanilla and unpatched Spigot/Paper 1.8.x. The client sees the
  same drop packets either way. Builds with Paper's separate world-generation
  `Random` patch do not reset the RNG that produces drops, so hits and seed
  recovery both stop working.
- Spigot can override structure salts in `spigot.yml`
  (`seed-village`, `seed-feature`, `seed-monument`, `seed-slime`). The defaults
  are assumed. Custom salts break both structure hits and seed recovery.
- `generate-structures: false` means no structure reset exists. Drops can still
  recover the world RNG state in principle, but there is nothing to walk back to
  and seed recovery has no candidate set.
- Plugins that change drop counts, drop positions, or merge drops break the
  solve. The mod rejects stacked entities rather than guessing.
- Fortune is taken from the held tool, with two safeguards: a single-base ore
  that produced four or more items is treated as Fortune III, and redstone or
  lapis retries Fortune 0 to 3 if the first model fails.
- The global backward search can return a valid but distant decoy region. The
  core also provides a windowed search for a known local reset, but the mod
  intentionally uses the global exploit. Treat the region as the answer and the
  placement chunk as a lead.
- Recovered seeds are the low 48 bits. That is all Minecraft's structure code
  uses. The upper 16 bits cannot be recovered from drops.
- A hit does not reveal blocks, inventory, or a player name.
- Server rules and anti-cheat are your responsibility. This is a cheat mod.

## Build from source

```sh
export JAVA_HOME=/path/to/jdk8
./gradlew :core:test --no-daemon
./gradlew :mod:build --no-daemon
```

The jar lands in `mod/build/libs/randar-189-1.0.0.jar`. The core solver is plain
Java 8 with JUnit 4 tests and no Minecraft dependency. The mod module uses
ForgeGradle 2.1 and mappings `stable_22`.

Tests cover:

- `JavaRandom` against `java.util.Random`;
- every quantity model against the 1.8.9 block classes, including the lapis
  draw order and the redstone `nextInt(1)` at Fortune 0;
- lattice solves for diamond, coal, emerald, lapis, redstone, clay, melon,
  glowstone and sea lantern;
- end-to-end drop to structure region, including negative regions;
- unknown-seed recovery, including the clay-only case;
- slime, fortress, mineshaft and placement formulas against `java.util.Random`;
- attribution wording, so the mod cannot claim more than the RNG knows.

## How it works

Drop entities are placed at `block + 0.25 + 0.5 * nextFloat()` per axis, and
1.8.9 sends positions in 1/32-block units. Each item therefore leaks four bits
per axis: exactly `floor(nextFloat() * 16)`. LattiCG turns those inequalities
into candidate 48-bit LCG states, and a replay of the full drop sequence rejects
the rest.

A structure reset sets `World.rand` to
`worldSeed + salt + regionX * 341873128712 + regionZ * 132897987541`. Walking
back from the drop state, each lag implies at most one region per structure
type. Two independent drops can only agree on the world seed if a chunk
generation event separates them.

The full derivation, constants and limits are in [docs/MATH.md](docs/MATH.md).

## Credits

The drop-to-region idea is the original Randar project. Truncated-LCG recovery
uses [LattiCG](https://github.com/mjtb49/LattiCG) by Matthew Bolan and
contributors. Minecraft is a trademark of Mojang; this project is not
affiliated with Mojang or the original Randar authors.

MIT licensed. See [LICENSE](LICENSE) and
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
