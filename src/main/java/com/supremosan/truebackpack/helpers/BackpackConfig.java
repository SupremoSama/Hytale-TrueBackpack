package com.supremosan.truebackpack.helpers;

import com.supremosan.truebackpack.BackpackArmorListener;
import com.supremosan.truebackpack.ToolListener;
import com.supremosan.truebackpack.ToolListener.ToolSlot;

public final class BackpackConfig {

    public static void registerDefaults() {
        registerTools();
        registerBackpacks();
    }

    private static void registerTools() {
        ToolListener.registerPrefix("Tool_Pickaxe",     ToolSlot.HIP_LEFT);
        ToolListener.registerPrefix("Tool_Hatchet",     ToolSlot.HIP_RIGHT);
        ToolListener.registerPrefix("Weapon_Sword",     ToolSlot.BACK);
        ToolListener.registerPrefix("Weapon_Longsword", ToolSlot.BACK);
        ToolListener.registerPrefix("Weapon_Axe",       ToolSlot.BACK);
        ToolListener.registerPrefix("Weapon_Battleaxe", ToolSlot.BACK);
        ToolListener.registerPrefix("Weapon_Shortbow", ToolSlot.BACK);
        ToolListener.registerPrefix("Weapon_Longbow",  ToolSlot.BACK);
    }

    private static void registerBackpacks() {
        BackpackArmorListener.registerBackpack(
                "Utility_Leather_Backpack",
                (short) 9,
                "Items/Back/Backpack_Medium.blockymodel",
                "Items/Back/Backpack_Medium_Texture.png");

        BackpackArmorListener.registerBackpack(
                "Utility_Leather_Medium_Backpack",
                (short) 18,
                "Items/Back/BackpackBig.blockymodel",
                "Items/Back/BackpackBig_Texture.png");

        BackpackArmorListener.registerBackpack(
                "Utility_Leather_Big_Backpack",
                (short) 27,
                "Items/Backpacks/Big_Backpack.blockymodel",
                "Items/Backpacks/Big_Backpack_Texture.png");

        BackpackArmorListener.registerBackpack(
                "Utility_Leather_Extra_Big_Backpack",
                (short) 36,
                "Items/Backpacks/Extra_Big_Backpack.blockymodel",
                "Items/Backpacks/Extra_Big_Backpack_Texture.png");
    }
}