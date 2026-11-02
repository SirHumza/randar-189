package io.github.sirhumza.randar189.mod;

import io.github.sirhumza.randar189.core.SeedTools;
import io.github.sirhumza.randar189.core.StructureType;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Client-side {@code /randar} command.  Alias: {@code /rdr}. */
public final class RandarCommand implements ICommand {

    private static final List<String> ROOT = Arrays.asList(
            "seed", "hud", "auto", "gui", "clear", "last", "status", "lag",
            "recover", "recoverlag", "structures", "slime", "fortress", "mineshaft");

    private final RandarEngine engine;

    public RandarCommand(RandarEngine engine) {
        this.engine = engine;
    }

    @Override
    public String getCommandName() {
        return "randar";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/randar <seed|hud|auto|gui|clear|last|status|lag|recover|recoverlag"
                + "|structures|slime|fortress|mineshaft>";
    }

    @Override
    public List<String> getCommandAliases() {
        return Collections.singletonList("rdr");
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            send(sender, getCommandUsage(sender));
            return;
        }

        String sub = args[0].toLowerCase();
        if ("seed".equals(sub)) {
            handleSeed(sender, args);
        } else if ("hud".equals(sub)) {
            engine.config().showHud = parseToggle(sender, args, engine.config().showHud);
            engine.config().save();
            send(sender, "HUD " + onOff(engine.config().showHud));
        } else if ("auto".equals(sub)) {
            engine.config().autoSolve = parseToggle(sender, args, engine.config().autoSolve);
            engine.config().save();
            send(sender, "Auto-capture " + onOff(engine.config().autoSolve));
        } else if ("gui".equals(sub)) {
            Minecraft.getMinecraft().displayGuiScreen(new RandarGui(engine));
        } else if ("clear".equals(sub)) {
            engine.clearHits();
            send(sender, "Hit history cleared.");
        } else if ("lag".equals(sub)) {
            handleLag(sender, args);
        } else if ("recover".equals(sub)) {
            handleRecover(sender, args);
        } else if ("recoverlag".equals(sub)) {
            handleRecoveryLag(sender, args);
        } else if ("structures".equals(sub)) {
            handleStructures(sender, args);
        } else if ("slime".equals(sub)) {
            handleSlime(sender, args);
        } else if ("fortress".equals(sub)) {
            handleFortress(sender, args);
        } else if ("mineshaft".equals(sub)) {
            handleMineshaft(sender, args);
        } else if ("last".equals(sub)) {
            List<RandarHit> hits = engine.hits();
            if (hits.isEmpty()) {
                send(sender, "No hits yet.");
            } else {
                RandarHit hit = hits.get(0);
                send(sender, hit.summary());
                send(sender, hit.placement() + ", lag " + hit.lag);
                send(sender, hit.attribution());
                if (hit.count > 1) {
                    send(sender, "Seen " + hit.count + " times from independent breaks.");
                }
            }
        } else if ("status".equals(sub)) {
            send(sender, "Seed: " + seedText(engine));
            send(sender, "Auto-capture: " + onOff(engine.config().autoSolve)
                    + ", HUD: " + onOff(engine.config().showHud)
                    + ", max lag: " + engine.config().maxLag);
            send(sender, "Recovery: " + engine.recoveryStatus()
                    + " (window " + engine.config().recoveryLag
                    + ", samples " + engine.recoverySampleCount() + ")");
            send(sender, engine.status());
        } else {
            send(sender, getCommandUsage(sender));
        }
    }

    private void handleSeed(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "World seed: " + seedText(engine));
            send(sender, "Use /randar seed <seed>, /randar seed 0x<hex>, or /randar seed clear.");
            return;
        }
        if ("clear".equalsIgnoreCase(args[1])) {
            engine.clearSeed();
            send(sender, "World seed cleared. Randar will recover it from drops.");
            return;
        }
        Long seed = RandarConfig.parseSeed(args[1]);
        if (seed == null) {
            send(sender, "That is not a whole-number seed.");
            return;
        }
        engine.setSeed(seed);
        send(sender, "World seed set to " + seed + ".");
    }

    private void handleLag(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "Backward search limit: " + engine.config().maxLag + " draws.");
            return;
        }
        int lag;
        try {
            lag = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            send(sender, "Expected a number of draws.");
            return;
        }
        if (lag < 100 || lag > 2_000_000) {
            send(sender, "Keep the limit between 100 and 2,000,000. Higher values take longer.");
            return;
        }
        engine.setMaxLag(lag);
        send(sender, "Backward search limit set to " + lag + " draws.");
    }

    private void handleRecover(ICommandSender sender, String[] args) {
        if (args.length >= 2 && "clear".equalsIgnoreCase(args[1])) {
            engine.clearRecovery();
            send(sender, "Seed recovery samples cleared.");
            return;
        }
        send(sender, "Seed recovery: " + engine.recoveryStatus()
                + " (" + engine.recoverySampleCount() + " samples, window "
                + engine.config().recoveryLag + ").");
        send(sender, "Break an ore with 4+ drops, move until new chunks load, "
                + "then break another. Two independent samples share only the world seed.");
    }

    private void handleRecoveryLag(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "Recovery window: " + engine.config().recoveryLag + " draws.");
            return;
        }
        int lag;
        try {
            lag = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            send(sender, "Expected a number of draws.");
            return;
        }
        if (lag < 1_000 || lag > 400_000) {
            send(sender, "Keep the recovery window between 1,000 and 400,000 draws.");
            return;
        }
        engine.setRecoveryLag(lag);
        send(sender, "Recovery window set to " + lag + " draws. "
                + "Larger windows use more memory but tolerate a longer gap since chunk generation.");
    }

    // ------------------------------------------------------------------
    // Seed-based locators
    // ------------------------------------------------------------------

    private void handleStructures(ICommandSender sender, String[] args) {
        Long seed = requireSeed(sender);
        if (seed == null) {
            return;
        }
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            return;
        }
        if (Minecraft.getMinecraft().theWorld.provider.getDimensionId() != 0) {
            send(sender, "Villages, temples and monuments are overworld structures; run this in the overworld.");
            return;
        }
        int radius = parseRadius(sender, args, 3, 1, 12);
        if (radius < 0) {
            return;
        }

        int centerRegionX = Math.floorDiv((int) Math.floor(player.posX), StructureType.REGION_BLOCKS);
        int centerRegionZ = Math.floorDiv((int) Math.floor(player.posZ), StructureType.REGION_BLOCKS);
        List<Placement> found = new ArrayList<Placement>();
        for (int regionX = centerRegionX - radius; regionX <= centerRegionX + radius; regionX++) {
            for (int regionZ = centerRegionZ - radius; regionZ <= centerRegionZ + radius; regionZ++) {
                for (StructureType type : StructureType.values()) {
                    int[] chunk = type.placementChunk(regionX, regionZ, seed);
                    double dx = chunk[0] * 16.0D + 8.0D - player.posX;
                    double dz = chunk[1] * 16.0D + 8.0D - player.posZ;
                    found.add(new Placement(type, regionX, regionZ, chunk[0], chunk[1],
                            dx * dx + dz * dz));
                }
            }
        }
        Collections.sort(found, new Comparator<Placement>() {
            @Override
            public int compare(Placement a, Placement b) {
                return Double.compare(a.distanceSq, b.distanceSq);
            }
        });

        send(sender, "Structure placement candidates within " + radius
                + " regions (seed " + seed + "):");
        int shown = 0;
        for (Placement placement : found) {
            if (shown++ >= 8) {
                break;
            }
            send(sender, placement.type.displayName() + " region "
                    + placement.regionX + "," + placement.regionZ
                    + " -> chunk " + placement.chunkX + "," + placement.chunkZ
                    + " (block " + placement.chunkX * 16 + "," + placement.chunkZ * 16
                    + "), " + Math.round(Math.sqrt(placement.distanceSq)) + " blocks");
        }
        send(sender, "The RNG roll is exact; vanilla still checks the biome before building.");
    }

    private void handleSlime(ICommandSender sender, String[] args) {
        Long seed = requireSeed(sender);
        if (seed == null) {
            return;
        }
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            return;
        }
        if (Minecraft.getMinecraft().theWorld.provider.getDimensionId() != 0) {
            send(sender, "Slime chunks are an overworld thing; run this in the overworld.");
            return;
        }
        int radius = parseRadius(sender, args, 8, 1, 32);
        if (radius < 0) {
            return;
        }
        int centerChunkX = Math.floorDiv((int) Math.floor(player.posX), 16);
        int centerChunkZ = Math.floorDiv((int) Math.floor(player.posZ), 16);
        List<int[]> chunks = new ArrayList<int[]>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int chunkX = centerChunkX + dx;
                int chunkZ = centerChunkZ + dz;
                if (SeedTools.isSlimeChunk(seed, chunkX, chunkZ)) {
                    chunks.add(new int[] {chunkX, chunkZ, dx * dx + dz * dz});
                }
            }
        }
        Collections.sort(chunks, new Comparator<int[]>() {
            @Override
            public int compare(int[] a, int[] b) {
                return Integer.compare(a[2], b[2]);
            }
        });
        send(sender, "Slime chunks within " + radius + " chunks of "
                + centerChunkX + "," + centerChunkZ + ":");
        if (chunks.isEmpty()) {
            send(sender, "None. Try a larger radius.");
            return;
        }
        int shown = 0;
        for (int[] chunk : chunks) {
            if (shown++ >= 20) {
                send(sender, "... " + (chunks.size() - 20) + " more.");
                break;
            }
            send(sender, "chunk " + chunk[0] + "," + chunk[1]
                    + " (block " + chunk[0] * 16 + "," + chunk[1] * 16 + ")");
        }
        send(sender, "Spigot servers may change the seed-slime salt; default 987234911.");
    }

    private void handleFortress(ICommandSender sender, String[] args) {
        Long seed = requireSeed(sender);
        if (seed == null) {
            return;
        }
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            return;
        }
        int dimension = Minecraft.getMinecraft().theWorld.provider.getDimensionId();
        if (dimension == 1) {
            send(sender, "Run this in the overworld or the Nether; the End has no fortresses.");
            return;
        }
        int radius = parseRadius(sender, args, 32, 4, 128);
        if (radius < 0) {
            return;
        }
        double netherX = dimension == -1 ? player.posX : player.posX / 8.0D;
        double netherZ = dimension == -1 ? player.posZ : player.posZ / 8.0D;
        int centerChunkX = Math.floorDiv((int) Math.floor(netherX), 16);
        int centerChunkZ = Math.floorDiv((int) Math.floor(netherZ), 16);
        List<int[]> chunks = new ArrayList<int[]>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int chunkX = centerChunkX + dx;
                int chunkZ = centerChunkZ + dz;
                if (SeedTools.isNetherFortressChunk(seed, chunkX, chunkZ)) {
                    chunks.add(new int[] {chunkX, chunkZ, dx * dx + dz * dz});
                }
            }
        }
        Collections.sort(chunks, new Comparator<int[]>() {
            @Override
            public int compare(int[] a, int[] b) {
                return Integer.compare(a[2], b[2]);
            }
        });
        send(sender, "Nether fortress chunks near nether chunk "
                + centerChunkX + "," + centerChunkZ + ":");
        if (chunks.isEmpty()) {
            send(sender, "None. Try a larger radius.");
            return;
        }
        int shown = 0;
        for (int[] chunk : chunks) {
            if (shown++ >= 20) {
                send(sender, "... " + (chunks.size() - 20) + " more.");
                break;
            }
            send(sender, "chunk " + chunk[0] + "," + chunk[1]
                    + " (nether block " + chunk[0] * 16 + "," + chunk[1] * 16 + ")");
        }
        if (dimension != -1) {
            send(sender, "Using nether coordinates (overworld / 8). Enter a portal near one of these.");
        }
    }

    private void handleMineshaft(ICommandSender sender, String[] args) {
        Long seed = requireSeed(sender);
        if (seed == null) {
            return;
        }
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            return;
        }
        if (Minecraft.getMinecraft().theWorld.provider.getDimensionId() != 0) {
            send(sender, "Mineshafts are overworld structures; run this in the overworld.");
            return;
        }
        int radius = parseRadius(sender, args, 16, 2, 64);
        if (radius < 0) {
            return;
        }
        int centerChunkX = Math.floorDiv((int) Math.floor(player.posX), 16);
        int centerChunkZ = Math.floorDiv((int) Math.floor(player.posZ), 16);
        List<int[]> chunks = new ArrayList<int[]>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int chunkX = centerChunkX + dx;
                int chunkZ = centerChunkZ + dz;
                if (SeedTools.isMineshaftChunk(seed, chunkX, chunkZ)) {
                    chunks.add(new int[] {chunkX, chunkZ, dx * dx + dz * dz});
                }
            }
        }
        Collections.sort(chunks, new Comparator<int[]>() {
            @Override
            public int compare(int[] a, int[] b) {
                return Integer.compare(a[2], b[2]);
            }
        });
        send(sender, "Mineshaft chunks within " + radius + " chunks of "
                + centerChunkX + "," + centerChunkZ + ":");
        if (chunks.isEmpty()) {
            send(sender, "None. Try a larger radius.");
            return;
        }
        int shown = 0;
        for (int[] chunk : chunks) {
            if (shown++ >= 20) {
                send(sender, "... " + (chunks.size() - 20) + " more.");
                break;
            }
            send(sender, "chunk " + chunk[0] + "," + chunk[1]
                    + " (block " + chunk[0] * 16 + "," + chunk[1] * 16 + ")");
        }
        send(sender, "Presence roll only; the generated pieces can be small or collapsed.");
    }

    private Long requireSeed(ICommandSender sender) {
        Long seed = engine.config().seed();
        if (seed == null) {
            send(sender, "Set a seed first with /randar seed <seed>, or recover one from drops.");
        }
        return seed;
    }

    private int parseRadius(ICommandSender sender, String[] args, int fallback,
                            int minimum, int maximum) {
        if (args.length < 2) {
            return fallback;
        }
        try {
            int radius = Integer.parseInt(args[1]);
            if (radius < minimum || radius > maximum) {
                send(sender, "Radius must be between " + minimum + " and " + maximum + ".");
                return -1;
            }
            return radius;
        } catch (NumberFormatException e) {
            send(sender, "Expected a whole-number radius.");
            return -1;
        }
    }

    private static final class Placement {

        final StructureType type;
        final int regionX;
        final int regionZ;
        final int chunkX;
        final int chunkZ;
        final double distanceSq;

        Placement(StructureType type, int regionX, int regionZ,
                  int chunkX, int chunkZ, double distanceSq) {
            this.type = type;
            this.regionX = regionX;
            this.regionZ = regionZ;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.distanceSq = distanceSq;
        }
    }

    private static String seedText(RandarEngine engine) {
        Long seed = engine.config().seed();
        return seed == null ? "not set" : seed + " (" + engine.config().seedSourceLabel() + ")";
    }

    private static boolean parseToggle(ICommandSender sender, String[] args, boolean current) {
        if (args.length < 2) {
            return !current;
        }
        if ("on".equalsIgnoreCase(args[1]) || "true".equalsIgnoreCase(args[1])) {
            return true;
        }
        if ("off".equalsIgnoreCase(args[1]) || "false".equalsIgnoreCase(args[1])) {
            return false;
        }
        send(sender, "Expected on or off; toggling instead.");
        return !current;
    }

    private static String onOff(boolean value) {
        return value ? "on" : "off";
    }

    private static void send(ICommandSender sender, String message) {
        sender.addChatMessage(new ChatComponentText(
                EnumChatFormatting.GOLD + "[Randar] " + EnumChatFormatting.RESET + message));
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 1) {
            List<String> matches = new ArrayList<String>();
            for (String option : ROOT) {
                if (option.startsWith(args[0].toLowerCase())) {
                    matches.add(option);
                }
            }
            return matches;
        }
        if (args.length == 2 && ("hud".equalsIgnoreCase(args[0]) || "auto".equalsIgnoreCase(args[0]))) {
            return Arrays.asList("on", "off");
        }
        if (args.length == 2 && "seed".equalsIgnoreCase(args[0])) {
            return Collections.singletonList("clear");
        }
        if (args.length == 2 && "recover".equalsIgnoreCase(args[0])) {
            return Collections.singletonList("clear");
        }
        return null;
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return false;
    }

    @Override
    public int compareTo(ICommand other) {
        return getCommandName().compareTo(other.getCommandName());
    }
}
