package io.github.sirhumza.randar189.core;

import java.util.Arrays;
import java.util.List;

/**
 * The three 1.8.9 overworld generators that call
 * {@code World.setRandomSeed} while a chunk is being prepared.
 *
 * <p>1.8.9 order in {@code ChunkProviderGenerate.provideChunk} is mineshaft,
 * village, stronghold, temple, ocean monument.  Mineshafts and strongholds do
 * not touch {@code World.rand}, so the ocean monument check is the last thing
 * that resets it.  That is why {@link #autoOrder()} puts monuments first.</p>
 */
public enum StructureType {

    MONUMENT("Ocean monument", 10387313L, true),
    TEMPLE("Temple", 14357617L, false),
    VILLAGE("Village", 10387312L, false);

    /** Chunks between structure regions in 1.8.9. */
    public static final int SPACING = 32;

    /** Minimum distance from a region edge, in chunks. */
    public static final int SEPARATION = 8;

    /** A region is 32 chunks, i.e. 512 blocks, across. */
    public static final int REGION_BLOCKS = SPACING * 16;

    private final String displayName;
    private final long salt;
    private final boolean averagedPlacement;

    StructureType(String displayName, long salt, boolean averagedPlacement) {
        this.displayName = displayName;
        this.salt = salt;
        this.averagedPlacement = averagedPlacement;
    }

    public String displayName() {
        return displayName;
    }

    public long salt() {
        return salt;
    }

    public static List<StructureType> autoOrder() {
        return Arrays.asList(MONUMENT, TEMPLE, VILLAGE);
    }

    /** The exact argument {@code World.setRandomSeed} passes to {@code Random.setSeed}. */
    public long structureSeed(int regionX, int regionZ, long worldSeed) {
        return (regionX * Lcg.STRUCTURE_X_MULTIPLIER
                + regionZ * Lcg.STRUCTURE_Z_MULTIPLIER
                + worldSeed
                + salt) & Lcg.MASK;
    }

    /**
     * The chunk a region would put its structure in.  This is the placement
     * roll only; 1.8.9 additionally checks the biome at that chunk.
     *
     * @return {@code {chunkX, chunkZ}}
     */
    public int[] placementChunk(int regionX, int regionZ, long worldSeed) {
        JavaRandom random = new JavaRandom(structureSeed(regionX, regionZ, worldSeed));
        int offsetX;
        int offsetZ;
        if (averagedPlacement) {
            // Ocean monuments: (nextInt(27) + nextInt(27)) / 2 per axis.
            offsetX = (random.nextInt(27) + random.nextInt(27)) / 2;
            offsetZ = (random.nextInt(27) + random.nextInt(27)) / 2;
        } else {
            // Villages and scattered features (temples): nextInt(24) per axis.
            int span = SPACING - SEPARATION;
            offsetX = random.nextInt(span);
            offsetZ = random.nextInt(span);
        }
        return new int[] {regionX * SPACING + offsetX, regionZ * SPACING + offsetZ};
    }
}
