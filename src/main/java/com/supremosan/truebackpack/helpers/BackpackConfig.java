package com.supremosan.truebackpack.helpers;

import com.supremosan.truebackpack.BackpackArmorListener;

public final class BackpackConfig {

    public static void registerDefaults() {
        BackpackArmorListener.registerBackpack("Utility_Leather_Backpack", (short) 9);
        BackpackArmorListener.registerBackpack("Utility_Leather_Medium_Backpack", (short) 18);
        BackpackArmorListener.registerBackpack("Utility_Leather_Big_Backpack", (short) 27);
        BackpackArmorListener.registerBackpack("Utility_Leather_Extra_Big_Backpack", (short) 36);
    }

    private BackpackConfig() {}
}
