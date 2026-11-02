package io.github.sirhumza.randar189.core;

import java.util.ArrayList;
import java.util.List;

/** Deterministic drop-sequence generation shared by the tests. */
final class TestSupport {

    private TestSupport() {
    }

    static DropRun capture(JavaRandom random, OreType ore, int fortune) {
        long sequenceState = random.getInternalState();
        int items = DropCracker.itemsFor(random, ore, fortune);

        List<QuantizedOffset> offsets = new ArrayList<QuantizedOffset>(items);
        long firstPositionState = -1L;
        for (int item = 0; item < items; item++) {
            random.nextFloat(); // chance
            if (item == 0) {
                firstPositionState = random.getInternalState();
            }
            int x = Quantization.nibble(random.nextFloat());
            int y = Quantization.nibble(random.nextFloat());
            int z = Quantization.nibble(random.nextFloat());
            offsets.add(new QuantizedOffset(x, y, z));
        }

        DropObservation observation = new DropObservation(0, 64, 0, offsets);
        return new DropRun(observation, sequenceState, firstPositionState, items);
    }

    /** Keeps consuming complete sequences until one has enough drops to solve. */
    static DropRun captureUntilEnough(JavaRandom random, OreType ore, int fortune) {
        while (true) {
            DropRun run = capture(random, ore, fortune);
            if (run.items >= DropCracker.MIN_ITEMS) {
                return run;
            }
        }
    }

    /**
     * Simulates chunk preparation at one structure region, unrelated world
     * draws (weather, ticks), and then a complete ore break.
     */
    static DropRun captureAfterReset(long worldSeed, StructureType type,
                                     int regionX, int regionZ, int unrelatedCalls,
                                     OreType ore, int fortune) {
        JavaRandom random = new JavaRandom(type.structureSeed(regionX, regionZ, worldSeed));
        if (type == StructureType.MONUMENT) {
            random.nextInt(27);
            random.nextInt(27);
            random.nextInt(27);
            random.nextInt(27);
        } else {
            random.nextInt(24);
            random.nextInt(24);
        }
        for (int i = 0; i < unrelatedCalls; i++) {
            random.nextInt(1000);
        }
        return captureUntilEnough(random, ore, fortune);
    }

    static final class DropRun {

        final DropObservation observation;
        final long sequenceState;
        final long firstPositionState;
        final int items;

        DropRun(DropObservation observation, long sequenceState, long firstPositionState, int items) {
            this.observation = observation;
            this.sequenceState = sequenceState;
            this.firstPositionState = firstPositionState;
            this.items = items;
        }
    }
}
