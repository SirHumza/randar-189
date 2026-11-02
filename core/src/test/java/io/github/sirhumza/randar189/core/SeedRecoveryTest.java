package io.github.sirhumza.randar189.core;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The multiplayer case: no world seed is known.
 *
 * <p>Each drop produces a set of seeds that could have produced the observed
 * state at some recent lag.  One drop is hopeless - every state implies some
 * seed.  Two drops whose candidate sets come from different orbit segments
 * (because a chunk was generated between them) share exactly the world seed.
 * That intersection is what these tests pin down.</p>
 */
public class SeedRecoveryTest {

    private static final long WORLD_SEED = 0x123456789ABCL;
    private static final int PLAYER_REGION_X = 10;
    private static final int PLAYER_REGION_Z = -7;
    private static final int MAX_LAG = 20_000;

    @Test
    public void oneDropContainsTheTrueSeed() {
        TestSupport.DropRun run = TestSupport.captureAfterReset(
                WORLD_SEED, StructureType.MONUMENT,
                PLAYER_REGION_X + 1, PLAYER_REGION_Z - 1, 500,
                OreType.DIAMOND, 3);

        long[] set = SeedRecovery.candidates(
                statesOf(run, OreType.DIAMOND, 3),
                PLAYER_REGION_X, PLAYER_REGION_Z, 1, MAX_LAG);

        assertTrue(set.length > 0);
        assertTrue(SeedRecovery.contains(set, WORLD_SEED));
    }

    @Test
    public void twoIndependentDropsShareOnlyTheTrueSeed() {
        TestSupport.DropRun first = TestSupport.captureAfterReset(
                WORLD_SEED, StructureType.MONUMENT,
                PLAYER_REGION_X + 1, PLAYER_REGION_Z - 1, 500,
                OreType.DIAMOND, 3);
        TestSupport.DropRun second = TestSupport.captureAfterReset(
                WORLD_SEED, StructureType.TEMPLE,
                PLAYER_REGION_X - 1, PLAYER_REGION_Z + 1, 3_000,
                OreType.REDSTONE, 0);

        long[] a = SeedRecovery.candidates(
                statesOf(first, OreType.DIAMOND, 3),
                PLAYER_REGION_X, PLAYER_REGION_Z, 1, MAX_LAG);
        long[] b = SeedRecovery.candidates(
                statesOf(second, OreType.REDSTONE, 0),
                PLAYER_REGION_X, PLAYER_REGION_Z, 1, MAX_LAG);

        long[] shared = SeedRecovery.intersect(a, b);

        assertArrayEquals(new long[] {WORLD_SEED}, shared);
    }

    @Test
    public void clayDropsRecoverTheSeedWithoutFortune() {
        // Clay always drops exactly four balls and burns no quantity draws, so
        // it is the one source that needs no enchantment at all.
        TestSupport.DropRun first = TestSupport.captureAfterReset(
                WORLD_SEED, StructureType.MONUMENT,
                PLAYER_REGION_X + 1, PLAYER_REGION_Z - 1, 500,
                OreType.CLAY, 0);
        TestSupport.DropRun second = TestSupport.captureAfterReset(
                WORLD_SEED, StructureType.VILLAGE,
                PLAYER_REGION_X - 1, PLAYER_REGION_Z + 1, 4_000,
                OreType.CLAY, 0);

        long[] shared = SeedRecovery.intersect(
                SeedRecovery.candidates(statesOf(first, OreType.CLAY, 0),
                        PLAYER_REGION_X, PLAYER_REGION_Z, 1, MAX_LAG),
                SeedRecovery.candidates(statesOf(second, OreType.CLAY, 0),
                        PLAYER_REGION_X, PLAYER_REGION_Z, 1, MAX_LAG));

        assertArrayEquals(new long[] {WORLD_SEED}, shared);
    }

    @Test
    public void recoveredSeedFindsTheResetRegion() {
        TestSupport.DropRun first = TestSupport.captureAfterReset(
                WORLD_SEED, StructureType.MONUMENT,
                PLAYER_REGION_X + 1, PLAYER_REGION_Z - 1, 500,
                OreType.DIAMOND, 3);
        TestSupport.DropRun second = TestSupport.captureAfterReset(
                WORLD_SEED, StructureType.TEMPLE,
                PLAYER_REGION_X - 1, PLAYER_REGION_Z + 1, 3_000,
                OreType.REDSTONE, 0);

        long[] shared = SeedRecovery.intersect(
                SeedRecovery.candidates(statesOf(first, OreType.DIAMOND, 3),
                        PLAYER_REGION_X, PLAYER_REGION_Z, 1, MAX_LAG),
                SeedRecovery.candidates(statesOf(second, OreType.REDSTONE, 0),
                        PLAYER_REGION_X, PLAYER_REGION_Z, 1, MAX_LAG));
        assertArrayEquals(new long[] {WORLD_SEED}, shared);

        long state = DropCracker.solve(first.observation, OreType.DIAMOND, 3)
                .get(0).firstPositionState();
        StructureLocator.Hit hit = StructureLocator.reverseFirstInWindow(
                state, shared[0], PLAYER_REGION_X, PLAYER_REGION_Z, 2, MAX_LAG);

        assertTrue("recovered seed must find the monument reset", hit != null);
        assertEquals(StructureType.MONUMENT, hit.type());
        assertEquals(PLAYER_REGION_X + 1, hit.regionX());
        assertEquals(PLAYER_REGION_Z - 1, hit.regionZ());
    }

