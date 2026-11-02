package io.github.sirhumza.randar189.mod;

import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import java.io.File;

/**
 * Randar 1.8.9 client mod.
 *
 * <p>1.8.9 quantises item positions to 1/32 of a block, so a drop leaks four
 * bits per axis.  Four or more drops give enough equations to recover
 * {@code World.rand}, and the last structure generation before the break
 * identifies the 512x512 structure region.</p>
 */
@Mod(modid = Randar189Mod.MODID,
        name = Randar189Mod.NAME,
        version = Randar189Mod.VERSION,
        clientSideOnly = true,
        acceptedMinecraftVersions = "[1.8.9]")
public final class Randar189Mod {

    public static final String MODID = "randar189";
    public static final String NAME = "Randar 1.8.9";
    public static final String VERSION = "1.0.0";

    private RandarConfig config;
    private RandarEngine engine;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        File configDir = event.getModConfigurationDirectory();
        config = RandarConfig.load(new File(configDir, "randar-189.json"));
        engine = new RandarEngine(config, configDir);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Entity events live on the Forge bus; FML game events (tick, input)
        // live on the FML bus in 1.8.9.
        MinecraftForge.EVENT_BUS.register(engine);
        FMLCommonHandler.instance().bus().register(engine);
        MinecraftForge.EVENT_BUS.register(new RandarHud(engine));
        FMLCommonHandler.instance().bus().register(new RandarKeyHandler(engine));

        ClientRegistry.registerKeyBinding(RandarKeyHandler.OPEN);
        ClientCommandHandler.instance.registerCommand(new RandarCommand(engine));
    }
}
