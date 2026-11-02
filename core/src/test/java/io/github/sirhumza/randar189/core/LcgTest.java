package io.github.sirhumza.randar189.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LcgTest {

    @Test
    public void previousInvertsNext() {
        long state = 0L;
        for (int i = 0; i < 1000; i++) {
            long next = Lcg.next(state);
            assertEquals(state, Lcg.previous(next));
            state = next;
        }
    }

    @Test
    public void inverseMultiplierIsCorrect() {
        assertEquals(1L, (Lcg.MULTIPLIER * Lcg.INVERSE_MULTIPLIER) & Lcg.MASK);
    }

    @Test
    public void advanceMatchesRepeatedNext() {
        long state = 0x123456789ABCDEFL & Lcg.MASK;
        long byLoop = state;
        for (int calls = 0; calls < 200; calls++) {
            assertEquals(byLoop, Lcg.advance(state, calls));
            byLoop = Lcg.next(byLoop);
        }
    }

    @Test
    public void seedConversionsRoundTrip() {
        long seed = -4172144997902289642L;
        assertEquals(seed & Lcg.MASK, Lcg.externalSeed(Lcg.internalSeed(seed)));
    }
}
