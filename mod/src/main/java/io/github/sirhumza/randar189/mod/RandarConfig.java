package io.github.sirhumza.randar189.mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.sirhumza.randar189.core.StructureLocator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/** Persistent mod settings, stored as JSON in {@code config/randar-189.json}. */
public final class RandarConfig {

    public static final String SOURCE_MANUAL = "manual";
    public static final String SOURCE_SINGLEPLAYER = "singleplayer";
    public static final String SOURCE_RECOVERED = "recovered";

    private static final Logger LOGGER = LogManager.getLogger("randar189");

    /** Empty when no seed has been supplied.  A string keeps bad input visible. */
    public String worldSeed = "";

    /** How {@link #worldSeed} was obtained: manual, singleplayer or recovered. */
    public String seedSource = "";

    public boolean autoSolve = true;
    public boolean showHud = true;
    public int maxLag = StructureLocator.DEFAULT_MAX_LAG;

    /** Backward window used while recovering an unknown seed. */
    public int recoveryLag = 40_000;

    /** Region window radius around the player, in 512-block regions. */
    public int regionRadius = 1;

    private transient File file;

    public static RandarConfig load(File file) {
        RandarConfig config = new RandarConfig();
        config.file = file;
        if (file.isFile()) {
            try (FileReader reader = new FileReader(file)) {
                RandarConfig loaded = new Gson().fromJson(reader, RandarConfig.class);
                if (loaded != null) {
                    config.worldSeed = loaded.worldSeed == null ? "" : loaded.worldSeed;
                    config.seedSource = loaded.seedSource == null ? "" : loaded.seedSource;
                    config.autoSolve = loaded.autoSolve;
                    config.showHud = loaded.showHud;
                    if (loaded.maxLag > 0) {
                        config.maxLag = loaded.maxLag;
                    }
                    if (loaded.recoveryLag > 0) {
                        config.recoveryLag = loaded.recoveryLag;
                    }
                    if (loaded.regionRadius >= 0) {
                        config.regionRadius = Math.min(2, loaded.regionRadius);
                    }
                }
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("Could not read " + file, e);
            }
        }
        if (config.seedSource.isEmpty() && config.seed() != null) {
            config.seedSource = SOURCE_MANUAL;
        }
        return config;
    }

    public void save() {
        if (file == null) {
            return;
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            return;
        }
        try (FileWriter writer = new FileWriter(file)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(this, writer);
        } catch (IOException e) {
            LOGGER.warn("Could not write " + file, e);
        }
    }

    /**
     * Accepts decimal seeds and {@code 0x}-prefixed hex.  Hex is useful when
     * the server exposes the seed as an unsigned 64-bit value.
     */
    public static Long parseSeed(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            if (trimmed.startsWith("0x") || trimmed.startsWith("0X")) {
                return Long.parseUnsignedLong(trimmed.substring(2), 16);
            }
            return Long.parseLong(trimmed);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The configured seed, or {@code null} when absent or malformed. */
    public Long seed() {
        return parseSeed(worldSeed);
    }

    public void setSeed(long seed) {
        setSeed(seed, SOURCE_MANUAL);
    }

    public void setSeed(long seed, String source) {
        this.worldSeed = Long.toString(seed);
        this.seedSource = source == null ? SOURCE_MANUAL : source;
        save();
    }

    public void clearSeed() {
        this.worldSeed = "";
        this.seedSource = "";
        save();
    }

    /** Human-readable provenance for the GUI and HUD. */
    public String seedSourceLabel() {
        if (seed() == null) {
            return "not set";
        }
        if (SOURCE_RECOVERED.equals(seedSource)) {
            return "recovered (low 48 bits)";
        }
        if (SOURCE_SINGLEPLAYER.equals(seedSource)) {
            return "singleplayer";
        }
        return "manual";
    }
}