    @Test
    public void aResetOutsideTheWindowIsNotACandidate() {
        TestSupport.DropRun farAway = TestSupport.captureAfterReset(
                WORLD_SEED, StructureType.MONUMENT,
                PLAYER_REGION_X + 5, PLAYER_REGION_Z + 5, 700,
                OreType.DIAMOND, 3);

        long[] set = SeedRecovery.candidates(
                statesOf(farAway, OreType.DIAMOND, 3),
                PLAYER_REGION_X, PLAYER_REGION_Z, 1, MAX_LAG);

        assertFalse(SeedRecovery.contains(set, WORLD_SEED));
    }

    @Test
    public void aThirdDropConfirmsTheIntersection() {
        TestSupport.DropRun first = TestSupport.captureAfterReset(
                WORLD_SEED, StructureType.MONUMENT,
                PLAYER_REGION_X + 1, PLAYER_REGION_Z - 1, 500,
                OreType.DIAMOND, 3);
        TestSupport.DropRun second = TestSupport.captureAfterReset(
                WORLD_SEED, StructureType.TEMPLE,
                PLAYER_REGION_X - 1, PLAYER_REGION_Z + 1, 3_000,
                OreType.REDSTONE, 0);
        TestSupport.DropRun third = TestSupport.captureAfterReset(
                WORLD_SEED, StructureType.VILLAGE,
                PLAYER_REGION_X, PLAYER_REGION_Z, 8_000,
                OreType.LAPIS, 0);

        long[] shared = SeedRecovery.intersect(
                SeedRecovery.candidates(statesOf(first, OreType.DIAMOND, 3),
                        PLAYER_REGION_X, PLAYER_REGION_Z, 1, MAX_LAG),
                SeedRecovery.candidates(statesOf(second, OreType.REDSTONE, 0),
                        PLAYER_REGION_X, PLAYER_REGION_Z, 1, MAX_LAG));
        long[] confirmed = SeedRecovery.intersect(
                shared,
                SeedRecovery.candidates(statesOf(third, OreType.LAPIS, 0),
                        PLAYER_REGION_X, PLAYER_REGION_Z, 1, MAX_LAG));

        assertArrayEquals(new long[] {WORLD_SEED}, confirmed);
    }

    @Test
    public void unrelatedSetsIntersectToNothing() {
        TestSupport.DropRun own = TestSupport.captureAfterReset(
                WORLD_SEED, StructureType.MONUMENT,
                PLAYER_REGION_X, PLAYER_REGION_Z, 100,
                OreType.DIAMOND, 3);
        TestSupport.DropRun other = TestSupport.captureAfterReset(
                0x777777777777L, StructureType.VILLAGE,
                PLAYER_REGION_X, PLAYER_REGION_Z, 1_000,
                OreType.DIAMOND, 3);

        long[] a = SeedRecovery.candidates(
                statesOf(own, OreType.DIAMOND, 3),
                PLAYER_REGION_X, PLAYER_REGION_Z, 1, MAX_LAG);
        long[] b = SeedRecovery.candidates(
                statesOf(other, OreType.DIAMOND, 3),
                PLAYER_REGION_X, PLAYER_REGION_Z, 1, MAX_LAG);

        assertEquals(0, SeedRecovery.intersect(a, b).length);
    }

    @Test
    public void candidatesAreSortedAndDeduplicated() {
        TestSupport.DropRun run = TestSupport.captureAfterReset(
                WORLD_SEED, StructureType.MONUMENT,
                PLAYER_REGION_X, PLAYER_REGION_Z, 0,
                OreType.DIAMOND, 3);

        long[] set = SeedRecovery.candidates(
                statesOf(run, OreType.DIAMOND, 3),
                PLAYER_REGION_X, PLAYER_REGION_Z, 1, 500);

        for (int i = 1; i < set.length; i++) {
            assertTrue("candidate array must be sorted", set[i - 1] < set[i]);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void absurdWindowsAreRejectedBeforeAllocating() {
        SeedRecovery.candidates(new long[] {123456789L}, 0, 0, 2, 5_000_000);
    }

    private static long[] statesOf(TestSupport.DropRun run, OreType ore, int fortune) {
        List<DropCracker.Candidate> candidates = DropCracker.solve(run.observation, ore, fortune);
        assertFalse("the simulated drop must be solvable", candidates.isEmpty());
        List<Long> states = new ArrayList<Long>();
        for (DropCracker.Candidate candidate : candidates) {
            states.add(candidate.firstPositionState());
        }
        long[] out = new long[states.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = states.get(i);
        }
        return out;
    }
}
