package com.supremosan.truebackpack.registries;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BackpackRegistry {

    public record HelipackConfig(
            String fuelItemId,
            String itemAnimationsId,
            float verticalFlySpeed,
            float horizontalFlySpeed,
            float fuelConsumeInterval,
            int fuelConsumeAmount
    ) {
        public static HelipackConfig of(
                String fuelItemId,
                String itemAnimationsId,
                float verticalFlySpeed,
                float horizontalFlySpeed,
                float fuelConsumeInterval,
                int fuelConsumeAmount
        ) {
            String resolvedFuel = (fuelItemId == null || fuelItemId.isBlank()) ? "Ingredient_Charcoal" : fuelItemId;
            return new HelipackConfig(resolvedFuel, itemAnimationsId, verticalFlySpeed, horizontalFlySpeed, fuelConsumeInterval, fuelConsumeAmount);
        }

        public boolean requiresFuel() {
            return fuelItemId != null && !fuelItemId.isEmpty() && !"none".equalsIgnoreCase(fuelItemId);
        }
    }

    public record BackpackEntry(
            String itemId,
            String blockId,
            short capacity,
            String model,
            String texture,
            @Nullable HelipackConfig helipackConfig
    ) {
        public boolean isHelipack() {
            return helipackConfig != null;
        }
    }

    private static final Map<String, BackpackEntry> REGISTRY = new LinkedHashMap<>();
    private static final Map<String, BackpackEntry> BY_BLOCK = new LinkedHashMap<>();
    private static final Map<String, BackpackEntry> LOOKUP_CACHE = new ConcurrentHashMap<>();

    private BackpackRegistry() {
    }

    public static void clear() {
        REGISTRY.clear();
        BY_BLOCK.clear();
        LOOKUP_CACHE.clear();
    }

    public static void register(String itemId, String blockId, short capacity, String model, String texture) {
        register(itemId, blockId, capacity, model, texture, null);
    }

    public static void registerHelipack(String itemId, String blockId, short capacity, String model, String texture, HelipackConfig helipackConfig) {
        register(itemId, blockId, capacity, model, texture, helipackConfig);
    }

    private static void register(String itemId, String blockId, short capacity, String model, String texture, @Nullable HelipackConfig helipackConfig) {
        BackpackEntry entry = new BackpackEntry(itemId, blockId, capacity, model, texture, helipackConfig);
        REGISTRY.put(itemId, entry);
        if (blockId != null && !blockId.isEmpty()) {
            BY_BLOCK.put(blockId, entry);
        }
        LOOKUP_CACHE.clear();
    }

    @Nullable
    public static BackpackEntry getByItem(@Nullable String itemId) {
        if (itemId == null || itemId.isEmpty()) return null;

        String normalized = itemId.toLowerCase();
        BackpackEntry cached = LOOKUP_CACHE.get(normalized);
        if (cached != null) return cached;

        BackpackEntry direct = REGISTRY.get(itemId);
        if (direct != null) {
            LOOKUP_CACHE.put(normalized, direct);
            return direct;
        }

        for (Map.Entry<String, BackpackEntry> e : REGISTRY.entrySet()) {
            String key = e.getKey().toLowerCase();
            if (key.equals(normalized) || key.endsWith(":" + normalized) || normalized.endsWith(":" + key)) {
                LOOKUP_CACHE.put(normalized, e.getValue());
                return e.getValue();
            }
        }

        return null;
    }

    @Nullable
    public static BackpackEntry getByBlock(@Nullable String blockId) {
        if (blockId == null || blockId.isEmpty()) return null;
        return BY_BLOCK.get(blockId);
    }

    public static boolean isBackpack(@Nullable String itemId) {
        return getByItem(itemId) != null;
    }

    public static short getCapacity(@Nullable String itemId) {
        BackpackEntry entry = getByItem(itemId);
        return entry != null ? entry.capacity() : 0;
    }
}