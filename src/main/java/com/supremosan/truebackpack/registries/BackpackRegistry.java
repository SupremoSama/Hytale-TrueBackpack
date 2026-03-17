package com.supremosan.truebackpack.registries;

import com.supremosan.truebackpack.listener.BackpackArmorListener;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BackpackRegistry {

    public record BackpackEntry(
            String itemId,
            String blockId,
            short capacity,
            String model,
            String texture
    ) {
    }

    private static final Map<String, BackpackEntry> BY_ITEM = new HashMap<>();
    private static final Map<String, BackpackEntry> BY_BLOCK = new HashMap<>();
    private static final List<BackpackEntry> ALL = new ArrayList<>();

    private BackpackRegistry() {
    }

    public static void register(String itemId, String blockId, short capacity, String model, String texture) {
        BackpackEntry entry = new BackpackEntry(itemId, blockId, capacity, model, texture);
        BY_ITEM.put(itemId, entry);
        BY_BLOCK.put(blockId, entry);
        ALL.add(entry);
        BackpackArmorListener.registerBackpack(itemId, capacity, model, texture);
    }

    @Nullable
    public static BackpackEntry getByItem(String itemId) {
        return BY_ITEM.get(itemId);
    }

    @Nullable
    public static BackpackEntry getByBlock(String blockId) {
        return BY_BLOCK.get(blockId);
    }

    public static List<BackpackEntry> getAll() {
        return ALL;
    }

    public static void registerDefaults() {
        register("Utility_Leather_Backpack", "TrueBackpack_Chest_Small", (short) 9,
                "Items/Backpacks/Small_Backpack.blockymodel", "Items/Back/BackpackBig_Texture.png");
        register("Utility_Leather_Medium_Backpack", "TrueBackpack_Chest_Medium", (short) 18,
                "Items/Back/BackpackBig.blockymodel", "Items/Back/BackpackBig_Texture.png");
        register("Utility_Leather_Big_Backpack", "TrueBackpack_Chest_Large", (short) 27,
                "Items/Backpacks/Big_Backpack.blockymodel", "Items/Backpacks/Big_Backpack_Texture.png");
        register("Utility_Leather_Extra_Big_Backpack", "TrueBackpack_Chest_ExtraLarge", (short) 36,
                "Items/Backpacks/Extra_Big_Backpack.blockymodel", "Items/Backpacks/Extra_Big_Backpack_Texture.png");
    }
}