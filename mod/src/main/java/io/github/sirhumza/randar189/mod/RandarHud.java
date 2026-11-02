package io.github.sirhumza.randar189.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.List;

/** Draws the seed and latest hit in the top-left corner. */
public final class RandarHud {

    private final RandarEngine engine;

    public RandarHud(RandarEngine engine) {
        this.engine = engine;
    }

    @SubscribeEvent
    public void onRenderText(RenderGameOverlayEvent.Text event) {
        if (!engine.config().showHud) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.gameSettings.showDebugInfo) {
            return;
        }

        Long seed = engine.config().seed();
        List<RandarHit> hits = engine.hits();

        event.left.add("");
        event.left.add(EnumChatFormatting.GOLD + "Randar 1.8.9");

        if (seed == null) {
            event.left.add(EnumChatFormatting.GRAY + "Seed: "
                    + EnumChatFormatting.RED + "unknown");
            event.left.add(EnumChatFormatting.GRAY + "  " + engine.recoveryStatus());
            if (engine.recoverySampleCount() == 0) {
                event.left.add(EnumChatFormatting.GRAY + "  break an ore, travel, break another");
            }
        } else {
            String label = RandarConfig.SOURCE_RECOVERED.equals(engine.config().seedSource)
                    ? "Seed (low 48): " : "Seed: ";
            event.left.add(EnumChatFormatting.GRAY + label
                    + EnumChatFormatting.WHITE + seed);
            if (RandarConfig.SOURCE_RECOVERED.equals(engine.config().seedSource)) {
                event.left.add(EnumChatFormatting.DARK_GRAY + "  recovered from drops");
            }
        }

        if (!hits.isEmpty()) {
            RandarHit hit = hits.get(0);
            event.left.add(EnumChatFormatting.YELLOW + hit.typeName()
                    + " " + hit.regionX + "," + hit.regionZ + "  lag " + hit.lag
                    + (hit.count > 1 ? " x" + hit.count : ""));
            event.left.add(EnumChatFormatting.GRAY + "  " + hit.placement());
            String attribution = hit.attributionShort();
            if (!attribution.isEmpty()) {
                event.left.add(EnumChatFormatting.GREEN + "  " + attribution);
            }
        }
    }
}
