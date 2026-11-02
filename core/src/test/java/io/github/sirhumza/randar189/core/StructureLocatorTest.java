package io.github.sirhumza.randar189.core;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class StructureLocatorTest {

    @Test
    public void reverseFindsMonumentRegionAtLagZero() {
        long worldSeed = 0x123456789ABCDEFL;
        int regionX = 38;
        int regionZ = -18;
        long state = Lcg.internalSeed(StructureType.MONUMENT.structureSeed(regionX, regionZ, worldSeed));

        StructureLocator.Hit hit = StructureLocator.reverseFirst(
                state, worldSeed, StructureType.autoOrder(), 0);

        assertNotNull(hit);
        assertEquals(StructureType.MONUMENT, hit.type());
        assertEquals(regionX, hit.regionX());
        assertEquals(regionZ, hit.regionZ());
        assertEquals(0, hit.lag());
    }

    @Test
    public void reverseStepsBackToTheReset() {
        long worldSeed = -123456789L;
        int regionX = -1024;
        int regionZ = 77;
        long state = Lcg.internalSeed(StructureType.TEMPLE.structureSeed(regionX, regionZ, worldSeed));
        for (int i = 0; i < 137; i++) {
            state = Lcg.next(state);
        }

        StructureLocator.Hit hit = StructureLocator.reverseFirst(
                state, worldSeed, Collections.singletonList(StructureType.TEMPLE), 137);

        assertNotNull(hit);
        assertEquals(StructureType.TEMPLE, hit.type());
        assertEquals(regionX, hit.regionX());
        assertEquals(regionZ, hit.regionZ());
        assertEquals(137, hit.lag());
    }

    @Test
    public void mostRecentResetWins() {
        long worldSeed = 0x123456789ABCDEFL;
        long village = Lcg.internalSeed(StructureType.VILLAGE.structureSeed(10, 10, worldSeed));
        long temple = Lcg.internalSeed(StructureType.TEMPLE.structureSeed(-4, 9, worldSeed));
        long monument = Lcg.internalSeed(StructureType.MONUMENT.structureSeed(38, -18, worldSeed));

        long state = village;
        for (int i = 0; i < 5; i++) {
            state = Lcg.next(state);
        }
        state = temple;
        for (int i = 0; i < 7; i++) {
            state = Lcg.next(state);
        }
        state = monument;
        for (int i = 0; i < 11; i++) {
            state = Lcg.next(state);
        }

        StructureLocator.Hit hit = StructureLocator.reverseFirst(
                state, worldSeed, StructureType.autoOrder(), 20);

        assertNotNull(hit);
        assertEquals(StructureType.MONUMENT, hit.type());
        assertEquals(38, hit.regionX());
        assertEquals(-18, hit.regionZ());
        assertEquals(11, hit.lag());
    }

    @Test
    public void regionsAtTheEdgeOfTheWorldWork() {
        long worldSeed = 42L;
        int[][] regions = {{-58594, -58594}, {58594, 58594}, {0, 0}, {-1, -1}};
        for (int[] region : regions) {
            long state = Lcg.internalSeed(
                    StructureType.VILLAGE.structureSeed(region[0], region[1], worldSeed));
            StructureLocator.Region found = StructureLocator.regionForState(
                    state, worldSeed, StructureType.VILLAGE);
            assertNotNull(found);
            assertEquals(region[0], found.x());
            assertEquals(region[1], found.z());
        }
    }

    @Test
    public void hitPlacementStaysInsideRegion() {
        long worldSeed = 0x123456789ABCDEFL;
        StructureLocator.Hit hit = StructureLocator.reverseFirst(
                Lcg.internalSeed(StructureType.MONUMENT.structureSeed(-1, -1, worldSeed)),
                worldSeed, Arrays.asList(StructureType.MONUMENT), 0);

        assertNotNull(hit);
        assertTrue(hit.chunkX() >= hit.regionX() * 32);
        assertTrue(hit.chunkX() < hit.regionX() * 32 + 32);
        assertTrue(hit.blockX() >= hit.regionMinBlockX());
        assertTrue(hit.blockX() <= hit.regionMaxBlockX());
        assertTrue(hit.blockZ() >= hit.regionMinBlockZ());
        assertTrue(hit.blockZ() <= hit.regionMaxBlockZ());
    }

    @Test
    public void noResetInWindowReturnsNull() {
        StructureLocator.Hit hit = StructureLocator.reverseFirst(
                12345678901234L, 987654321L, StructureType.autoOrder(), 2);
        assertNull(hit);
    }

    @Test
    public void windowedSearchFindsTheExactReset() {
        long worldSeed = -123456789L;
        int regionX = -1024;
        int regionZ = 77;
        long state = Lcg.internalSeed(
                StructureType.TEMPLE.structureSeed(regionX, regionZ, worldSeed));
        for (int i = 0; i < 137; i++) {
            state = Lcg.next(state);
        }

        StructureLocator.Hit hit = StructureLocator.reverseFirstInWindow(
                state, worldSeed, regionX + 1, regionZ - 1, 1, 137);

        assertNotNull(hit);
        assertEquals(StructureType.TEMPLE, hit.type());
        assertEquals(regionX, hit.regionX());
        assertEquals(regionZ, hit.regionZ());
        assertEquals(137, hit.lag());
    }

    @Test
    public void windowedSearchRejectsResetsOutsideTheWindow() {
        long worldSeed = 0x123456789ABCDEFL;
        int regionX = 500;
        int regionZ = -500;
        long state = Lcg.internalSeed(
                StructureType.MONUMENT.structureSeed(regionX, regionZ, worldSeed));

        StructureLocator.Hit hit = StructureLocator.reverseFirstInWindow(
                state, worldSeed, regionX + 3, regionZ, 1, 20000);

        assertNull(hit);
    }
}
