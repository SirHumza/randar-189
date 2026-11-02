package io.github.sirhumza.randar189.mod;

import io.github.sirhumza.randar189.core.StructureLocator;
import io.github.sirhumza.randar189.core.StructureType;

/**
 * A persisted result.  Kept as a plain DTO because Gson writes it straight to
 * {@code config/randar-189-hits.json}.
 *
 * <p>The hit itself only identifies a 512x512 structure region where a chunk
 * was generated.  The attribution fields record what was true when the hit
 * happened, so the HUD can say "likely another player" without pretending the
 * RNG knows a name.</p>
 */
public final class RandarHit {

    public String type = StructureType.MONUMENT.name();
    public int regionX;
    public int regionZ;
    public int chunkX;
    public int chunkZ;
    public int blockX;
    public int blockZ;
    public int lag;
    public long worldSeed;
    public long foundAt;
    public int count = 1;

    /** Null on hits written before attribution fields existed. */
    public Boolean local;
    public int distanceBlocks;
    public String direction = "";
    public Boolean movedRecently;
    public int otherPlayersOnline;
    public String otherPlayersSample = "";

    public RandarHit() {
    }

    static RandarHit from(StructureLocator.Hit hit, long worldSeed) {
        RandarHit record = new RandarHit();
        record.type = hit.type().name();
        record.regionX = hit.regionX();
        record.regionZ = hit.regionZ();
        record.chunkX = hit.chunkX();
        record.chunkZ = hit.chunkZ();
        record.blockX = hit.blockX();
        record.blockZ = hit.blockZ();
        record.lag = hit.lag();
        record.worldSeed = worldSeed;
        record.foundAt = System.currentTimeMillis();
        return record;
    }

    public String typeName() {
        return OreItems.structureName(type);
    }

    public int regionMinBlockX() {
        return regionX * StructureType.REGION_BLOCKS;
    }

    public int regionMinBlockZ() {
        return regionZ * StructureType.REGION_BLOCKS;
    }

    public int regionMaxBlockX() {
        return regionMinBlockX() + StructureType.REGION_BLOCKS - 1;
    }

    public int regionMaxBlockZ() {
        return regionMinBlockZ() + StructureType.REGION_BLOCKS - 1;
    }

    public String summary() {
        return typeName() + " region " + regionX + "," + regionZ
                + " (blocks " + regionMinBlockX() + ".." + regionMaxBlockX()
                + ", " + regionMinBlockZ() + ".." + regionMaxBlockZ() + ")";
    }

    public String placement() {
        return "chunk " + chunkX + "," + chunkZ + " at block " + blockX + "," + blockZ;
    }

    /**
     * Honest attribution: a region, not a name.  The other-player count and the
     * player's own recent chunk changes are hints, not proof.
     */
    public String attribution() {
        StringBuilder text = new StringBuilder();
        if (Boolean.TRUE.equals(local)) {
            text.append("in your region (you, a nearby player, or spawn chunks)");
        } else if (Boolean.FALSE.equals(local)) {
            text.append(distanceBlocks).append(" blocks ");
            if (direction != null && !direction.isEmpty()) {
                text.append(direction);
            } else {
                text.append("away");
            }
            if (Boolean.FALSE.equals(movedRecently)) {
                text.append(" - likely another player; you have not entered new chunks recently");
            } else if (Boolean.TRUE.equals(movedRecently)) {
                text.append(" - could be another player or your own earlier path");
            } else {
                text.append(" - someone loaded chunks there");
            }
        } else {
            text.append("a region where someone loaded chunks");
        }

        if (otherPlayersOnline == 1) {
            text.append("; only other player online: ").append(playersText());
        } else if (otherPlayersOnline > 1) {
            text.append("; ").append(otherPlayersOnline).append(" other players online");
            if (!playersText().isEmpty()) {
                text.append(" (").append(playersText()).append(")");
            }
        } else if (otherPlayersOnline == 0) {
            text.append("; no other players in the tab list");
        }
        return text.toString();
    }

    /** One-line HUD variant. */
    public String attributionShort() {
        if (Boolean.TRUE.equals(local)) {
            return "your region";
        }
        if (Boolean.FALSE.equals(local)) {
            StringBuilder text = new StringBuilder();
            text.append(distanceBlocks).append("b");
            if (direction != null && !direction.isEmpty()) {
                text.append(' ').append(direction);
            }
            if (Boolean.FALSE.equals(movedRecently)) {
                text.append(" - likely another player");
            }
            if (otherPlayersOnline == 1) {
                text.append(" (").append(playersText()).append(')');
            } else if (otherPlayersOnline > 1) {
                text.append(" / ").append(otherPlayersOnline).append(" others online");
            }
            return text.toString();
        }
        return "chunks loaded";
    }

    private String playersText() {
        return otherPlayersSample == null ? "" : otherPlayersSample;
    }
}
