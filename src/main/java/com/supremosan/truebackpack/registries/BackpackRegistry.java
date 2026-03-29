package com.supremosan.truebackpack.registries;

import com.supremosan.truebackpack.listener.BackpackArmorListener;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BackpackRegistry {

    public record HelipackConfig(String fuelItemId, String itemAnimationsId, float verticalFlySpeed,
                                 float horizontalFlySpeed, float fuelConsumeInterval, int fuelConsumeAmount) {
        public static HelipackConfig of(String fuelItemId, String itemAnimationsId, float verticalFlySpeed, float horizontalFlySpeed, float fuelConsumeInterval, int fuelConsumeAmount) {
            return new HelipackConfig(fuelItemId, itemAnimationsId, verticalFlySpeed, horizontalFlySpeed, fuelConsumeInterval, fuelConsumeAmount);
        }

        public boolean requiresFuel() {
            return fuelItemId != null && !fuelItemId.isEmpty();
        }
    }

    public record BackpackEntry(String itemId, String blockId, short capacity, String model, String texture,
                                @Nullable HelipackConfig helipackConfig) {
        public boolean isHelipack() {
            return helipackConfig != null;
        }
    }

    private static final Map<String, BackpackEntry> BY_ITEM = new HashMap<>();
    private static final Map<String, BackpackEntry> BY_BLOCK = new HashMap<>();
    private static final List<BackpackEntry> ALL = new ArrayList<>();

    private BackpackRegistry() {
    }

    public static void register(String itemId, String blockId, short capacity, String model, String texture) {
        register(itemId, blockId, capacity, model, texture, null);
    }

    public static void registerHelipack(String itemId, String blockId, short capacity, String model, String texture, HelipackConfig helipackConfig) {
        register(itemId, blockId, capacity, model, texture, helipackConfig);
    }

    private static void register(String itemId, String blockId, short capacity, String model, String texture, @Nullable HelipackConfig helipackConfig) {
        BackpackEntry entry = new BackpackEntry(itemId, blockId, capacity, model, texture, helipackConfig);
        BY_ITEM.put(itemId, entry);
        if (blockId != null && !blockId.isEmpty()) BY_BLOCK.put(blockId, entry);
        ALL.add(entry);
        BackpackArmorListener.registerBackpack(itemId, capacity, model, texture);
    }

    @Nullable
    public static BackpackEntry getByItem(String itemId) {
        BackpackEntry exact = BY_ITEM.get(itemId);
        if (exact != null) return exact;
        for (Map.Entry<String, BackpackEntry> e : BY_ITEM.entrySet()) {
            if (itemId.contains(e.getKey())) return e.getValue();
        }
        return null;
    }

    @Nullable
    public static BackpackEntry getByBlock(String blockId) {
        return BY_BLOCK.get(blockId);
    }

    public static void registerDefaults() {
        registerHelipack("Utility_Heli_Backpack", "", (short) 3, "Items/Backpacks/Helipack.blockymodel", "Items/Back/Helipack_Texture.png", HelipackConfig.of("Ingredient_Charcoal", "HelipackAnimations", 6.0f, 7.0f, 10.0f, 5));
        register("Utility_Leather_Side_Backpack", "TrueBackpack_Chest_Side", (short) 6, "Items/Backpacks/Side_Backpack.blockymodel", "Items/Backpacks/Side_Backpack_Texture.png");
        register("Utility_Leather_Backpack", "TrueBackpack_Chest_Small", (short) 9, "Items/Backpacks/Small_Backpack.blockymodel", "Items/Back/BackpackBig_Texture.png");
        register("Utility_Leather_Medium_Backpack", "TrueBackpack_Chest_Medium", (short) 18, "Items/Back/BackpackBig.blockymodel", "Items/Back/BackpackBig_Texture.png");
        register("Utility_Leather_Big_Backpack", "TrueBackpack_Chest_Large", (short) 27, "Items/Backpacks/Big_Backpack.blockymodel", "Items/Backpacks/Big_Backpack_Texture.png");
        register("Utility_Leather_Extra_Big_Backpack", "TrueBackpack_Chest_ExtraLarge", (short) 36, "Items/Backpacks/Extra_Big_Backpack.blockymodel", "Items/Backpacks/Extra_Big_Backpack_Texture.png");
    }
}