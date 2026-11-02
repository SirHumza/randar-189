package io.github.sirhumza.randar189.mod;

import io.github.sirhumza.randar189.core.OreType;
import io.github.sirhumza.randar189.core.StructureType;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;

/** Maps the item entity a player sees back to the block that produced it. */
final class OreItems {

    private OreItems() {
    }

    static OreType classify(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        Item item = stack.getItem();
        if (item == Items.diamond) {
            return OreType.DIAMOND;
        }
        if (item == Items.coal && stack.getMetadata() == 0) {
            return OreType.COAL;
        }
        if (item == Items.emerald) {
            return OreType.EMERALD;
        }
        if (item == Items.redstone) {
            return OreType.REDSTONE;
        }
        if (item == Items.dye && stack.getMetadata() == EnumDyeColor.BLUE.getDyeDamage()) {
            return OreType.LAPIS;
        }
        if (item == Items.clay_ball) {
            return OreType.CLAY;
        }
        if (item == Items.melon) {
            return OreType.MELON;
        }
        if (item == Items.glowstone_dust) {
            return OreType.GLOWSTONE;
        }
        if (item == Items.prismarine_crystals) {
            return OreType.SEA_LANTERN;
        }
        return null;
    }

    /** A human-readable name for the HUD/GUI. */
    static String structureName(String enumName) {
        try {
            return StructureType.valueOf(enumName).displayName();
        } catch (RuntimeException e) {
            return enumName;
        }
    }
}
