package com.supremosan.truebackpack.config;

import com.supremosan.truebackpack.listener.BackpackArmorListener;

public final class BackpackConfig {

    public static void registerDefaults() {
        registerBackpacks();
    }

    private static void registerBackpacks() {
        BackpackArmorListener.registerBackpack(
                "Utility_Leather_Backpack",
                (short) 9,
                "Items/Backpacks/Small_Backpack.blockymodel",
                "Items/Back/BackpackBig_Texture.png");

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