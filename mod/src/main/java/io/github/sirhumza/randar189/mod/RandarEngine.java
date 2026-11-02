package io.github.sirhumza.randar189.mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.github.sirhumza.randar189.core.DropCracker;
import io.github.sirhumza.randar189.core.DropObservation;
import io.github.sirhumza.randar189.core.OreType;
import io.github.sirhumza.randar189.core.Quantization;
import io.github.sirhumza.randar189.core.QuantizedOffset;
import io.github.sirhumza.randar189.core.QuantityModel;
import io.github.sirhumza.randar189.core.SeedRecovery;
import io.github.sirhumza.randar189.core.StructureLocator;
import io.github.sirhumza.randar189.core.StructureType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Captures item entities produced by a broken ore, groups them by the block
 * they came from, and solves the group off the client thread.
 *
 * <p>Grouping by block matters because a single break produces several
 * entities, and every entity's spawn packet leaks four bits per axis.  The
 * first four to six items are enough to recover the server's
 * {@code World.rand} state; the remainder are kept for the replay check.</p>
 *
 * <p>When no seed is configured, each solved drop is also added to a recovery
 * pool.  Two breaks separated by a chunk generation event share exactly one
 * candidate seed; two breaks on the same RNG orbit share a huge range and are
 * rejected as ambiguous.</p>
 */
public final class RandarEngine {

    /** Items of one break usually arrive in the same tick; a small window absorbs lag. */
    private static final int GROUP_IDLE_TICKS = 4;

    /** Dropped items beyond this distance belong to someone else's break. */
    private static final double MAX_DISTANCE_SQ = 12.0D * 12.0D;

    private static final int MAX_HITS = 32;

    /** Recovery is a convenience feature; bound its memory and UI noise. */
    private static final int MAX_RECOVERY_SAMPLES = 6;
    /** A solve can return a handful of states; keep the top eight. */
    private static final int MAX_RECOVERY_STATES = 8;
    private static final int MAX_RECOVERY_MATCHES = 16;

    private static final Logger LOGGER = LogManager.getLogger("randar189");

    private final RandarConfig config;
    private final File hitsFile;
    private final Map<BlockPos, PendingDrop> pending = new HashMap<BlockPos, PendingDrop>();
    private final List<RandarHit> hits = new ArrayList<RandarHit>();
    private final ExecutorService worker;
    private final Object hitLock = new Object();

    private final List<RecoverySample> recoverySamples = new ArrayList<RecoverySample>();
    private final Object recoveryLock = new Object();

    /** Worker-thread only: candidates that survived one intersection and need confirmation. */
    private long[] pendingSeeds;

    private int tick;
    private int lastPlayerChunkX;
    private int lastPlayerChunkZ;
    private int lastChunkChangeTick;
    private boolean playerChunkKnown;
    private long lastTooFewNotice;
    private long lastRecoveryNotice;
    private volatile String status = "Ready";
    private volatile String recoveryStatus = "No samples yet";
    private volatile int recoverySampleCount;

