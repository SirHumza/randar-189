package io.github.sirhumza.randar189.core;

/** One item's four-bit offsets within the broken block, in the order x, y, z. */
public final class QuantizedOffset {

    private final int x;
    private final int y;
    private final int z;

    public QuantizedOffset(int x, int y, int z) {
        check(x);
        check(y);
        check(z);
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public int axis(int index) {
        switch (index) {
            case 0:
                return x;
            case 1:
                return y;
            case 2:
                return z;
            default:
                throw new IndexOutOfBoundsException("axis " + index);
        }
    }

    private static void check(int value) {
        if (value < 0 || value >= Quantization.NIBBLES) {
            throw new IllegalArgumentException("offset must be in [0, 15]: " + value);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof QuantizedOffset)) {
            return false;
        }
        QuantizedOffset that = (QuantizedOffset) other;
        return x == that.x && y == that.y && z == that.z;
    }

    @Override
    public int hashCode() {
        return (x << 8) | (y << 4) | z;
    }

    @Override
    public String toString() {
        return x + "," + y + "," + z;
    }
}
