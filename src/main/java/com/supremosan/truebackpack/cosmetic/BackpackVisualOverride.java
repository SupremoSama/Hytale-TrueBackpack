package com.supremosan.truebackpack.cosmetic;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BackpackVisualOverride {

    public record Override(String model, String texture) {
    }

    private static final Map<UUID, Override> OVERRIDES = new ConcurrentHashMap<>();

    private BackpackVisualOverride() {
    }

    public static void set(UUID playerUuid, String model, String texture) {
        OVERRIDES.put(playerUuid, new Override(model, texture));
    }

    public static void remove(UUID playerUuid) {
        OVERRIDES.remove(playerUuid);
    }

    @Nullable
    public static Override get(UUID playerUuid) {
        return OVERRIDES.get(playerUuid);
    }

    public static boolean has(UUID playerUuid) {
        return OVERRIDES.containsKey(playerUuid);
    }
}