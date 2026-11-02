package io.github.sirhumza.randar189.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Walks the world RNG backwards until it reaches a state that
 * {@code World.setRandomSeed} could have produced, then solves for the region
 * coordinates that created it.
 *
 * <p>For a region {@code (rx, rz)} the reset argument is</p>
 *
 * <pre>
 *   seed = rx * 341873128712 + rz * 132897987541 + worldSeed + salt   (mod 2^48)
 * </pre>
 *
 * <p>The z coefficient is odd, so it is invertible modulo 2^48.  For every
 * possible x we can therefore compute the one z that would explain the state,
 * and keep it only when z is inside the world.</p>
 *
 * <p>The direct scan does 117k multiplies per candidate state.  The x
 * coefficient is divisible by eight, so the low three bits of z are fixed by
 * the state and stepping z by eight lets the matching x residue advance with
 * a single addition.  {@link #regionForState} uses that form; it is about
 * eight times cheaper and produces the same answer.</p>
 */
public final class StructureLocator {

    /** {@code 132897987541^-1 mod 2^48}. */
    public static final long INVERSE_Z = 211541297333629L;

    /**
     * Default region bound for a 30M-block world border:
     * {@code floor(30_000_000 / 16 / 32) = 58_593}, widened by one.
     */
    public static final int MIN_REGION = -58594;
    public static final int MAX_REGION = 58594;

    /** Plenty for a busy server; quiet worlds usually hit in a few hundred. */
    public static final int DEFAULT_MAX_LAG = 20000;

    /**
     * {@code (341873128712 / 8)^-1 mod 2^45}.  The x coefficient is divisible
     * by eight, which leaves the low three bits of the z coordinate free.
     */
    private static final long INVERSE_X_ODD = 1330862368097L;

    /** {@code -132897987541 * INVERSE_X_ODD mod 2^45}. */
    private static final long X_RESIDUE_STEP = 17695715978059L;

    private static final long MASK_45 = (1L << 45) - 1L;

    /** Residues at or above this value represent negative regions. */
    private static final long NEGATIVE_X_LIMIT = (1L << 45) - 58594L;

    /** {@code 132897987541^-1 mod 8}, used to pick the z residue class. */
    private static final long Z_PHASE_MULTIPLIER = 5L;

    private StructureLocator() {
    }

    /**
     * Steps back from {@code firstPositionState} until one of {@code types}
     * could have been the last structure reset.  Types are tried in list order
     * at each lag, so a monument wins ties against a village.
     *
     * <p>This is the global variant: it checks every region the 30M-block
     * world border allows.  Use {@link #reverseFirstInWindow} when the reset
     * is known to be near the player; that variant is thousands of times
     * faster and cannot return a decoy from the far side of the map.</p>
     *
     * @return the first hit, or {@code null} when {@code maxLag} is exhausted
     */
    public static Hit reverseFirst(long firstPositionState, long worldSeed,
                                   List<StructureType> types, int maxLag) {
        if (types == null || types.isEmpty()) {
            throw new IllegalArgumentException("at least one structure type is required");
        }
        if (maxLag < 0) {
            throw new IllegalArgumentException("maxLag must be >= 0");
        }

        long state = firstPositionState & Lcg.MASK;
        for (int lag = 0; lag <= maxLag; lag++) {
            for (StructureType type : types) {
                Region region = regionForState(state, worldSeed, type);
                if (region != null) {
                    return new Hit(type, lag, region.x(), region.z(), worldSeed);
                }
            }
            state = Lcg.previous(state);
        }
        return null;
    }

    /**
     * The exact, fast variant for a reset that happened near the player.
     *
     * <p>Chunk generation only checks regions inside a small window around the
     * generated chunk.  Precomputing those reset states turns the search into
     * a hash lookup per lag instead of a 117k-wide region scan.</p>
     */
    public static Hit reverseFirstInWindow(long firstPositionState, long worldSeed,
                                           int centerRegionX, int centerRegionZ,
                                           int radius, int maxLag) {
        if (radius < 0 || radius > 8) {
            throw new IllegalArgumentException("radius must be between 0 and 8");
        }
        if (maxLag < 0) {
            throw new IllegalArgumentException("maxLag must be >= 0");
        }

        Map<Long, Reset> resets = new HashMap<Long, Reset>();
        for (StructureType type : StructureType.autoOrder()) {
            for (int regionX = centerRegionX - radius;
                 regionX <= centerRegionX + radius; regionX++) {
                for (int regionZ = centerRegionZ - radius;
                     regionZ <= centerRegionZ + radius; regionZ++) {
                    long resetState = Lcg.internalSeed(
                            type.structureSeed(regionX, regionZ, worldSeed));
                    if (!resets.containsKey(resetState)) {
                        resets.put(resetState, new Reset(type, regionX, regionZ));
                    }
                }
            }
        }

        long state = firstPositionState & Lcg.MASK;
        for (int lag = 0; lag <= maxLag; lag++) {
            Reset reset = resets.get(state);
            if (reset != null) {
                return new Hit(reset.type, lag, reset.regionX, reset.regionZ, worldSeed);
            }
            state = Lcg.previous(state);
        }
        return null;
    }

    /**
     * Tests a single state against one structure salt.
     *
     * <p>Rather than scanning all 117k x coordinates with two multiplies each,
     * this walks the z coordinates.  Because the x coefficient is divisible
     * by eight, the low three bits of z are fixed by the state; stepping z by
     * eight lets the matching x residue be advanced with one addition.  That
     * is about eight times less work than the direct formulation.</p>
     */
    public static Region regionForState(long state, long worldSeed, StructureType type) {
        long input = (state ^ Lcg.MULTIPLIER) & Lcg.MASK;
        long target = (input - (worldSeed & Lcg.MASK) - type.salt()) & Lcg.MASK;

        int zPhase = (int) (((target & 7L) * Z_PHASE_MULTIPLIER) & 7L);
        long z = MIN_REGION + (((long) zPhase - (MIN_REGION & 7L)) & 7L);
        long xResidue = ((((target - z * Lcg.STRUCTURE_Z_MULTIPLIER) & Lcg.MASK) >>> 3)
                * INVERSE_X_ODD) & MASK_45;

        for (; z <= MAX_REGION; z += 8L) {
            if (xResidue <= MAX_REGION) {
                return new Region((int) xResidue, (int) z);
            }
            if (xResidue >= NEGATIVE_X_LIMIT) {
                return new Region((int) (xResidue - (1L << 45)), (int) z);
            }
            xResidue = (xResidue + X_RESIDUE_STEP) & MASK_45;
        }
        return null;
    }

    private static final class Reset {

        final StructureType type;
        final int regionX;
        final int regionZ;

        Reset(StructureType type, int regionX, int regionZ) {
            this.type = type;
            this.regionX = regionX;
            this.regionZ = regionZ;
        }
    }

    /** A structure region coordinate. */
    public static final class Region {

        private final int x;
        private final int z;

        Region(int x, int z) {
            this.x = x;
            this.z = z;
        }

        public int x() {
            return x;
        }

        public int z() {
            return z;
        }

        @Override
        public String toString() {
            return x + "," + z;
        }
    }

    /** A confirmed backward hit: structure type, region, placement and lag. */
    public static final class Hit {

        private final StructureType type;
        private final int lag;
        private final int regionX;
        private final int regionZ;
        private final int chunkX;
        private final int chunkZ;
        private final int blockX;
        private final int blockZ;
        private final int regionMinBlockX;
        private final int regionMinBlockZ;
        private final long structureSeed;

        Hit(StructureType type, int lag, int regionX, int regionZ, long worldSeed) {
            this.type = type;
            this.lag = lag;
            this.regionX = regionX;
            this.regionZ = regionZ;
            this.structureSeed = type.structureSeed(regionX, regionZ, worldSeed);
            int[] chunk = type.placementChunk(regionX, regionZ, worldSeed);
            this.chunkX = chunk[0];
            this.chunkZ = chunk[1];
            this.blockX = chunkX * 16;
            this.blockZ = chunkZ * 16;
            this.regionMinBlockX = regionX * StructureType.REGION_BLOCKS;
            this.regionMinBlockZ = regionZ * StructureType.REGION_BLOCKS;
        }

        public StructureType type() {
            return type;
        }

        /** Backward steps from the observed drop state to the structure reset. */
        public int lag() {
            return lag;
        }

        public int regionX() {
            return regionX;
        }

        public int regionZ() {
            return regionZ;
        }

        /** Chunk selected by the region's placement roll. */
        public int chunkX() {
            return chunkX;
        }

        public int chunkZ() {
            return chunkZ;
        }

        /** Block origin (minimum corner) of {@link #chunkX()}, {@link #chunkZ()}. */
        public int blockX() {
            return blockX;
        }

        public int blockZ() {
            return blockZ;
        }

        /** Minimum block coordinate of the 512x512 region. */
        public int regionMinBlockX() {
            return regionMinBlockX;
        }

        public int regionMinBlockZ() {
            return regionMinBlockZ;
        }

        public int regionMaxBlockX() {
            return regionMinBlockX + StructureType.REGION_BLOCKS - 1;
        }

        public int regionMaxBlockZ() {
            return regionMinBlockZ + StructureType.REGION_BLOCKS - 1;
        }

        public long structureSeed() {
            return structureSeed;
        }

        @Override
        public String toString() {
            return type.displayName() + " region " + regionX + "," + regionZ
                    + " (blocks " + regionMinBlockX + ".." + regionMaxBlockX()
                    + ", " + regionMinBlockZ + ".." + regionMaxBlockZ()
                    + "); placement chunk " + chunkX + "," + chunkZ
                    + " at blocks " + blockX + "," + blockZ
                    + "; lag " + lag;
        }
    }
}
