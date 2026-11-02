package io.github.sirhumza.randar189.core;

/**
 * The 48-bit linear congruential generator behind {@link java.util.Random}.
 *
 * <p>Keeping the arithmetic in one place matters here: every equation in the
 * exploit is a modular one.  Minecraft's structure code feeds a deliberately
 * mixed seed into {@code Random.setSeed}, and the drop solver walks the same
 * recurrence backwards.</p>
 */
public final class Lcg {

    /** {@code java.util.Random}'s multiplier. */
    public static final long MULTIPLIER = 0x5DEECE66DL;

    /** {@code java.util.Random}'s addend. */
    public static final long ADDEND = 0xBL;

    /** The low 48 bits of the state. */
    public static final long MASK = (1L << 48) - 1L;

    /** {@code MULTIPLIER^-1 mod 2^48}. */
    public static final long INVERSE_MULTIPLIER = 246154705703781L;

    /** x coefficient used by {@code World.setRandomSeed}. */
    public static final long STRUCTURE_X_MULTIPLIER = 341873128712L;

    /** z coefficient used by {@code World.setRandomSeed}. */
    public static final long STRUCTURE_Z_MULTIPLIER = 132897987541L;

    private Lcg() {
    }

    /** Advances one call. */
    public static long next(long state) {
        return (state * MULTIPLIER + ADDEND) & MASK;
    }

    /** Rewinds one call. */
    public static long previous(long state) {
        return ((state - ADDEND) * INVERSE_MULTIPLIER) & MASK;
    }

    /**
     * Advances {@code calls} steps using binary exponentiation of the affine
     * map {@code x -> a*x + c}.  Used by the item-drop verifier.
     */
    public static long advance(long state, long calls) {
        if (calls < 0L) {
            throw new IllegalArgumentException("calls must be >= 0");
        }
        long resultMultiplier = 1L;
        long resultAddend = 0L;
        long stepMultiplier = MULTIPLIER;
        long stepAddend = ADDEND;

        long remaining = calls;
        while (remaining > 0L) {
            if ((remaining & 1L) != 0L) {
                resultAddend = (resultMultiplier * stepAddend + resultAddend) & MASK;
                resultMultiplier = (resultMultiplier * stepMultiplier) & MASK;
            }
            stepAddend = (stepMultiplier * stepAddend + stepAddend) & MASK;
            stepMultiplier = (stepMultiplier * stepMultiplier) & MASK;
            remaining >>>= 1;
        }
        return (resultMultiplier * state + resultAddend) & MASK;
    }

    /** Converts the state carried by {@code Random} into the value passed to {@code setSeed}. */
    public static long externalSeed(long internalState) {
        return (internalState ^ MULTIPLIER) & MASK;
    }

    /** Converts a {@code setSeed} argument into the state carried by {@code Random}. */
    public static long internalSeed(long seed) {
        return (seed ^ MULTIPLIER) & MASK;
    }
}
