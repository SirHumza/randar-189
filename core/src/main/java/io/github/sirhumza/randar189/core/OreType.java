package io.github.sirhumza.randar189.core;

/**
 * The 1.8.9 overworld blocks whose drops expose consecutive
 * {@code nextFloat()} calls.
 *
 * <p>Diamond, coal and emerald have a fixed base count of one; lapis has a
 * random base count of {@code 4 + nextInt(5)}.  All four use
 * {@code BlockOre.quantityDroppedWithBonus}, which rolls an optional Fortune
 * multiplier <em>before</em> the base count.</p>
 *
 * <p>Redstone is the odd one out.  {@code BlockRedstoneOre} overrides the
 * quantity to {@code 4 + nextInt(2) + nextInt(fortune + 1)}: Fortune adds
 * extra items instead of multiplying, and the bonus roll (including
 * {@code nextInt(1)} at Fortune 0) is always consumed.</p>
 *
 * <p>Clay, melon, glowstone and sea lanterns widen the source list.  Clay is
 * the best one: it always drops exactly four clay balls and burns no RNG on
 * the quantity, so it works at Fortune 0.  Melon and glowstone roll a random
 * base count and then add {@code nextInt(fortune + 1)}; sea lanterns do the
 * same with a lower base and a clamp of five.</p>
 *
 * <p>Nether quartz is deliberately absent.  It only generates in the Nether,
 * whose {@code World.rand} is never reset by the overworld structure checks
 * this solver reverses.</p>
 */
public enum OreType {

    DIAMOND("Diamond ore", 1, 0, QuantityModel.MULTIPLIED, Integer.MAX_VALUE),
    COAL("Coal ore", 1, 0, QuantityModel.MULTIPLIED, Integer.MAX_VALUE),
    EMERALD("Emerald ore", 1, 0, QuantityModel.MULTIPLIED, Integer.MAX_VALUE),
    LAPIS("Lapis lazuli ore", 4, 5, QuantityModel.MULTIPLIED, Integer.MAX_VALUE),
    REDSTONE("Redstone ore", 4, 2, QuantityModel.ADDITIVE, Integer.MAX_VALUE),
    CLAY("Clay", 4, 0, QuantityModel.FIXED, 4),
    MELON("Melon", 3, 5, QuantityModel.ADDITIVE, 9),
    GLOWSTONE("Glowstone", 2, 3, QuantityModel.ADDITIVE, 4),
    SEA_LANTERN("Sea lantern", 2, 2, QuantityModel.ADDITIVE, 5);

    private final String displayName;
    private final int baseCount;
    private final int baseRandomBound;
    private final QuantityModel quantityModel;
    private final int maxCount;

    OreType(String displayName, int baseCount, int baseRandomBound,
            QuantityModel quantityModel, int maxCount) {
        this.displayName = displayName;
        this.baseCount = baseCount;
        this.baseRandomBound = baseRandomBound;
        this.quantityModel = quantityModel;
        this.maxCount = maxCount;
    }

    public String displayName() {
        return displayName;
    }

    /** Count before the Fortune roll: 1 for fixed ores, 4 for redstone/lapis. */
    public int baseCount() {
        return baseCount;
    }

    /** Bound of {@code nextInt} used for the base count, or 0 when there is none. */
    public int baseRandomBound() {
        return baseRandomBound;
    }

    public QuantityModel quantityModel() {
        return quantityModel;
    }

    /** Upper clamp applied after the Fortune roll, or {@code Integer.MAX_VALUE}. */
    public int maxCount() {
        return maxCount;
    }

    public boolean hasRandomBaseCount() {
        return baseRandomBound > 0;
    }

    public boolean isAdditiveFortune() {
        return quantityModel == QuantityModel.ADDITIVE;
    }

    public boolean isFixedQuantity() {
        return quantityModel == QuantityModel.FIXED;
    }
}
