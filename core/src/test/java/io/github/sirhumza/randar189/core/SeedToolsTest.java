package io.github.sirhumza.randar189.core;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * These locators replay vanilla's mixing formulas with {@link JavaRandom}.
 * The reference side below uses {@link java.util.Random} so a mistake in the
 * reimplementation (or a missing draw) shows up as a mismatch instead of both
 * sides agreeing on the same bug.
 */
public class SeedToolsTest {

    private static final long SEED = 0x123456789ABCL;

    @Test
    public void slimeChunksMatchJavaRandom() {
        int hits = 0;
        for (long seed : new long[] {0L, 1L, -1L, SEED, 0x7FFFFFFFFFFFFFFFL}) {
            for (int chunkX = -120; chunkX <= 120; chunkX += 7) {
                for (int chunkZ = -120; chunkZ <= 120; chunkZ += 7) {
                    boolean expected = referenceSlime(seed, chunkX, chunkZ);
                    assertEquals("slime " + seed + " " + chunkX + "," + chunkZ,
                            expected, SeedTools.isSlimeChunk(seed, chunkX, chunkZ));
                    if (expected) {
                        hits++;
                    }
                }
            }
        }
        assertTrue("the sample should contain slime chunks", hits > 0);
    }

    @Test
    public void netherFortressesMatchJavaRandom() {
        int hits = 0;
        for (int regionX = -25; regionX <= 25; regionX++) {
            for (int regionZ = -25; regionZ <= 25; regionZ++) {
                for (int chunkX = regionX << 4; chunkX < (regionX << 4) + 16; chunkX++) {
                    for (int chunkZ = regionZ << 4; chunkZ < (regionZ << 4) + 16; chunkZ++) {
                        boolean expected = referenceFortress(SEED, chunkX, chunkZ);
                        assertEquals("fortress " + chunkX + "," + chunkZ,
                                expected, SeedTools.isNetherFortressChunk(SEED, chunkX, chunkZ));
                        if (expected) {
                            hits++;
                        }
                    }
                }
            }
        }
        assertTrue("the sample should contain fortress chunks", hits > 0);
        // A 16x16 region places at most one fortress chunk.
        for (int regionX = -5; regionX <= 5; regionX++) {
            for (int regionZ = -5; regionZ <= 5; regionZ++) {
                int count = 0;
                for (int chunkX = regionX << 4; chunkX < (regionX << 4) + 16; chunkX++) {
                    for (int chunkZ = regionZ << 4; chunkZ < (regionZ << 4) + 16; chunkZ++) {
                        if (SeedTools.isNetherFortressChunk(SEED, chunkX, chunkZ)) {
                            count++;
                        }
                    }
                }
                assertTrue("region " + regionX + "," + regionZ + " placed " + count,
                        count <= 1);
            }
        }
    }

    @Test
    public void mineshaftsMatchJavaRandom() {
        int hits = 0;
        for (int chunkX = -400; chunkX <= 400; chunkX += 13) {
            for (int chunkZ = -400; chunkZ <= 400; chunkZ += 13) {
                boolean expected = referenceMineshaft(SEED, chunkX, chunkZ);
                assertEquals("mineshaft " + chunkX + "," + chunkZ,
                        expected, SeedTools.isMineshaftChunk(SEED, chunkX, chunkZ));
                if (expected) {
                    hits++;
                }
            }
        }
        assertTrue("the sample should contain mineshaft chunks", hits > 0);
        // The distance bias makes the origin impossible and a distant chunk likely.
        assertFalse(SeedTools.isMineshaftChunk(SEED, 0, 0));
    }

    @Test
    public void structurePlacementsMatchSetRandomSeed() {
        int[] regions = {-58_594, -1000, -7, 0, 1, 19, 58_594};
        for (StructureType type : StructureType.values()) {
            for (int regionX : regions) {
                for (int regionZ : regions) {
                    assertArrayEquals(type + " " + regionX + "," + regionZ,
                            referencePlacement(type, regionX, regionZ, SEED),
                            SeedTools.structurePlacement(type, regionX, regionZ, SEED));
                }
            }
        }
    }

    @Test
    public void placementsStayInsideTheirRegion() {
        for (StructureType type : StructureType.values()) {
            for (int regionX = -4; regionX <= 4; regionX++) {
                for (int regionZ = -4; regionZ <= 4; regionZ++) {
                    int[] chunk = type.placementChunk(regionX, regionZ, SEED);
                    assertTrue(chunk[0] >= regionX * StructureType.SPACING);
                    assertTrue(chunk[0] < regionX * StructureType.SPACING + StructureType.SPACING);
                    assertTrue(chunk[1] >= regionZ * StructureType.SPACING);
                    assertTrue(chunk[1] < regionZ * StructureType.SPACING + StructureType.SPACING);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Transcribed vanilla implementations, using java.util.Random.
    // ------------------------------------------------------------------

    private static boolean referenceSlime(long worldSeed, int chunkX, int chunkZ) {
        long mixed = (worldSeed
                + (long) (chunkX * chunkX) * 4987142L
                + (long) chunkX * 5947611L
                + (long) (chunkZ * chunkZ) * 4392871L
                + (long) chunkZ * 389711L) ^ 987234911L;
        return new Random(mixed).nextInt(10) == 0;
    }

    private static boolean referenceFortress(long worldSeed, int chunkX, int chunkZ) {
        int regionX = chunkX >> 4;
        int regionZ = chunkZ >> 4;
        Random random = new Random((long) (regionX ^ (regionZ << 4)) ^ worldSeed);
        random.nextInt();
        if (random.nextInt(3) != 0) {
            return false;
        }
        return chunkX == (regionX << 4) + 4 + random.nextInt(8)
                && chunkZ == (regionZ << 4) + 4 + random.nextInt(8);
    }

    private static boolean referenceMineshaft(long worldSeed, int chunkX, int chunkZ) {
        Random seedRandom = new Random(worldSeed);
        long j = seedRandom.nextLong();
        long k = seedRandom.nextLong();
        Random random = new Random((long) chunkX * j ^ (long) chunkZ * k ^ worldSeed);
        random.nextInt(); // MapGenStructure.recursiveGenerate
        if (random.nextDouble() >= 0.004D) {
            return false;
        }
        return random.nextInt(80) < Math.max(Math.abs(chunkX), Math.abs(chunkZ));
    }

    private static int[] referencePlacement(StructureType type, int regionX, int regionZ,
                                            long worldSeed) {
        long seed = (long) regionX * 341873128712L
                + (long) regionZ * 132897987541L
                + worldSeed
                + type.salt();
        Random random = new Random(seed);
        int offsetX;
        int offsetZ;
        if (type == StructureType.MONUMENT) {
            offsetX = (random.nextInt(27) + random.nextInt(27)) / 2;
            offsetZ = (random.nextInt(27) + random.nextInt(27)) / 2;
        } else {
            offsetX = random.nextInt(24);
            offsetZ = random.nextInt(24);
        }
        return new int[] {regionX * 32 + offsetX, regionZ * 32 + offsetZ};
    }
}
