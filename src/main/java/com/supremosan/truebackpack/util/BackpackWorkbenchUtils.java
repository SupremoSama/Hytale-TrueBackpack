package com.supremosan.truebackpack.util;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import javax.annotation.Nullable;

/** Identifies the two backpack crafting benches. */
public final class BackpackWorkbenchUtils {
    private BackpackWorkbenchUtils() {}

    public static boolean isBackpackWorkbench(@Nullable BlockType blockType) {
        if (blockType == null || blockType.getBench() == null) return false;
        String id = blockType.getBench().getId();
        return "Backpack_Workbench_Apprentice".equals(id)
                || "Backpack_Workbench_Master".equals(id);
    }
}
