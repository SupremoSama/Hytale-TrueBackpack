package com.supremosan.truebackpack.data;

import com.hypixel.hytale.server.core.inventory.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BackpackDataStorage {

    private static final Map<String, List<ItemStack>> LIVE_CONTENTS = new ConcurrentHashMap<>();

    private BackpackDataStorage() {
    }

    public static void clearActiveItem(@Nonnull String playerUuid) {
        LIVE_CONTENTS.remove(playerUuid);
    }

    public static void setLiveContents(@Nonnull String playerUuid, @Nonnull List<ItemStack> contents) {
        LIVE_CONTENTS.put(playerUuid, contents);
    }

    @Nullable
    public static List<ItemStack> getLiveContents(@Nonnull String playerUuid) {
        return LIVE_CONTENTS.get(playerUuid);
    }
}