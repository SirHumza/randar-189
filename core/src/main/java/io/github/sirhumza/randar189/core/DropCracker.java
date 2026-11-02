package io.github.sirhumza.randar189.core;

import com.seedfinding.latticg.JavaRandomReverser;
import com.seedfinding.latticg.reversal.calltype.FilteredSkip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Recovers the internal state of the server's {@code World.rand} from the
 * four-bit offsets of an ore's drops.
 *
 * <p>The observed call sequence for a non-silk block break is:</p>
 *
 * <pre>
 *   quantity draws                see {@link #itemsFor}
 *   for each item:
 *       nextFloat()               the drop chance, unconstrained
 *       nextFloat()  x offset
 *       nextFloat()  y offset
 *       nextFloat()  z offset
 * </pre>
 *
 * <p>Four items give twelve four-bit constraints, enough for a handful of
 * states.  Six are used when available; the rest are replayed as a check.</p>
 */
public final class DropCracker {

    /** Four items give 12 nibbles = 48 bits.  Fewer cannot pin a 48-bit state. */
    public static final int MIN_ITEMS = 4;

    /** Extra items improve the solve but enlarge the lattice; six is plenty. */
    public static final int MAX_CONSTRAINT_ITEMS = 6;

    /** Guards the UI against a pathological lattice result. */
    public static final int MAX_CANDIDATES = 64;

    private DropCracker() {
    }

    /**
     * Rolls the total drop count exactly as 1.8.9 does.  This is the single
     * source of truth for both the lattice constraints and the replay check.
     */
    static int itemsFor(JavaRandom random, OreType ore, int fortune) {
        if (ore.isFixedQuantity()) {
            // BlockClay: quantityDropped returns 4 and consumes nothing.
            return ore.baseCount();
        }

        if (ore.isAdditiveFortune()) {
            // quantityDropped(random) first, then nextInt(fortune + 1).
            // nextInt(1) still burns one 31-bit draw, so this runs at Fortune 0 too.
            int base = ore.baseCount();
            if (ore.hasRandomBaseCount()) {
                base += random.nextInt(ore.baseRandomBound());
            }
            return Math.min(ore.maxCount(), base + random.nextInt(fortune + 1));
        }

        // BlockOre: the Fortune multiplier is drawn BEFORE quantityDropped.
        // Getting this backwards breaks lapis at Fortune > 0.
        int multiplier = 1;
        if (fortune > 0) {
            multiplier = Math.max(0, random.nextInt(fortune + 2) - 1) + 1;
        }
        int base = ore.baseCount();
        if (ore.hasRandomBaseCount()) {
            base += random.nextInt(ore.baseRandomBound());
        }
        return base * multiplier;
    }

    /** Mirrors {@link #itemsFor} with unconstrained lattice calls. */
    static void addQuantityDraws(JavaRandomReverser reverser, OreType ore, int fortune) {
        if (ore.isFixedQuantity()) {
            return;
        }
        if (ore.isAdditiveFortune()) {
            if (ore.hasRandomBaseCount()) {
                reverser.consumeNextIntCalls(1, ore.baseRandomBound());
            }
            reverser.consumeNextIntCalls(1, fortune + 1);
            return;
        }
        // Same order as itemsFor: Fortune multiplier, then base count.
        if (fortune > 0) {
            reverser.consumeNextIntCalls(1, fortune + 2);
        }
        if (ore.hasRandomBaseCount()) {
            reverser.consumeNextIntCalls(1, ore.baseRandomBound());
        }
    }

    public static List<Candidate> solve(DropObservation observation, OreType ore, int fortune) {
        if (observation == null || ore == null) {
            throw new IllegalArgumentException("observation and ore are required");
        }
        if (fortune < 0 || fortune > 10) {
            throw new IllegalArgumentException("fortune must be in [0, 10]");
        }
        if (observation.size() < MIN_ITEMS) {
            return Collections.emptyList();
        }

        JavaRandomReverser reverser = new JavaRandomReverser(new ArrayList<FilteredSkip>());
        addQuantityDraws(reverser, ore, fortune);

        int constrained = Math.min(observation.size(), MAX_CONSTRAINT_ITEMS);
        for (int item = 0; item < constrained; item++) {
            // The chance roll happens before every item's position.  It does
            // not constrain the state, but it advances the generator.
            reverser.consumeNextFloatCalls(1);
            QuantizedOffset offset = observation.get(item);
            for (int axis = 0; axis < 3; axis++) {
                int nibble = offset.axis(axis);
                reverser.addNextFloatCall(Quantization.lowerBound(nibble), Quantization.upperBound(nibble));
            }
        }

        long[] seeds = reverser.findAllValidSeeds().limit(MAX_CANDIDATES).toArray();
        List<Candidate> candidates = new ArrayList<Candidate>();
        for (long seed : seeds) {
            Candidate candidate = verify(observation, ore, fortune, seed);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }
        return candidates;
    }

    /**
     * Replays the full drop sequence from a candidate.  This rejects lattice
     * solutions that satisfy the first six items but produce the wrong total
     * count, or a different quantisation on a later item.
     */
    static Candidate verify(DropObservation observation, OreType ore, int fortune, long sequenceState) {
        JavaRandom random = JavaRandom.fromInternalState(sequenceState);

        int items = itemsFor(random, ore, fortune);
        if (items != observation.size()) {
            return null;
        }

        long firstPositionState = -1L;
        for (int item = 0; item < observation.size(); item++) {
            random.nextFloat(); // chance
            if (item == 0) {
                firstPositionState = random.getInternalState();
            }
            QuantizedOffset offset = observation.get(item);
            for (int axis = 0; axis < 3; axis++) {
                if (Quantization.nibble(random.nextFloat()) != offset.axis(axis)) {
                    return null;
                }
            }
        }

        return new Candidate(sequenceState, firstPositionState);
    }

    /** A state that reproduces every observed nibble. */
    public static final class Candidate {

        private final long sequenceState;
        private final long firstPositionState;

        Candidate(long sequenceState, long firstPositionState) {
            this.sequenceState = sequenceState;
            this.firstPositionState = firstPositionState;
        }

        /** Internal state immediately before the sequence's first draw. */
        public long sequenceState() {
            return sequenceState;
        }

        /** Internal state immediately before the first observed x offset. */
        public long firstPositionState() {
            return firstPositionState;
        }

        @Override
        public String toString() {
            return "Candidate{sequence=" + sequenceState + ", firstPosition=" + firstPositionState + "}";
        }
    }
}
