package com.supremosan.truebackpack.registries;

import com.hypixel.hytale.protocol.ColorLight;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class HatRegistry {

    public record HatEntry(
            @Nonnull String baseItemId,
            short maxDurability,
            int drainIntervalTicks,
            @Nonnull String modelPath,
            @Nonnull String texturePath,
            @Nullable ColorLight dynamicLight
    ) {
        public HatEntry(
                @Nonnull String baseItemId,
                short maxDurability,
                int drainIntervalTicks,
                @Nonnull String modelPath,
                @Nonnull String texturePath
        ) {
            this(baseItemId, maxDurability, drainIntervalTicks, modelPath, texturePath, null);
        }
    }

    private static final Map<String, HatEntry> REGISTRY = new LinkedHashMap<>();
    private static final Map<String, HatEntry> BY_ITEM_CACHE = new ConcurrentHashMap<>();

    private HatRegistry() {}

    public static void register(@Nonnull HatEntry entry) {
        REGISTRY.put(entry.baseItemId(), entry);
        BY_ITEM_CACHE.clear();
    }

    @Nullable
    public static HatEntry getByItem(@Nonnull String itemId) {
        String normalized = itemId.toLowerCase();

        HatEntry cached = BY_ITEM_CACHE.get(normalized);
        if (cached != null) return cached;

        for (Map.Entry<String, HatEntry> e : REGISTRY.entrySet()) {
            String key = e.getKey().toLowerCase();
            if (key.equals(normalized) || key.endsWith(":" + normalized)) {
                BY_ITEM_CACHE.put(normalized, e.getValue());
                return e.getValue();
            }
        }
        return null;
    }

    public static boolean isHat(@Nonnull String itemId) {
        return getByItem(itemId) != null;
    }

    public static void clear() {
        REGISTRY.clear();
        BY_ITEM_CACHE.clear();
    }
}