package com.supremosan.truebackpack.helpers;

import com.supremosan.truebackpack.model.BlockyModelWrapper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class GeneratedModel {

    private static final Map<String, GeneratedModel> CACHE = new ConcurrentHashMap<>();

    private final String playerUuid;
    private final String slotKey;
    private final String modelPath;

    private GeneratedModel(String playerUuid,
                           String slotKey,
                           String modelPath) {
        this.playerUuid = playerUuid;
        this.slotKey = slotKey;
        this.modelPath = modelPath;
    }

    @Nullable
    public static GeneratedModel create(@Nonnull String playerUuid,
                                        @Nonnull String slotKey,
                                        @Nonnull String sourceModelPath) {

        String wrapped = BlockyModelWrapper.wrap(playerUuid, slotKey, sourceModelPath);
        if (wrapped == null) return null;

        GeneratedModel model = new GeneratedModel(playerUuid, slotKey, wrapped);
        CACHE.put(playerUuid + ":" + slotKey, model);
        return model;
    }

    public static void delete(@Nonnull String playerUuid,
                              @Nonnull String slotKey) {

        String key = playerUuid + ":" + slotKey;
        GeneratedModel model = CACHE.remove(key);
        if (model != null) {
            BlockyModelWrapper.delete(playerUuid, slotKey);
        }
    }

    @Nonnull
    public String getModelPath() {
        return modelPath;
    }
}