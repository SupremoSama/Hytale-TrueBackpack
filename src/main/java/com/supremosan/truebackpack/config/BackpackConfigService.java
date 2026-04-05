package com.supremosan.truebackpack.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.supremosan.truebackpack.registries.BackpackRegistry;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BackpackConfigService {

    private static final Gson GSON = new Gson();
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();

    private BackpackConfigService() {
    }

    public static void reloadAndRegister(Logger logger) throws Exception {
        BackpackConfigAssets.ensureDefaultConfigCopied(logger);
        BackpackConfig cfg = loadConfig();
        BackpackRegistry.clear();
        int registered = registerAll(cfg, logger);
        logger.log(Level.INFO, "[TrueBackpack] Reload OK. Registered=" + registered);
    }

    public static boolean updateHelipackFuel(String itemId, String fuelItemId, int fuelConsumeAmount, float fuelConsumeInterval, Logger logger) throws Exception {
        BackpackConfig cfg = loadConfig();

        BackpackConfig.Entry target = cfg.backpacks.stream()
                .filter(e -> e != null && itemId.equalsIgnoreCase(e.itemId) && e.isHelipack())
                .findFirst()
                .orElse(null);

        if (target == null) return false;

        target.helipack.fuelItemId = fuelItemId;
        target.helipack.fuelConsumeAmount = fuelConsumeAmount;
        target.helipack.fuelConsumeInterval = fuelConsumeInterval;

        saveConfig(cfg);
        BackpackRegistry.clear();
        registerAll(cfg, logger);
        logger.log(Level.INFO, "[TrueBackpack] Updated helipack fuel for '" + itemId + "'");
        return true;
    }

    private static BackpackConfig loadConfig() throws Exception {
        Path p = BackpackConfigPaths.configPath();

        try (Reader r = Files.newBufferedReader(p)) {
            BackpackConfig cfg = GSON.fromJson(r, BackpackConfig.class);
            if (cfg == null) cfg = new BackpackConfig();
            if (cfg.backpacks == null) cfg.backpacks = List.of();
            return cfg;
        }
    }

    private static void saveConfig(BackpackConfig cfg) throws Exception {
        Path p = BackpackConfigPaths.configPath();

        try (Writer w = Files.newBufferedWriter(p, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            PRETTY.toJson(cfg, w);
        }
    }

    private static int registerAll(BackpackConfig cfg, Logger logger) {
        if (cfg == null || cfg.backpacks == null) return 0;

        int count = 0;

        for (BackpackConfig.Entry e : cfg.backpacks) {
            if (e == null || !e.isValid()) {
                logger.log(Level.WARNING, "[TrueBackpack] Skipping invalid entry: " + entryDescription(e));
                continue;
            }

            try {
                if (e.isHelipack()) {
                    BackpackConfig.HelipackEntry h = e.helipack;
                    BackpackRegistry.registerHelipack(
                            e.itemId,
                            e.blockId,
                            e.capacity,
                            e.model,
                            e.texture,
                            BackpackRegistry.HelipackConfig.of(
                                    h.fuelItemId,
                                    h.itemAnimationsId,
                                    h.verticalFlySpeed,
                                    h.horizontalFlySpeed,
                                    h.fuelConsumeInterval,
                                    h.fuelConsumeAmount
                            )
                    );
                } else {
                    BackpackRegistry.register(e.itemId, e.blockId, e.capacity, e.model, e.texture);
                }
                count++;
            } catch (Exception ex) {
                logger.log(Level.WARNING, "[TrueBackpack] Failed to register entry '" + e.itemId + "': " + ex.getMessage());
            }
        }

        return count;
    }

    private static String entryDescription(BackpackConfig.Entry e) {
        if (e == null) return "<null>";
        return "itemId=" + e.itemId + ", model=" + e.model + ", texture=" + e.texture;
    }
}