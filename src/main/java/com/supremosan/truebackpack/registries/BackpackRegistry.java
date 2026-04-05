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

    public static void clear() {
        BY_ITEM.clear();
        BY_BLOCK.clear();
        ALL.clear();
        BackpackArmorListener.clear();
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
}