package io.github.sirhumza.randar189.mod;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Attribution wording is a product decision: it must never imply the RNG
 * identified a player.  These tests pin the honest phrasing.
 */
public class RandarHitTest {

    @Test
    public void localRegionSaysItMightBeAnyoneNearby() {
        RandarHit hit = new RandarHit();
        hit.local = true;

        String text = hit.attribution();

        assertTrue(text, text.contains("in your region"));
        assertTrue(text, text.contains("spawn chunks"));
    }

    @Test
    public void distantHitWithNoRecentMovementBlamesAnotherPlayer() {
        RandarHit hit = distantHit();
        hit.movedRecently = false;

        String text = hit.attribution();

        assertTrue(text, text.contains("200 blocks"));
        assertTrue(text, text.contains("north"));
        assertTrue(text, text.contains("likely another player"));
        assertEquals("200b north - likely another player", hit.attributionShort());
    }

    @Test
    public void recentMovementKeepsTheAlternativeOpen() {
        RandarHit hit = distantHit();
        hit.movedRecently = true;

        String text = hit.attribution();

        assertTrue(text, text.contains("could be another player or your own earlier path"));
    }

    @Test
    public void singleOtherPlayerIsNamedAsALeadNotAConfirmation() {
        RandarHit hit = distantHit();
        hit.movedRecently = false;
        hit.otherPlayersOnline = 1;
        hit.otherPlayersSample = "Steve";

        String text = hit.attribution();

        assertTrue(text, text.contains("only other player online: Steve"));
        assertTrue(hit.attributionShort().contains("Steve"));
    }

    @Test
    public void severalPlayersAreListedAsASample() {
        RandarHit hit = distantHit();
        hit.otherPlayersOnline = 4;
        hit.otherPlayersSample = "Steve, Alex";

        String text = hit.attribution();

        assertTrue(text, text.contains("4 other players online"));
        assertTrue(text, text.contains("Steve, Alex"));
        assertTrue(hit.attributionShort().contains("4 others online"));
    }

    @Test
    public void noOtherPlayersIsReportedPlainly() {
        RandarHit hit = distantHit();
        hit.otherPlayersOnline = 0;

        assertTrue(hit.attribution().contains("no other players in the tab list"));
    }

    @Test
    public void missingContextDoesNotClaimAnIdentity() {
        RandarHit hit = new RandarHit();
        hit.local = null;
        hit.otherPlayersOnline = 0;

        String text = hit.attribution();

        assertTrue(text, text.contains("a region where someone loaded chunks"));
        assertEquals("chunks loaded", hit.attributionShort());
    }

    private static RandarHit distantHit() {
        RandarHit hit = new RandarHit();
        hit.local = false;
        hit.distanceBlocks = 200;
        hit.direction = "north";
        hit.otherPlayersOnline = 0;
        return hit;
    }
}
