package io.github.sirhumza.randar189.core;

/**
 * A small, allocation-free reimplementation of {@link java.util.Random}'s
 * observable behaviour.  The solver needs to replay a candidate state exactly,
 * including the rejection loop in {@code nextInt(bound)}.
 */
public final class JavaRandom {

    private long state;

    public JavaRandom(long seed) {
        this.state = Lcg.internalSeed(seed);
    }

    private JavaRandom(long state, boolean ignored) {
        this.state = state & Lcg.MASK;
    }

    /** Creates a generator positioned at an internal 48-bit state. */
    public static JavaRandom fromInternalState(long internalState) {
        return new JavaRandom(internalState, true);
    }

    public long getInternalState() {
        return state;
    }

    /** The value that would have been passed to {@code setSeed}. */
    public long getSeed() {
        return Lcg.externalSeed(state);
    }

    public int next(int bits) {
        if (bits < 1 || bits > 32) {
            throw new IllegalArgumentException("bits must be between 1 and 32");
        }
        state = Lcg.next(state);
        return (int) (state >>> (48 - bits));
    }

    /** {@code Random.nextInt()}: a full 32-bit draw. */
    public int nextInt() {
        return next(32);
    }

    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        if ((bound & -bound) == bound) {
            return (int) ((bound * (long) next(31)) >> 31);
        }
        int bits;
        int value;
        do {
            bits = next(31);
            value = bits % bound;
        } while (bits - value + (bound - 1) < 0);
        return value;
    }

    public float nextFloat() {
        return next(24) / (float) (1 << 24);
    }

    public double nextDouble() {
        return (((long) next(26) << 27) + next(27)) / (double) (1L << 53);
    }

    public long nextLong() {
        return ((long) next(32) << 32) + next(32);
    }

    /** Skips {@code calls} draws without materialising them. */
    public void skip(long calls) {
        state = Lcg.advance(state, calls);
    }
}