    public RandarEngine(RandarConfig config, File configDir) {
        this.config = config;
        this.hitsFile = new File(configDir, "randar-189-hits.json");
        this.worker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Randar solver");
            thread.setDaemon(true);
            return thread;
        });
        loadHits();
    }

    public RandarConfig config() {
        return config;
    }

    public String status() {
        return status;
    }

    public String recoveryStatus() {
        return recoveryStatus;
    }

    public int recoverySampleCount() {
        return recoverySampleCount;
    }

    public List<RandarHit> hits() {
        synchronized (hitLock) {
            return new ArrayList<RandarHit>(hits);
        }
    }

    public void setSeed(long seed) {
        setSeed(seed, RandarConfig.SOURCE_MANUAL);
    }

    public void setSeed(long seed, String source) {
        config.setSeed(seed, source);
        status = "Seed set to " + seed + " (" + config.seedSourceLabel() + ")";
    }

    public void clearSeed() {
        config.clearSeed();
        status = "World seed cleared.";
    }

    public void setMaxLag(int maxLag) {
        config.maxLag = maxLag;
        config.save();
        status = "Max backward search set to " + maxLag + " draws";
    }

    public void setRecoveryLag(int recoveryLag) {
        config.recoveryLag = recoveryLag;
        config.save();
        status = "Recovery window set to " + recoveryLag + " draws";
    }

    public void clearRecovery() {
        synchronized (recoveryLock) {
            recoverySamples.clear();
            recoverySampleCount = 0;
        }
        pendingSeeds = null;
        recoveryStatus = "No samples yet";
        status = "Seed recovery samples cleared.";
    }

    public void clearHits() {
        synchronized (hitLock) {
            hits.clear();
        }
        saveHits();
        status = "Hits cleared";
    }

    // ------------------------------------------------------------------
    // Capture
    // ------------------------------------------------------------------

    @SubscribeEvent
    public void onEntityJoin(EntityJoinWorldEvent event) {
        if (!config.autoSolve) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null || event.world != mc.theWorld) {
            return;
        }
        if (mc.theWorld.provider.getDimensionId() != 0) {
            // Structure resets only exist in the overworld.  Nether and End
            // drops would otherwise be matched against an unrelated state.
            return;
        }
        if (!(event.entity instanceof EntityItem)) {
            return;
        }

        EntityItem entity = (EntityItem) event.entity;
        ItemStack stack = entity.getEntityItem();
        if (stack == null || stack.stackSize != 1) {
            // A stacked drop has been rewritten by a plugin; its position and
            // count no longer describe one vanilla break.
            return;
        }
        OreType ore = OreItems.classify(stack);
        if (ore == null) {
            return;
        }

        EntityPlayer player = mc.thePlayer;
        double dx = entity.posX - player.posX;
        double dy = entity.posY - player.posY;
        double dz = entity.posZ - player.posZ;
        if (dx * dx + dy * dy + dz * dz > MAX_DISTANCE_SQ) {
            return;
        }

        int x = (int) Math.floor(entity.posX);
        int y = (int) Math.floor(entity.posY);
        int z = (int) Math.floor(entity.posZ);
        QuantizedOffset offset;
        try {
            offset = new QuantizedOffset(
                    Quantization.nibbleFromPosition(entity.posX),
                    Quantization.nibbleFromPosition(entity.posY),
                    Quantization.nibbleFromPosition(entity.posZ));
        } catch (IllegalArgumentException notABlockDrop) {
            return;
        }

        BlockPos pos = new BlockPos(x, y, z);
        PendingDrop group = pending.get(pos);
        if (group == null || group.ore != ore) {
            group = new PendingDrop(pos, ore, fortuneOf(player));
            pending.put(pos, group);
        }
        group.offsets.add(offset);
        group.lastTick = tick;
    }

    private static int fortuneOf(EntityPlayer player) {
        return Math.max(0, EnchantmentHelper.getFortuneModifier(player));
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        tick++;

        trackPlayerChunk();

        Iterator<Map.Entry<BlockPos, PendingDrop>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            PendingDrop group = iterator.next().getValue();
            if (tick - group.lastTick < GROUP_IDLE_TICKS) {
                continue;
            }
            iterator.remove();
            submit(group);
        }
    }

    private void submit(PendingDrop group) {
        if (group.offsets.size() < DropCracker.MIN_ITEMS) {
            // A single-base ore at Fortune 0..2 (or a silk-touch drop) simply
            // does not leak enough bits.  Do not spam chat for every block.
            if (tick - lastTooFewNotice > 600) {
                lastTooFewNotice = tick;
                notice("Need at least " + DropCracker.MIN_ITEMS
                        + " drops from one block. Clay always drops 4; Fortune III diamond/coal/emerald, "
                        + "or redstone/lapis/melon/glowstone/sea lantern works too.");
            }
            return;
        }

        final DropObservation observation = new DropObservation(
                group.pos.getX(), group.pos.getY(), group.pos.getZ(), group.offsets);
        final OreType ore = group.ore;
        final int fortune = effectiveFortune(ore, group.fortune, observation.size());

        final Long seed = effectiveSeed();
        if (seed == null) {
            final int regionX = regionOf(Minecraft.getMinecraft().thePlayer.posX);
            final int regionZ = regionOf(Minecraft.getMinecraft().thePlayer.posZ);
            status = "Seed unknown: collecting a recovery sample...";
            worker.submit(() -> recover(observation, ore, fortune, regionX, regionZ));
            return;
        }

        status = "Solving " + observation.size() + " " + ore.displayName() + " drops...";
        worker.submit(() -> solve(observation, ore, fortune, seed));
    }

    /**
     * A single-base ore cannot drop four items below Fortune III.  Trusting
     * the item count instead of the held tool keeps the solve correct when
     * another player broke the block, or when the tool was switched between
     * the break and the packet arriving.
     */
    private static int effectiveFortune(OreType ore, int heldFortune, int items) {
        if (ore.quantityModel() == QuantityModel.MULTIPLIED
                && ore.baseCount() == 1
                && items >= DropCracker.MIN_ITEMS) {
            return 3;
        }
        return heldFortune;
    }

    /** 512x512-block structure region containing a block coordinate. */
    static int regionOf(double blockCoordinate) {
        return Math.floorDiv((int) Math.floor(blockCoordinate), StructureType.REGION_BLOCKS);
    }

    /**
     * The integrated server exposes the seed; a remote server never sends it,
     * so multiplayer uses recovery or the manual entry.
     */
    private Long effectiveSeed() {
        Long configured = config.seed();
        if (configured != null) {
            return configured;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (!mc.isIntegratedServerRunning()) {
            return null;
        }
        IntegratedServer server = mc.getIntegratedServer();
        if (server == null || server.worldServers == null || server.worldServers.length == 0) {
            return null;
        }
        WorldServer overworld = server.worldServers[0];
        if (overworld == null) {
            return null;
        }
        long seed = overworld.getSeed();
        config.setSeed(seed, RandarConfig.SOURCE_SINGLEPLAYER);
        notice("World seed detected: " + seed);
        return seed;
    }

    // ------------------------------------------------------------------
    // Seed recovery
    // ------------------------------------------------------------------

    private void recover(DropObservation observation, OreType ore, int fortune,
                         int playerRegionX, int playerRegionZ) {
        try {
            List<DropCracker.Candidate> candidates = solveCandidates(observation, ore, fortune);
            if (candidates.isEmpty()) {
                status = "No 1.8.9 drop state matched; cannot recover the seed from this break.";
                return;
            }

            long[] states = new long[Math.min(candidates.size(), MAX_RECOVERY_STATES)];
            for (int i = 0; i < states.length; i++) {
                states[i] = candidates.get(i).firstPositionState();
            }

            long[] current;
            try {
                current = SeedRecovery.candidates(states, playerRegionX, playerRegionZ,
                        config.regionRadius, config.recoveryLag);
            } catch (IllegalArgumentException tooLarge) {
                status = "Recovery window is too large: " + tooLarge.getMessage();
                return;
            }

            if (pendingSeeds != null) {
                long[] narrowed = SeedRecovery.intersect(pendingSeeds, current);
                if (narrowed.length == 1) {
                    acceptRecoveredSeed(narrowed[0], observation, ore, fortune);
                    return;
                }
                pendingSeeds = narrowed.length > 1 ? narrowed : null;
            }

            List<RecoverySample> older;
            synchronized (recoveryLock) {
                older = new ArrayList<RecoverySample>(recoverySamples);
            }

            for (RecoverySample sample : older) {
                long[] previous;
                try {
                    previous = SeedRecovery.candidates(sample.states,
                            sample.regionX, sample.regionZ,
                            config.regionRadius, config.recoveryLag);
                } catch (IllegalArgumentException tooLarge) {
                    status = "Recovery window is too large: " + tooLarge.getMessage();
                    return;
                }
                long[] shared = SeedRecovery.intersect(previous, current);
                if (shared.length == 0) {
                    continue;
                }
                if (shared.length > MAX_RECOVERY_MATCHES) {
                    // No chunk generation between the two breaks: both sets
                    // describe the same orbit and share far too much.
                    continue;
                }
                if (shared.length == 1) {
                    acceptRecoveredSeed(shared[0], observation, ore, fortune);
                    return;
                }
                pendingSeeds = shared;
                status = "Seed recovery: " + shared.length
                        + " candidates left; break one more ore to confirm.";
                recoveryStatus = shared.length + " candidates";
                storeSample(states, playerRegionX, playerRegionZ);
                return;
            }

            storeSample(states, playerRegionX, playerRegionZ);
            recoveryStatus = recoverySampleCount + " sample"
                    + (recoverySampleCount == 1 ? "" : "s") + ", waiting for a match";
            if (recoverySampleCount < 2) {
                status = "Seed unknown. Sample 1 of 2 - move into fresh chunks, then break another ore.";
                notifyRecovery("Seed unknown. Break an ore, move until new chunks load, then break another ore. "
                        + "Randar will intersect the two drops.");
            } else {
                status = "Seed unknown. " + recoverySampleCount
                        + " samples, no match yet - travel further and break another ore.";
                notifyRecovery("No shared seed yet. Travel into newly generated chunks and break another ore.");
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Randar seed recovery failed", e);
            status = "Seed recovery failed: " + e;
        }
    }

    private void acceptRecoveredSeed(long seed, DropObservation observation,
                                     OreType ore, int fortune) {
        pendingSeeds = null;
        config.setSeed(seed, RandarConfig.SOURCE_RECOVERED);
        synchronized (recoveryLock) {
            recoverySamples.clear();
            recoverySampleCount = 0;
        }
        recoveryStatus = "Recovered";
        status = "Seed recovered (low 48 bits): " + seed;
        notifyRecovery("Seed recovered from drop positions: " + seed
                + " (low 48 bits; enough for all structure math).");
        solve(observation, ore, fortune, seed);
    }

    private void storeSample(long[] states, int regionX, int regionZ) {
        synchronized (recoveryLock) {
            recoverySamples.add(new RecoverySample(states, regionX, regionZ));
            while (recoverySamples.size() > MAX_RECOVERY_SAMPLES) {
                recoverySamples.remove(0);
            }
            recoverySampleCount = recoverySamples.size();
        }
    }

    private void notifyRecovery(String message) {
        long now = System.currentTimeMillis();
        if (now - lastRecoveryNotice < 5_000L) {
            return;
        }
        lastRecoveryNotice = now;
        notice(message);
    }

    // ------------------------------------------------------------------
    // Solve
    // ------------------------------------------------------------------

    private void solve(DropObservation observation, OreType ore, int fortune, long worldSeed) {
        try {
            List<DropCracker.Candidate> candidates = solveCandidates(observation, ore, fortune);
            if (candidates.isEmpty()) {
                status = "No 1.8.9 drop state matched; the server may alter drops or World.rand.";
                return;
            }

            StructureLocator.Hit best = null;
            for (DropCracker.Candidate candidate : candidates) {
                StructureLocator.Hit hit = StructureLocator.reverseFirst(
                        candidate.firstPositionState(), worldSeed,
                        StructureType.autoOrder(), config.maxLag);
                if (hit != null && (best == null || hit.lag() < best.lag())) {
                    best = hit;
                }
            }
            if (best == null) {
                status = "State recovered, but no structure reset in " + config.maxLag
                        + " draws. Try /randar lag <n>; check generate-structures.";
                return;
            }

            final RandarHit record = RandarHit.from(best, worldSeed);
            status = record.summary();
            Minecraft.getMinecraft().addScheduledTask(() -> acceptHit(record));
        } catch (RuntimeException e) {
            LOGGER.warn("Randar solve failed", e);
            status = "Solver failed: " + e;
        }
    }

    private static List<DropCracker.Candidate> solveCandidates(DropObservation observation,
                                                               OreType ore, int fortune) {
        List<DropCracker.Candidate> candidates = DropCracker.solve(observation, ore, fortune);
        boolean fortuneForced = ore.quantityModel() == QuantityModel.MULTIPLIED
                && ore.baseCount() == 1
                && observation.size() >= DropCracker.MIN_ITEMS;
        if (candidates.isEmpty() && !fortuneForced) {
            // Redstone and lapis need the Fortune level to model the quantity
            // draws.  If the held tool was not the one that broke the block,
            // try every vanilla level before giving up.
            for (int retry = 0; retry <= 3 && candidates.isEmpty(); retry++) {
                if (retry != fortune) {
                    candidates = DropCracker.solve(observation, ore, retry);
                }
            }
        }
        return candidates;
    }

    private void acceptHit(RandarHit record) {
        captureContext(record);
        boolean isNew = true;
        synchronized (hitLock) {
            if (!hits.isEmpty()) {
                RandarHit previous = hits.get(0);
                if (previous.type.equals(record.type)
                        && previous.regionX == record.regionX
                        && previous.regionZ == record.regionZ) {
                    previous.count++;
                    previous.foundAt = record.foundAt;
                    captureContext(previous);
                    isNew = false;
                }
            }
            if (isNew) {
                hits.add(0, record);
                while (hits.size() > MAX_HITS) {
                    hits.remove(hits.size() - 1);
                }
            }
        }
        saveHits();
        if (isNew) {
            notice(record.summary() + "  (" + record.placement() + ", lag " + record.lag + ")");
        }
    }

    /** Records a 512-block region, not a player identity. */
    private void captureContext(RandarHit record) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        if (player == null) {
            return;
        }
        record.local = regionOf(player.posX) == record.regionX
                && regionOf(player.posZ) == record.regionZ;

        double centerX = record.regionMinBlockX() + StructureType.REGION_BLOCKS / 2.0D;
        double centerZ = record.regionMinBlockZ() + StructureType.REGION_BLOCKS / 2.0D;
        double dx = centerX - player.posX;
        double dz = centerZ - player.posZ;
        record.distanceBlocks = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        record.direction = compass(dx, dz);
        record.movedRecently = (tick - lastChunkChangeTick) < 200;

        record.otherPlayersOnline = 0;
        record.otherPlayersSample = "";
        if (mc.getNetHandler() == null) {
            return;
        }
        StringBuilder names = new StringBuilder();
        int shown = 0;
        for (NetworkPlayerInfo info : mc.getNetHandler().getPlayerInfoMap()) {
            String name = info.getGameProfile().getName();
            if (name == null || name.equals(player.getName())) {
                continue;
            }
            record.otherPlayersOnline++;
            if (shown < 6) {
                if (names.length() > 0) {
                    names.append(", ");
                }
                names.append(name);
                shown++;
            }
        }
        record.otherPlayersSample = names.toString();
    }

    /** 0 = north, 90 = east; a compass bearing, not Minecraft's yaw. */
    private static String compass(double dx, double dz) {
        if (dx == 0.0D && dz == 0.0D) {
            return "";
        }
        double degrees = Math.toDegrees(Math.atan2(dx, -dz));
        if (degrees < 0.0D) {
            degrees += 360.0D;
        }
        String[] names = {"north", "north-east", "east", "south-east",
                "south", "south-west", "west", "north-west"};
        return names[(int) Math.round(degrees / 45.0D) % 8];
    }

    private void trackPlayerChunk() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        int chunkX = Math.floorDiv((int) Math.floor(mc.thePlayer.posX), 16);
        int chunkZ = Math.floorDiv((int) Math.floor(mc.thePlayer.posZ), 16);
        if (!playerChunkKnown) {
            playerChunkKnown = true;
            lastPlayerChunkX = chunkX;
            lastPlayerChunkZ = chunkZ;
            lastChunkChangeTick = tick;
        } else if (chunkX != lastPlayerChunkX || chunkZ != lastPlayerChunkZ) {
            lastPlayerChunkX = chunkX;
            lastPlayerChunkZ = chunkZ;
            lastChunkChangeTick = tick;
        }
    }

    // ------------------------------------------------------------------
    // Persistence and chat
    // ------------------------------------------------------------------

    private void loadHits() {
        if (!hitsFile.isFile()) {
            return;
        }
        try (FileReader reader = new FileReader(hitsFile)) {
            Type type = new TypeToken<List<RandarHit>>() { }.getType();
            List<RandarHit> loaded = new Gson().fromJson(reader, type);
            if (loaded != null) {
                synchronized (hitLock) {
                    hits.addAll(loaded);
                }
            }
        } catch (IOException | RuntimeException e) {
            // History is a convenience; a broken file must not stop the mod.
            LOGGER.debug("Could not read hit history", e);
        }
    }

    private void saveHits() {
        File parent = hitsFile.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            return;
        }
        try (FileWriter writer = new FileWriter(hitsFile)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(hits(), writer);
        } catch (IOException ignored) {
            // Same as above.
        }
    }

    private void notice(final String message) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            EntityPlayer player = Minecraft.getMinecraft().thePlayer;
            if (player != null) {
                player.addChatMessage(new ChatComponentText(
                        EnumChatFormatting.GOLD + "[Randar] " + EnumChatFormatting.RESET + message));
            }
        });
    }

    private static final class RecoverySample {

        final long[] states;
        final int regionX;
        final int regionZ;

        RecoverySample(long[] states, int regionX, int regionZ) {
            this.states = states;
            this.regionX = regionX;
            this.regionZ = regionZ;
        }
    }

    private static final class PendingDrop {

        final BlockPos pos;
        final OreType ore;
        final int fortune;
        final List<QuantizedOffset> offsets = new ArrayList<QuantizedOffset>();
        int lastTick;

        PendingDrop(BlockPos pos, OreType ore, int fortune) {
            this.pos = pos;
            this.ore = ore;
            this.fortune = fortune;
        }
    }
}
