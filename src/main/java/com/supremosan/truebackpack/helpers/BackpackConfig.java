package com.supremosan.truebackpack.helpers;

import com.supremosan.truebackpack.BackpackArmorListener;
import com.supremosan.truebackpack.BackpackCosmeticListener;

public final class BackpackConfig {

    public static void registerDefaults() {
        BackpackArmorListener.registerBackpack("Utility_Leather_Backpack", (short) 9);
        BackpackArmorListener.registerBackpack("Utility_Leather_Medium_Backpack", (short) 18);
        BackpackArmorListener.registerBackpack("Utility_Leather_Big_Backpack", (short) 27);
        BackpackArmorListener.registerBackpack("Utility_Leather_Extra_Big_Backpack", (short) 36);

        BackpackCosmeticListener.registerBackpackVisual(
                "Utility_Leather_Backpack",
                "Items/Back/Backpack_Medium.blockymodel",
                "Items/Back/Backpack_Medium_Texture.png");
        BackpackCosmeticListener.registerBackpackVisual(
                "Utility_Leather_Medium_Backpack",
                "Items/Back/BackpackBig.blockymodel",
                "Items/Back/BackpackBig_Texture.png");
        BackpackCosmeticListener.registerBackpackVisual(
                "Utility_Leather_Big_Backpack",
                "Items/Backpacks/Big_Backpack.blockymodel",
                "Items/Backpacks/Big_Backpack_Texture.png");
        BackpackCosmeticListener.registerBackpackVisual(
                "Utility_Leather_Extra_Big_Backpack",
                "Items/Backpacks/Extra_Big_Backpack.blockymodel",
                "Items/Backpacks/Extra_Big_Backpack_Texture.png");
    }
}
