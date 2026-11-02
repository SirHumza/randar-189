package io.github.sirhumza.randar189.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

/** Keybind handler.  Default key: R. */
public final class RandarKeyHandler {

    public static final KeyBinding OPEN = new KeyBinding(
            "Open Randar", Keyboard.KEY_R, "Randar 1.8.9");

    private final RandarEngine engine;

    public RandarKeyHandler(RandarEngine engine) {
        this.engine = engine;
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (!OPEN.isPressed()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen == null) {
            mc.displayGuiScreen(new RandarGui(engine));
        }
    }
}
