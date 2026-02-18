package com.supremosan.truebackpack.helpers;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BackpackDataStorage {
    private static final Map<String, com.hypixel.hytale.server.core.inventory.ItemStack>
            ACTIVE_ITEM = new ConcurrentHashMap<>();

    private BackpackDataStorage() {}

    public static void setActiveItem(@Nonnull String playerUuid,
                                     @Nonnull com.hypixel.hytale.server.core.inventory.ItemStack item) {
        ACTIVE_ITEM.put(playerUuid, item);
    }

    @Nullable
    public static com.hypixel.hytale.server.core.inventory.ItemStack getActiveItem(
            @Nonnull String playerUuid) {
        return ACTIVE_ITEM.get(playerUuid);
    }

    public static void clearActiveItem(@Nonnull String playerUuid) {
        ACTIVE_ITEM.remove(playerUuid);
    }
}