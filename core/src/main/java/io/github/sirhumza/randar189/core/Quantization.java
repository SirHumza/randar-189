package io.github.sirhumza.randar189.core;

/**
 * Conversions between a server-side item position and the four bits that
 * survive the 1.8.9 spawn packet.
 *
 * <p>1.8.9 sends entity positions as fixed-point numbers with five fractional
 * bits ({@code floor(pos * 32)}).  A block-drop item is placed at
 * {@code block + 0.25 + 0.5 * nextFloat()} on each axis, so one axis leaks
 * exactly four bits: {@code floor(nextFloat() * 16)}.</p>
 */
public final class Quantization {

    public static final int NIBBLES = 16;

    private Quantization() {
    }

    /** The four bits carried by one axis of a drop. */
    public static int nibble(float nextFloat) {
        if (nextFloat < 0.0f || nextFloat >= 1.0f) {
            throw new IllegalArgumentException("nextFloat must be in [0, 1)");
        }
        return (int) (nextFloat * 16.0f);
    }

    public static float lowerBound(int nibble) {
        checkNibble(nibble);
        return nibble / 16.0f;
    }

    public static float upperBound(int nibble) {
        checkNibble(nibble);
        return (nibble + 1) / 16.0f;
    }

    /**
     * Recovers the four-bit offset from an entity position as seen by the
     * client.  The position may be either the exact server value or the
     * 1/32-quantised packet value; both give the same nibble because the
     * quantisation step is smaller than the leaked interval.
     */
    public static int nibbleFromPosition(double position) {
        int packet = (int) Math.floor(position * 32.0D);
        int block = (int) Math.floor(position);
        int nibble = packet - block * 32 - 8;
        if (nibble < 0 || nibble >= NIBBLES) {
            throw new IllegalArgumentException("position is not a block-drop offset: " + position);
        }
        return nibble;
    }

    public static int blockFromPosition(double position) {
        return (int) Math.floor(position);
    }

    private static void checkNibble(int nibble) {
        if (nibble < 0 || nibble >= NIBBLES) {
            throw new IllegalArgumentException("nibble must be in [0, 15]: " + nibble);
        }
    }
}
