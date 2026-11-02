package io.github.sirhumza.randar189.core;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;

public class JavaRandomTest {

    @Test
    public void matchesJavaUtilRandom() {
        long[] seeds = {-9223372036854775808L, -1L, 0L, 1L, 42L, 123456789L, 0x123456789ABCDEFL};
        int[] bounds = {1, 2, 3, 4, 5, 7, 8, 16, 100, 1000};

        for (long seed : seeds) {
            Random reference = new Random(seed);
            JavaRandom actual = new JavaRandom(seed);
            for (int bound : bounds) {
                for (int i = 0; i < 200; i++) {
                    assertEquals("nextInt(" + bound + ") seed " + seed,
                            reference.nextInt(bound), actual.nextInt(bound));
                }
            }
            for (int i = 0; i < 200; i++) {
                assertEquals(reference.nextFloat(), actual.nextFloat(), 0.0f);
                assertEquals(reference.nextDouble(), actual.nextDouble(), 0.0d);
                assertEquals(reference.nextLong(), actual.nextLong());
            }
        }
    }

    @Test
    public void internalStateRoundTrips() {
        long seed = 0x123456789ABCDEFL;
        JavaRandom random = new JavaRandom(seed);
        assertEquals(seed & Lcg.MASK, random.getSeed());

        JavaRandom restored = JavaRandom.fromInternalState(random.getInternalState());
        assertEquals(random.nextFloat(), restored.nextFloat(), 0.0f);
    }

    @Test
    public void skipMatchesDraws() {
        JavaRandom skipped = new JavaRandom(7L);
        JavaRandom drawn = new JavaRandom(7L);
        skipped.skip(37);
        for (int i = 0; i < 37; i++) {
            drawn.next(32);
        }
        assertEquals(drawn.getInternalState(), skipped.getInternalState());
    }
}
