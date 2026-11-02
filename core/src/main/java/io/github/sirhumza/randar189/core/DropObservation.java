package io.github.sirhumza.randar189.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * All item entities produced by one broken block.
 *
 * <p>The block coordinate is integer information; the list holds the four-bit
 * x/y/z offsets of each entity.  Items must be in server spawn order.</p>
 */
public final class DropObservation {

    private final int blockX;
    private final int blockY;
    private final int blockZ;
    private final List<QuantizedOffset> offsets;

    public DropObservation(int blockX, int blockY, int blockZ, List<QuantizedOffset> offsets) {
        if (offsets == null || offsets.isEmpty()) {
            throw new IllegalArgumentException("an observation needs at least one drop");
        }
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
        this.offsets = Collections.unmodifiableList(new ArrayList<QuantizedOffset>(offsets));
    }

    public int blockX() {
        return blockX;
    }

    public int blockY() {
        return blockY;
    }

    public int blockZ() {
        return blockZ;
    }

    public int size() {
        return offsets.size();
    }

    public QuantizedOffset get(int index) {
        return offsets.get(index);
    }

    public List<QuantizedOffset> offsets() {
        return offsets;
    }

    @Override
    public String toString() {
        return "DropObservation{" + blockX + "," + blockY + "," + blockZ + " items=" + offsets.size() + "}";
    }
}
