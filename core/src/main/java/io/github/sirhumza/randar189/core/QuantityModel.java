package io.github.sirhumza.randar189.core;

/**
 * How a block's total drop count is rolled.
 *
 * <p>The distinction matters for the solver because each model consumes a
 * different number of {@code nextInt} calls before the position floats.</p>
 */
public enum QuantityModel {

    /**
     * {@code BlockOre}: an optional Fortune multiplier, then an optional random
     * base count.  Diamond, coal, emerald, quartz and lapis use this.
     */
    MULTIPLIED,

    /**
     * {@code BlockRedstoneOre} / {@code BlockMelon} / {@code BlockGlowstone} /
     * {@code BlockSeaLantern}: a random base count plus
     * {@code nextInt(fortune + 1)}.
     */
    ADDITIVE,

    /** {@code BlockClay}: a fixed count, no RNG draws at all. */
    FIXED
}
