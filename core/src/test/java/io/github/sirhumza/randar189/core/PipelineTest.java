package io.github.sirhumza.randar189.core;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** End-to-end: simulated chunk preparation -> ore drops -> solver -> region. */
public class PipelineTest {

    @Test
    public void diamondMonumentPipeline() {
        runScenario(0x123456789ABCDEFL, 1234, -567, StructureType.MONUMENT,
                OreType.DIAMOND, 3, 137);
    }

    @Test
    public void redstoneMonumentPipeline() {
        runScenario(-4172144997902289642L, -55, 77, StructureType.MONUMENT,
                OreType.REDSTONE, 3, 2000);
    }

    @Test
    public void templePipeline() {
        runScenario(0x123456789ABCDEFL, 42, 42, StructureType.TEMPLE,
                OreType.DIAMOND, 3, 300);
    }

    @Test
    public void villagePipeline() {
        runScenario(-123456789L, -1000, -1000, StructureType.VILLAGE,
                OreType.DIAMOND, 3, 50);
    }

    @Test
    public void clayMonumentPipeline() {
        runScenario(0x123456789ABCDEFL, 321, -654, StructureType.MONUMENT,
                OreType.CLAY, 0, 100);
    }

    @Test
    public void melonTemplePipeline() {
        runScenario(-4172144997902289642L, -77, 88, StructureType.TEMPLE,
                OreType.MELON, 3, 500);
    }

    @Test
    public void glowstoneVillagePipeline() {
        runScenario(0x777777777777L, 12, 34, StructureType.VILLAGE,
                OreType.GLOWSTONE, 3, 900);
    }

    private static void runScenario(long worldSeed, int chunkX, int chunkZ,
                                    StructureType lastReset, OreType ore, int fortune,
                                    int unrelatedCalls) {
        // The last check in MapGenBase's 17x17 loop is (chunk + 8, chunk + 8).
        int checkChunkX = chunkX + 8;
        int checkChunkZ = chunkZ + 8;
        int expectedRegionX = Math.floorDiv(checkChunkX, StructureType.SPACING);
        int expectedRegionZ = Math.floorDiv(checkChunkZ, StructureType.SPACING);

        long seed = lastReset.structureSeed(expectedRegionX, expectedRegionZ, worldSeed);
        JavaRandom random = new JavaRandom(seed);

        // The struct itself advances World.rand while it chooses its chunk.
        if (lastReset == StructureType.MONUMENT) {
            random.nextInt(27);
            random.nextInt(27);
            random.nextInt(27);
            random.nextInt(27);
        } else {
            random.nextInt(24);
            random.nextInt(24);
        }

        // Weather, mob spawning, random ticks: draws whose values do not matter.
        for (int i = 0; i < unrelatedCalls; i++) {
            random.nextInt(1000);
        }

        TestSupport.DropRun run = TestSupport.captureUntilEnough(random, ore, fortune);
        List<DropCracker.Candidate> candidates = DropCracker.solve(run.observation, ore, fortune);
        assertFalse("no candidate state for " + ore, candidates.isEmpty());

        StructureLocator.Hit match = null;
        for (DropCracker.Candidate candidate : candidates) {
            StructureLocator.Hit hit = StructureLocator.reverseFirst(
                    candidate.firstPositionState(), worldSeed,
                    StructureType.autoOrder(), StructureLocator.DEFAULT_MAX_LAG);
            if (hit == null) {
                continue;
            }
            if (hit.type() == lastReset
                    && hit.regionX() == expectedRegionX
                    && hit.regionZ() == expectedRegionZ) {
                match = hit;
                break;
            }
            if (match == null) {
                match = hit;
            }
        }

        assertNotNull("no structure reset found in the backward window", match);
        assertEquals(lastReset, match.type());
        assertEquals(expectedRegionX, match.regionX());
        assertEquals(expectedRegionZ, match.regionZ());
        assertTrue("the reset cannot be the observed state itself", match.lag() > 0);
    }
}
