package io.github.sirhumza.randar189.core;

import java.util.Arrays;

/**
 * Recovers the low 48 bits of a world seed from ore drops, without being told
 * the seed.
 *
 * <p>A structure check resets {@code World.rand} to
 * {@code internalSeed(worldSeed + salt + regionX * C1 + regionZ * C2)}.  The
 * drop solver gives the state at the break, but not how many draws happened
 * since that reset.  Walking back {@code maxLag} states and testing every
 * structure type and every region in a small window turns one drop into a
 * candidate set:</p>
 *
 * <pre>
 *   for each lag:
 *       state = previous(state)
 *       for each type and nearby region:
 *           seed = (state ^ multiplier) - salt - regionX*C1 - regionZ*C2
 * </pre>
 *
 * <p>One drop is meaningless on its own.  Two drops separated by a chunk
 * generation event lie on different orbit segments, so their candidate sets
 * share exactly the real seed.  A pair with no reset between them shares an
 * enormous range instead; the caller can detect that by intersection size and
 * ask for another sample.</p>
 *
 * <p>Only the low 48 bits matter to Minecraft's structure code: every
 * {@code setSeed} argument is reduced modulo {@code 2^48}.  The upper 16 bits
 * of the world seed cannot be recovered this way.</p>
 */
public final class SeedRecovery {

    /** Enough for a generous lag window without risking a client OOM. */
    public static final int MAX_CANDIDATES = 50_000_000;

    /** A 5x5 region window already covers a very fast traveller. */
    public static final int MAX_REGION_RADIUS = 2;

    private SeedRecovery() {
    }

    /**
     * Builds the sorted, de-duplicated set of seeds that could explain any of
     * {@code firstPositionStates} at a lag of at most {@code maxLag}.
     *
     * @param firstPositionStates states returned by the drop solver; usually one
     * @param playerRegionX       structure region containing the player
     * @param playerRegionZ       structure region containing the player
     * @param regionRadius        region window radius, 0 to {@value #MAX_REGION_RADIUS}
     * @param maxLag              how many draws back to search
     */
    public static long[] candidates(long[] firstPositionStates,
                                    int playerRegionX, int playerRegionZ,
                                    int regionRadius, int maxLag) {
        if (firstPositionStates == null || firstPositionStates.length == 0) {
            throw new IllegalArgumentException("at least one drop state is required");
        }
        if (regionRadius < 0 || regionRadius > MAX_REGION_RADIUS) {
            throw new IllegalArgumentException(
                    "regionRadius must be between 0 and " + MAX_REGION_RADIUS);
        }
        if (maxLag < 0) {
            throw new IllegalArgumentException("maxLag must be >= 0");
        }

        int span = regionRadius * 2 + 1;
        StructureType[] types = StructureType.values();
        long count = (long) firstPositionStates.length
                * types.length * span * span * (maxLag + 1L);
        if (count > MAX_CANDIDATES) {
            throw new IllegalArgumentException(
                    "candidate set would need " + count + " entries; lower maxLag or regionRadius");
        }

        long[] out = new long[(int) count];
        int at = 0;
        for (long firstPositionState : firstPositionStates) {
            long state = firstPositionState & Lcg.MASK;
            for (int lag = 0; lag <= maxLag; lag++) {
                long input = (state ^ Lcg.MULTIPLIER) & Lcg.MASK;
                for (StructureType type : types) {
                    long byX = (input - type.salt()) & Lcg.MASK;
                    for (int regionX = playerRegionX - regionRadius;
                         regionX <= playerRegionX + regionRadius; regionX++) {
                        long byZ = (byX - regionX * Lcg.STRUCTURE_X_MULTIPLIER) & Lcg.MASK;
                        for (int regionZ = playerRegionZ - regionRadius;
                             regionZ <= playerRegionZ + regionRadius; regionZ++) {
                            out[at++] = (byZ - regionZ * Lcg.STRUCTURE_Z_MULTIPLIER) & Lcg.MASK;
                        }
                    }
                }
                state = Lcg.previous(state);
            }
        }

        Arrays.sort(out);
        return deduplicate(out);
    }

    /** Convenience overload for the usual one-candidate solve. */
    public static long[] candidates(long firstPositionState,
                                    int playerRegionX, int playerRegionZ,
                                    int regionRadius, int maxLag) {
        return candidates(new long[] {firstPositionState},
                playerRegionX, playerRegionZ, regionRadius, maxLag);
    }

    /**
     * Intersects two sorted candidate arrays in linear time.
     * The inputs must be sorted and free of duplicates, as returned by
     * {@link #candidates}.
     */
    public static long[] intersect(long[] a, long[] b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("both candidate sets are required");
        }
        int left = 0;
        int right = 0;
        int matches = 0;
        while (left < a.length && right < b.length) {
            if (a[left] < b[right]) {
                left++;
            } else if (a[left] > b[right]) {
                right++;
            } else {
                matches++;
                left++;
                right++;
            }
        }

        long[] out = new long[matches];
        int at = 0;
        left = 0;
        right = 0;
        while (left < a.length && right < b.length) {
            if (a[left] < b[right]) {
                left++;
            } else if (a[left] > b[right]) {
                right++;
            } else {
                out[at++] = a[left];
                left++;
                right++;
            }
        }
        return out;
    }

    /** Binary search over a sorted candidate array. */
    public static boolean contains(long[] sorted, long seed) {
        if (sorted == null) {
            return false;
        }
        return Arrays.binarySearch(sorted, seed & Lcg.MASK) >= 0;
    }

    private static long[] deduplicate(long[] sorted) {
        if (sorted.length < 2) {
            return sorted;
        }
        int unique = 1;
        for (int i = 1; i < sorted.length; i++) {
            if (sorted[i] != sorted[i - 1]) {
                unique++;
            }
        }
        if (unique == sorted.length) {
            return sorted;
        }
        long[] compact = new long[unique];
        compact[0] = sorted[0];
        int at = 1;
        for (int i = 1; i < sorted.length; i++) {
            if (sorted[i] != sorted[i - 1]) {
                compact[at++] = sorted[i];
            }
        }
        return compact;
    }
}
