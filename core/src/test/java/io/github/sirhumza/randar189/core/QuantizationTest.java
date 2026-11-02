package io.github.sirhumza.randar189.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class QuantizationTest {

    @Test
    public void packetCellsRecoverEveryNibble() {
        int[] blocks = {-1000, -100, -1, 0, 1, 100, 1_000_000};
        for (int block : blocks) {
            for (int nibble = 0; nibble < 16; nibble++) {
                double position = block + 0.25D + (nibble + 0.5D) / 32.0D;
                assertEquals("block " + block + " nibble " + nibble,
                        nibble, Quantization.nibbleFromPosition(position));
            }
        }
    }

    @Test
    public void exactServerPositionsRecoverEveryNibble() {
        for (int block = -3; block <= 3; block++) {
            for (int nibble = 0; nibble < 16; nibble++) {
                float f = (nibble + 0.5f) / 16.0f;
                double position = block + 0.25D + 0.5D * f;
                assertEquals(nibble, Quantization.nibbleFromPosition(position));
            }
        }
    }

    @Test
    public void blockFloorHandlesNegatives() {
        assertEquals(-1, Quantization.blockFromPosition(-0.0001D));
        assertEquals(-1, Quantization.blockFromPosition(-1.0D));
        assertEquals(0, Quantization.blockFromPosition(0.999D));
    }

    @Test
    public void boundsAreTheNibbleInterval() {
        assertEquals(0.0f, Quantization.lowerBound(0), 0.0f);
        assertEquals(1.0f, Quantization.upperBound(15), 0.0f);
        assertEquals(0, Quantization.nibble(0.0f));
        assertEquals(1, Quantization.nibble(0.0625f));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonDropPositions() {
        Quantization.nibbleFromPosition(0.1D);
    }

    @Test
    public void nibbleRejectsOutOfRange() {
        try {
            Quantization.nibble(1.0f);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
