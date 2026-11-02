package io.github.sirhumza.randar189.core;

/**
 * Exact 1.8.9 seed-based locators for things that are not the shared
 * {@code World.rand} side channel.
 *
 * <p>Once the world seed is known (from singleplayer, a manual entry, or
 * {@link SeedRecovery}), these are pure functions of the seed and a chunk
 * coordinate.  They are useful when nobody is moving and Randar has no fresh
 * structure reset to reverse.</p>
 */
public final class SeedTools {

    /** Spigot's default slime salt; servers may change {@code seed-slime}. */
    public static final long SLIME_SALT = 987234911L;

    private SeedTools() {
    }

    /**
     * {@code ChunkProviderGenerate.isSlimeChunk}: one {@code nextInt(10)} from a
     * seed mixed from the world seed and the chunk coordinates.
     */
    public static boolean isSlimeChunk(long worldSeed, int chunkX, int chunkZ) {
        long mixed = (worldSeed
                + (long) (chunkX * chunkX) * 4987142L
                + (long) chunkX * 5947611L
                + (long) (chunkZ * chunkZ) * 4392871L
                + (long) chunkZ * 389711L) ^ SLIME_SALT;
        return new JavaRandom(mixed).nextInt(10) == 0;
    }

    /**
     * {@code MapGenNetherBridge.canSpawnStructureAtCoords}: one fortress per
     * 16x16 chunk region, with a 1-in-3 chance and a rolled chunk inside it.
     */
    public static boolean isNetherFortressChunk(long worldSeed, int chunkX, int chunkZ) {
        int regionX = chunkX >> 4;
        int regionZ = chunkZ >> 4;
        JavaRandom random = new JavaRandom(
                (long) (regionX ^ (regionZ << 4)) ^ worldSeed);
        random.nextInt(); // MapGenStructure.recursiveGenerate discard
        if (random.nextInt(3) != 0) {
            return false;
        }
        return chunkX == (regionX << 4) + 4 + random.nextInt(8)
                && chunkZ == (regionZ << 4) + 4 + random.nextInt(8);
    }

    /**
     * {@code MapGenMineshaft.canSpawnStructureAtCoords}: a 0.4% roll followed
     * by a distance bias, using the per-chunk map-generator seed.
     *
     * <p>Mineshafts are more common far from the origin because the second
     * roll has to beat {@code max(|chunkX|, |chunkZ|)} out of 80.</p>
     */
    public static boolean isMineshaftChunk(long worldSeed, int chunkX, int chunkZ) {
        // MapGenBase.generate seeds its per-chunk generator from two longs
        // drawn once from a Random(worldSeed).
        JavaRandom seedRandom = new JavaRandom(worldSeed);
        long j = seedRandom.nextLong();
        long k = seedRandom.nextLong();
        long mixed = (long) chunkX * j ^ (long) chunkZ * k ^ worldSeed;

        JavaRandom random = new JavaRandom(mixed);
        random.nextInt(); // MapGenStructure.recursiveGenerate discard
        if (random.nextDouble() >= 0.004D) {
            return false;
        }
        return random.nextInt(80) < Math.max(Math.abs(chunkX), Math.abs(chunkZ));
    }

    /**
     * The chunk a structure region places its candidate in.  This is the
     * {@code World.setRandomSeed} placement roll; 1.8.9 additionally checks
     * the biome before actually generating the structure.
     */
    public static int[] structurePlacement(StructureType type, int regionX, int regionZ,
                                           long worldSeed) {
        return type.placementChunk(regionX, regionZ, worldSeed);
    }
}
