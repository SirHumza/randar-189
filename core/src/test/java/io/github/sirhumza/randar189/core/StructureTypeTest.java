package io.github.sirhumza.randar189.core;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StructureTypeTest {

    @Test
    public void placementMatchesJavaUtilRandom() {
        long worldSeed = -4172144997902289642L;
        int[] regions = {-58594, -1024, -32, -1, 0, 1, 31, 32, 58594};

        for (StructureType type : StructureType.values()) {
            for (int regionX : regions) {
                for (int regionZ : regions) {
                    long seed = type.structureSeed(regionX, regionZ, worldSeed);
                    Random reference = new Random(seed);
                    int offsetX;
                    int offsetZ;
                    if (type == StructureType.MONUMENT) {
                        offsetX = (reference.nextInt(27) + reference.nextInt(27)) / 2;
                        offsetZ = (reference.nextInt(27) + reference.nextInt(27)) / 2;
                    } else {
                        offsetX = reference.nextInt(24);
                        offsetZ = reference.nextInt(24);
                    }

                    int[] chunk = type.placementChunk(regionX, regionZ, worldSeed);
                    assertEquals(regionX * 32 + offsetX, chunk[0]);
                    assertEquals(regionZ * 32 + offsetZ, chunk[1]);
                    assertTrue(chunk[0] >= regionX * 32 && chunk[0] < regionX * 32 + 32);
                    assertTrue(chunk[1] >= regionZ * 32 && chunk[1] < regionZ * 32 + 32);
                }
            }
        }
    }

    @Test
    public void autoOrderPutsMonumentFirst() {
        assertEquals(StructureType.MONUMENT, StructureType.autoOrder().get(0));
        assertEquals(StructureType.TEMPLE, StructureType.autoOrder().get(1));
        assertEquals(StructureType.VILLAGE, StructureType.autoOrder().get(2));
    }

    @Test
    public void saltsAreThe189Values() {
        assertEquals(10387312L, StructureType.VILLAGE.salt());
        assertEquals(14357617L, StructureType.TEMPLE.salt());
        assertEquals(10387313L, StructureType.MONUMENT.salt());
        assertEquals(32, StructureType.SPACING);
        assertEquals(8, StructureType.SEPARATION);
        assertEquals(512, StructureType.REGION_BLOCKS);
    }

    @Test
    public void negativeRegionsStayInBounds() {
        int[] chunk = StructureType.MONUMENT.placementChunk(-1, -1, 123L);
        assertTrue(chunk[0] >= -32 && chunk[0] <= -1);
        assertTrue(chunk[1] >= -32 && chunk[1] <= -1);
    }
}
