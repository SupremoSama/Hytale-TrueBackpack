package com.supremosan.truebackpack.registries;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public class BackpackBlockRegistry {

    public record BackpackBlockEntry(String itemId, String blockId, short capacity) {
    }

    private static final Map<String, BackpackBlockEntry> ITEM_REGISTRY = new HashMap<>();
    private static final Map<String, BackpackBlockEntry> BLOCK_REGISTRY = new HashMap<>();

    private BackpackBlockRegistry() {
    }

    public static void register(@Nullable String itemId, @Nullable String blockId, short capacity) {
        if (itemId == null || blockId == null) return;

        BackpackBlockEntry entry = new BackpackBlockEntry(itemId, blockId, capacity);
        ITEM_REGISTRY.put(itemId, entry);
        BLOCK_REGISTRY.put(blockId, entry);
    }

    @Nullable
    public static BackpackBlockEntry getByItem(String itemId) {
        return ITEM_REGISTRY.get(itemId);
    }

    @Nullable
    public static BackpackBlockEntry getByBlock(String blockId) {
        return BLOCK_REGISTRY.get(blockId);
    }

    public static void registerDefaults() {
        register("Utility_Leather_Backpack", "TrueBackpack_Chest_Small", (short) 9);
        register("Utility_Leather_Medium_Backpack", "TrueBackpack_Chest_Medium", (short) 18);
        register("Utility_Leather_Big_Backpack", "TrueBackpack_Chest_Large", (short) 27);
        register("Utility_Leather_Extra_Big_Backpack", "TrueBackpack_Chest_ExtraLarge", (short) 36);
    }
}