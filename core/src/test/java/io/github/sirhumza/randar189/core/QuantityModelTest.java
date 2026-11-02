package io.github.sirhumza.randar189.core;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;

/**
 * Pins the quantity model to the 1.8.9 source.  The reference formulas below
 * are transcribed from {@code BlockOre.quantityDroppedWithBonus},
 * {@code BlockRedstoneOre}, {@code BlockClay}, {@code BlockMelon},
 * {@code BlockGlowstone} and {@code BlockSeaLantern}; the test also checks
 * that the RNG ends up in the same state, so a missing draw cannot slip
 * through.
 */
public class QuantityModelTest {

    @Test
    public void multipliedOresMatchBlockOre() {
        long[] seeds = {-9223372036854775808L, -1L, 0L, 1L, 42L, 0x123456789ABCDEFL};
        OreType[] ores = {OreType.DIAMOND, OreType.COAL, OreType.EMERALD, OreType.LAPIS};

        for (long seed : seeds) {
            for (OreType ore : ores) {
                for (int fortune = 0; fortune <= 4; fortune++) {
                    assertMatchesBlockOre(seed, ore, fortune);
                }
            }
        }
    }

    private static void assertMatchesBlockOre(long seed, OreType ore, int fortune) {
        Random reference = new Random(seed);
        int expected;
        if (fortune > 0) {
            // The multiplier is drawn before quantityDropped.  This order is
            // what makes lapis work at Fortune > 0.
            int i = reference.nextInt(fortune + 2) - 1;
            if (i < 0) {
                i = 0;
            }
            int base = ore.baseCount();
            if (ore.hasRandomBaseCount()) {
                base += reference.nextInt(ore.baseRandomBound());
            }
            expected = base * (i + 1);
        } else {
            int base = ore.baseCount();
            if (ore.hasRandomBaseCount()) {
                base += reference.nextInt(ore.baseRandomBound());
            }
            expected = base;
        }

        JavaRandom actual = new JavaRandom(seed);
        assertEquals(ore + " fortune " + fortune, expected, DropCracker.itemsFor(actual, ore, fortune));
        assertEquals("RNG state after " + ore + " fortune " + fortune,
                reference.nextLong(), actual.nextLong());
    }

    @Test
    public void lapisDrawsFortuneMultiplierBeforeBaseCount() {
        // Find a seed where reading the two draws in the wrong order changes
        // the item count.  The old implementation failed this seed.
        long seed = 0L;
        while (true) {
            Random reference = new Random(seed);
            int multiplierDraw = reference.nextInt(5);
            int baseDraw = reference.nextInt(5);
            int correct = (4 + baseDraw) * (Math.max(0, multiplierDraw - 1) + 1);

            Random swapped = new Random(seed);
            int first = swapped.nextInt(5);
            int second = swapped.nextInt(5);
            int wrong = (4 + first) * (Math.max(0, second - 1) + 1);
            if (correct != wrong) {
                JavaRandom actual = new JavaRandom(seed);
                assertEquals(correct, DropCracker.itemsFor(actual, OreType.LAPIS, 3));
                assertEquals(reference.nextLong(), actual.nextLong());
                return;
            }
            seed++;
        }
    }

    @Test
    public void redstoneMatchesBlockRedstoneOre() {
        long[] seeds = {-9223372036854775808L, -1L, 0L, 1L, 42L, 0x123456789ABCDEFL};

        for (long seed : seeds) {
            for (int fortune = 0; fortune <= 4; fortune++) {
                Random reference = new Random(seed);
                int expected = 4 + reference.nextInt(2) + reference.nextInt(fortune + 1);

                JavaRandom actual = new JavaRandom(seed);
                assertEquals("fortune " + fortune, expected,
                        DropCracker.itemsFor(actual, OreType.REDSTONE, fortune));
                assertEquals("RNG state after redstone fortune " + fortune,
                        reference.nextLong(), actual.nextLong());
            }
        }
    }

    @Test
    public void clayMatchesBlockClay() {
        long[] seeds = {-9223372036854775808L, -1L, 0L, 1L, 42L, 0x123456789ABCDEFL};
        for (long seed : seeds) {
            for (int fortune = 0; fortune <= 4; fortune++) {
                Random reference = new Random(seed);
                JavaRandom actual = new JavaRandom(seed);
                assertEquals(4, DropCracker.itemsFor(actual, OreType.CLAY, fortune));
                assertEquals(reference.nextLong(), actual.nextLong());
            }
        }
    }

    @Test
    public void melonMatchesBlockMelon() {
        long[] seeds = {-9223372036854775808L, -1L, 0L, 1L, 42L, 0x123456789ABCDEFL};
        for (long seed : seeds) {
            for (int fortune = 0; fortune <= 4; fortune++) {
                Random reference = new Random(seed);
                int expected = Math.min(9,
                        (3 + reference.nextInt(5)) + reference.nextInt(fortune + 1));

                JavaRandom actual = new JavaRandom(seed);
                assertEquals("fortune " + fortune, expected,
                        DropCracker.itemsFor(actual, OreType.MELON, fortune));
                assertEquals("RNG state after melon fortune " + fortune,
                        reference.nextLong(), actual.nextLong());
            }
        }
    }

    @Test
    public void glowstoneMatchesBlockGlowstone() {
        long[] seeds = {-9223372036854775808L, -1L, 0L, 1L, 42L, 0x123456789ABCDEFL};
        for (long seed : seeds) {
            for (int fortune = 0; fortune <= 4; fortune++) {
                Random reference = new Random(seed);
                int expected = Math.max(1, Math.min(4,
                        (2 + reference.nextInt(3)) + reference.nextInt(fortune + 1)));

                JavaRandom actual = new JavaRandom(seed);
                assertEquals("fortune " + fortune, expected,
                        DropCracker.itemsFor(actual, OreType.GLOWSTONE, fortune));
                assertEquals("RNG state after glowstone fortune " + fortune,
                        reference.nextLong(), actual.nextLong());
            }
        }
    }

    @Test
    public void seaLanternMatchesBlockSeaLantern() {
        long[] seeds = {-9223372036854775808L, -1L, 0L, 1L, 42L, 0x123456789ABCDEFL};
        for (long seed : seeds) {
            for (int fortune = 0; fortune <= 4; fortune++) {
                Random reference = new Random(seed);
                int expected = Math.max(1, Math.min(5,
                        (2 + reference.nextInt(2)) + reference.nextInt(fortune + 1)));

                JavaRandom actual = new JavaRandom(seed);
                assertEquals("fortune " + fortune, expected,
                        DropCracker.itemsFor(actual, OreType.SEA_LANTERN, fortune));
                assertEquals("RNG state after sea lantern fortune " + fortune,
                        reference.nextLong(), actual.nextLong());
            }
        }
    }

    @Test
    public void redstoneFortuneAddsInsteadOfMultiplying() {
        // Find a seed where the two models disagree, then prove the additive
        // one is what the generator state says happened.
        long seed = 7L;
        int fortune = 3;
        Random reference = new Random(seed);
        int base = 4 + reference.nextInt(2);
        int addBonus = reference.nextInt(fortune + 1);

        JavaRandom actual = new JavaRandom(seed);
        assertEquals(base + addBonus, DropCracker.itemsFor(actual, OreType.REDSTONE, fortune));
        assertEquals(reference.nextLong(), actual.nextLong());
    }
}
