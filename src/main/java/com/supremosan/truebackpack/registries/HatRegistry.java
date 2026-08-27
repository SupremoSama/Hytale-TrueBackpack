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
    private static final Map<String, HatEntry> LOOKUP_CACHE = new ConcurrentHashMap<>();

    private HatRegistry() {
    }

    public static void register(@Nonnull HatEntry entry) {
        REGISTRY.put(entry.baseItemId(), entry);
        LOOKUP_CACHE.clear();
    }

    @Nullable
    public static HatEntry getByItem(@Nullable String itemId) {
        if (itemId == null || itemId.isEmpty()) return null;

        String normalized = itemId.toLowerCase();
        HatEntry cached = LOOKUP_CACHE.get(normalized);
        if (cached != null) return cached;

        HatEntry direct = REGISTRY.get(itemId);
        if (direct != null) {
            LOOKUP_CACHE.put(normalized, direct);
            return direct;
        }

        for (Map.Entry<String, HatEntry> e : REGISTRY.entrySet()) {
            String key = e.getKey().toLowerCase();
            if (key.equals(normalized) || key.endsWith(":" + normalized) || normalized.endsWith(":" + key)) {
                LOOKUP_CACHE.put(normalized, e.getValue());
                return e.getValue();
            }
        }

        return null;
    }

    public static boolean isHat(@Nullable String itemId) {
        return getByItem(itemId) != null;
    }

    public static void clear() {
        REGISTRY.clear();
        LOOKUP_CACHE.clear();
    }
}