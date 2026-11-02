# Changelog

## 1.0.0

First release.

### Drop capture and solving

- Recovers the server's `World.rand` state from four or more item entities
  dropped by one block.
- Supports diamond, coal, emerald, lapis, redstone, clay, melon, glowstone and
  sea lantern drops.
- Correct 1.8.9 quantity models, including the lapis Fortune draw order and the
  redstone `nextInt(fortune + 1)` draw at Fortune 0.
- Replays the full drop sequence to reject bad lattice candidates.

### Structure hits

- Village, temple and ocean monument region detection from the last
  `World.setRandomSeed` reset.
- Fast region scan using the divisibility of the x coefficient by eight.
- Hit history persisted to `config/randar-189-hits.json`.

### Seed recovery

- Recovers the low 48 bits of the world seed from two independent drops
  separated by chunk generation.
- Rejects ambiguous intersections when no reset happened between breaks.
- Persists seed provenance: manual, singleplayer or recovered.

### Seed tools

- `/randar structures`: village, temple and monument placement rolls.
- `/randar slime`: slime chunks.
- `/randar fortress`: nether fortress chunks.
- `/randar mineshaft`: mineshaft chunks.

### Attribution

- Hits record region, distance, compass direction, recent player movement and
  other online players.
- HUD, GUI and `/randar last` show what the hit does and does not prove.

### UI

- `/randar` command with tab completion.
- Config GUI with seed entry, recovery reset, HUD and auto-capture toggles.
- HUD overlay.

### Compatibility

- Minecraft 1.8.9, Forge 11.15.1.2318, Java 8.
- Vanilla, Spigot and Paper 1.8.x.
- Documented Spigot salt overrides, plugin drop changes and decoy hits.
