package com.supremosan.truebackpack.config.backpack;

import com.supremosan.truebackpack.config.ConfigHelper;
import com.supremosan.truebackpack.registries.BackpackRegistry;

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BackpackConfigService {

    private static final String CONFIG_FILE = "backpacks.json";

    private BackpackConfigService() {
    }

    public static void reloadAndRegister(Logger logger) throws Exception {
        BackpackConfig cfg = loadConfig(logger);
        BackpackRegistry.clear();
        int registered = registerAll(cfg, logger);
        logger.log(Level.INFO, "[TrueBackpack] Reload OK. Registered=" + registered);
    }

    public static boolean updateHelipackFuel(String itemId, String fuelItemId, int fuelConsumeAmount, float fuelConsumeInterval, Logger logger) throws Exception {
        BackpackConfig cfg = loadConfig(logger);

        BackpackConfig.Entry target = cfg.backpacks.stream()
                .filter(e -> e != null && itemId.equalsIgnoreCase(e.itemId) && e.isHelipack())
                .findFirst()
                .orElse(null);

        if (target == null) return false;

        target.helipack.fuelItemId = fuelItemId;
        target.helipack.fuelConsumeAmount = fuelConsumeAmount;
        target.helipack.fuelConsumeInterval = fuelConsumeInterval;

        ConfigHelper.save(CONFIG_FILE, cfg);
        BackpackRegistry.clear();
        registerAll(cfg, logger);
        logger.log(Level.INFO, "[TrueBackpack] Updated helipack fuel for '" + itemId + "'");
        return true;
    }

    private static BackpackConfig loadConfig(Logger logger) throws Exception {
        BackpackConfig cfg = ConfigHelper.loadOrCreate(CONFIG_FILE, BackpackConfig.class, logger);
        if (cfg.backpacks == null) {
            cfg.backpacks = new ArrayList<>();
        }
        return cfg;
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