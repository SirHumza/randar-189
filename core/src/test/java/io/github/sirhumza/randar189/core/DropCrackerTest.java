package io.github.sirhumza.randar189.core;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DropCrackerTest {

    @Test
    public void recoversDiamondFortuneThreeState() {
        JavaRandom random = new JavaRandom(0x1234L);
        TestSupport.DropRun run = TestSupport.captureUntilEnough(random, OreType.DIAMOND, 3);
        assertEquals(4, run.items);

        List<DropCracker.Candidate> candidates = DropCracker.solve(run.observation, OreType.DIAMOND, 3);

        DropCracker.Candidate expected = findByFirstPosition(candidates, run.firstPositionState);
        assertNotNull("the true state must be among the candidates", expected);
        assertEquals(run.sequenceState, expected.sequenceState());
    }

    @Test
    public void recoversRedstoneFortuneThreeState() {
        JavaRandom random = new JavaRandom(0xCAFEBABEL);
        TestSupport.DropRun run = TestSupport.captureUntilEnough(random, OreType.REDSTONE, 3);
        assertTrue(run.items >= DropCracker.MIN_ITEMS);

        List<DropCracker.Candidate> candidates = DropCracker.solve(run.observation, OreType.REDSTONE, 3);

        assertNotNull(findByFirstPosition(candidates, run.firstPositionState));
    }

    @Test
    public void recoversLapisState() {
        JavaRandom random = new JavaRandom(-99887766L);
        TestSupport.DropRun run = TestSupport.captureUntilEnough(random, OreType.LAPIS, 3);

        List<DropCracker.Candidate> candidates = DropCracker.solve(run.observation, OreType.LAPIS, 3);

        assertNotNull(findByFirstPosition(candidates, run.firstPositionState));
    }

    @Test
    public void recoversLapisWithoutFortune() {
        JavaRandom random = new JavaRandom(0x5150L);
        TestSupport.DropRun run = TestSupport.captureUntilEnough(random, OreType.LAPIS, 0);

        List<DropCracker.Candidate> candidates = DropCracker.solve(run.observation, OreType.LAPIS, 0);

        assertNotNull(findByFirstPosition(candidates, run.firstPositionState));
    }

    @Test
    public void recoversRedstoneWithoutFortune() {
        // BlockRedstoneOre still burns nextInt(1) at Fortune 0.  Forgetting it
        // shifts every position call and the solve fails.
        JavaRandom random = new JavaRandom(0xBEEFL);
        TestSupport.DropRun run = TestSupport.captureUntilEnough(random, OreType.REDSTONE, 0);

        List<DropCracker.Candidate> candidates = DropCracker.solve(run.observation, OreType.REDSTONE, 0);

        assertNotNull(findByFirstPosition(candidates, run.firstPositionState));
    }

    @Test
    public void recoversClayState() {
        JavaRandom random = new JavaRandom(0xC1A7L);
        TestSupport.DropRun run = TestSupport.captureUntilEnough(random, OreType.CLAY, 0);
        assertEquals(4, run.items);

        List<DropCracker.Candidate> candidates = DropCracker.solve(run.observation, OreType.CLAY, 0);

        assertNotNull(findByFirstPosition(candidates, run.firstPositionState));
    }

    @Test
    public void recoversMelonState() {
        JavaRandom random = new JavaRandom(0x9E10L);
        TestSupport.DropRun run = TestSupport.captureUntilEnough(random, OreType.MELON, 3);
        assertTrue(run.items >= DropCracker.MIN_ITEMS);

        List<DropCracker.Candidate> candidates = DropCracker.solve(run.observation, OreType.MELON, 3);

        assertNotNull(findByFirstPosition(candidates, run.firstPositionState));
    }

    @Test
    public void recoversGlowstoneState() {
        JavaRandom random = new JavaRandom(0x610551L);
        TestSupport.DropRun run = TestSupport.captureUntilEnough(random, OreType.GLOWSTONE, 3);
        assertTrue(run.items >= DropCracker.MIN_ITEMS);

        List<DropCracker.Candidate> candidates = DropCracker.solve(run.observation, OreType.GLOWSTONE, 3);

        assertNotNull(findByFirstPosition(candidates, run.firstPositionState));
    }

    @Test
    public void recoversSeaLanternState() {
        JavaRandom random = new JavaRandom(0x5EA1L);
        TestSupport.DropRun run = TestSupport.captureUntilEnough(random, OreType.SEA_LANTERN, 3);
        assertTrue(run.items >= DropCracker.MIN_ITEMS);

        List<DropCracker.Candidate> candidates = DropCracker.solve(run.observation, OreType.SEA_LANTERN, 3);

        assertNotNull(findByFirstPosition(candidates, run.firstPositionState));
    }

    @Test
    public void rejectsTooFewDrops() {
        JavaRandom random = new JavaRandom(1L);
        TestSupport.DropRun run = TestSupport.capture(random, OreType.DIAMOND, 0);
        assertEquals(1, run.items);

        assertTrue(DropCracker.solve(run.observation, OreType.DIAMOND, 0).isEmpty());
    }

    @Test
    public void replayRejectsCorruptedObservation() {
        JavaRandom random = new JavaRandom(0x1234L);
        TestSupport.DropRun run = TestSupport.captureUntilEnough(random, OreType.DIAMOND, 3);

        List<QuantizedOffset> corrupted = new ArrayList<QuantizedOffset>();
        for (QuantizedOffset offset : run.observation.offsets()) {
            corrupted.add(offset);
        }
        QuantizedOffset first = corrupted.get(0);
        corrupted.set(0, new QuantizedOffset((first.x() + 1) % 16, first.y(), first.z()));
        DropObservation bad = new DropObservation(
                run.observation.blockX(), run.observation.blockY(), run.observation.blockZ(), corrupted);

        List<DropCracker.Candidate> candidates = DropCracker.solve(bad, OreType.DIAMOND, 3);
        assertEquals(null, findByFirstPosition(candidates, run.firstPositionState));
    }

    private static DropCracker.Candidate findByFirstPosition(
            List<DropCracker.Candidate> candidates, long firstPositionState) {
        for (DropCracker.Candidate candidate : candidates) {
            if (candidate.firstPositionState() == firstPositionState) {
                return candidate;
            }
        }
        return null;
    }
}
