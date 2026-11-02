package io.github.sirhumza.randar189.mod;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.List;

/** Small configuration and history screen, opened with the keybind or {@code /randar gui}. */
public final class RandarGui extends GuiScreen {

    private final RandarEngine engine;
    private GuiTextField seedField;
    private GuiButton hudButton;
    private GuiButton autoButton;
    private String message = "";

    public RandarGui(RandarEngine engine) {
        this.engine = engine;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int left = width / 2 - 170;

        seedField = new GuiTextField(0, fontRendererObj, left + 82, 38, 180, 20);
        seedField.setMaxStringLength(20);
        seedField.setText(engine.config().worldSeed == null ? "" : engine.config().worldSeed);
        seedField.setFocused(true);

        buttonList.add(new GuiButton(1, left + 268, 38, 72, 20, "Set seed"));
        hudButton = new GuiButton(2, left, 84, 160, 20, hudLabel());
        buttonList.add(hudButton);
        autoButton = new GuiButton(3, left + 180, 84, 160, 20, autoLabel());
        buttonList.add(autoButton);
        buttonList.add(new GuiButton(4, left, height - 36, 100, 20, "Clear hits"));
        buttonList.add(new GuiButton(6, left + 110, height - 36, 120, 20, "Reset recovery"));
        buttonList.add(new GuiButton(5, left + 340 - 100, height - 36, 100, 20, "Close"));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Randar 1.8.9", width / 2, 14, 0xFFFFFF);

        int left = width / 2 - 170;
        drawString(fontRendererObj, "World seed", left, 44, 0xA0A0A0);
        seedField.drawTextBox();

        String source = "Source: " + engine.config().seedSourceLabel();
        if (engine.config().seed() == null) {
            source = "Source: unknown - recover from drops or /randar seed <seed>";
        }
        drawString(fontRendererObj, source, left, 64, 0x808080);

        String recovery = "Recovery: " + engine.recoveryStatus()
                + "   samples " + engine.recoverySampleCount()
                + "   window " + engine.config().recoveryLag;
        drawString(fontRendererObj, recovery, left, 114, 0x80FF80);

        if (!message.isEmpty()) {
            drawCenteredString(fontRendererObj, message, width / 2, 128, 0xFFFF55);
        }

        List<RandarHit> hits = engine.hits();
        int y = 146;
        if (hits.isEmpty()) {
            drawCenteredString(fontRendererObj,
                    "No hits yet - break one ore block and keep 4+ drops.",
                    width / 2, y, 0xA0A0A0);
        } else {
            drawString(fontRendererObj, "Recent hits", left, y, 0xFFFFFF);
            y += 14;
            for (int i = 0; i < hits.size() && y < height - 58; i++) {
                RandarHit hit = hits.get(i);
                drawString(fontRendererObj,
                        hit.typeName() + "  region " + hit.regionX + "," + hit.regionZ
                                + "  lag " + hit.lag
                                + (hit.count > 1 ? "  x" + hit.count : ""),
                        left, y, 0xFFFF55);
                y += 11;
                drawString(fontRendererObj,
                        "  " + hit.placement()
                                + "   blocks " + hit.regionMinBlockX() + ".." + hit.regionMaxBlockX()
                                + ", " + hit.regionMinBlockZ() + ".." + hit.regionMaxBlockZ(),
                        left, y, 0xA0A0A0);
                y += 11;
                drawString(fontRendererObj,
                        "  " + fontRendererObj.trimStringToWidth(hit.attribution(), width - 2 * left - 4),
                        left, y, 0x80FF80);
                y += 13;
            }
        }

        drawString(fontRendererObj, engine.status(), left, height - 52, 0x808080);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (seedField.isFocused() && keyCode != Keyboard.KEY_ESCAPE) {
            if (keyCode == Keyboard.KEY_RETURN) {
                applySeed();
                return;
            }
            seedField.textboxKeyTyped(typedChar, keyCode);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        seedField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case 1:
                applySeed();
                break;
            case 2:
                engine.config().showHud = !engine.config().showHud;
                engine.config().save();
                hudButton.displayString = hudLabel();
                break;
            case 3:
                engine.config().autoSolve = !engine.config().autoSolve;
                engine.config().save();
                autoButton.displayString = autoLabel();
                break;
            case 4:
                engine.clearHits();
                message = "Hit history cleared.";
                break;
            case 6:
                engine.clearRecovery();
                message = "Recovery samples cleared.";
                break;
            case 5:
                mc.displayGuiScreen(null);
                break;
            default:
                break;
        }
    }

    private void applySeed() {
        String text = seedField.getText().trim();
        Long seed = RandarConfig.parseSeed(text);
        if (seed == null) {
            message = "Seed must be a whole number (or 0x hex).";
            return;
        }
        engine.setSeed(seed);
        message = "Seed set to " + seed + ".";
    }

    private String hudLabel() {
        return "HUD: " + (engine.config().showHud ? "on" : "off");
    }

    private String autoLabel() {
        return "Auto-capture: " + (engine.config().autoSolve ? "on" : "off");
    }
}
