package com.supremosan.truebackpack.config.hat;

import com.google.gson.Gson;
import com.hypixel.hytale.protocol.ColorLight;
import com.supremosan.truebackpack.registries.HatRegistry;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class HatConfigService {

    private static final Gson GSON = new Gson();

    private HatConfigService() {}

    public static void reloadAndRegister(Logger logger) throws Exception {
        HatConfigAssets.ensureDefaultConfigCopied(logger);
        HatConfig cfg = loadConfig();
        HatRegistry.clear();
        int registered = registerAll(cfg, logger);
        logger.log(Level.INFO, "[TrueBackpack] Hat reload OK. Registered=" + registered);
    }

    private static HatConfig loadConfig() throws Exception {
        Path p = HatConfigPaths.configPath();

        try (Reader r = Files.newBufferedReader(p)) {
            HatConfig cfg = GSON.fromJson(r, HatConfig.class);
            if (cfg == null) cfg = new HatConfig();
            if (cfg.hats == null) cfg.hats = new ArrayList<>();
            return cfg;
        }
    }

    private static int registerAll(HatConfig cfg, Logger logger) {
        if (cfg == null || cfg.hats == null) return 0;

        int count = 0;

        for (HatConfig.Entry e : cfg.hats) {
            if (e == null || !e.isValid()) {
                logger.log(Level.WARNING, "[TrueBackpack] Skipping invalid hat entry: " + entryDescription(e));
                continue;
            }

            try {
                ColorLight light = resolveLight(e, logger);
                HatRegistry.register(new HatRegistry.HatEntry(
                        e.itemId,
                        e.maxDurability,
                        e.drainIntervalTicks,
                        e.model,
                        e.texture,
                        light
                ));
                count++;
            } catch (Exception ex) {
                logger.log(Level.WARNING, "[TrueBackpack] Failed to register hat '" + e.itemId + "': " + ex.getMessage());
            }
        }

        return count;
    }

    private static ColorLight resolveLight(HatConfig.Entry e, Logger logger) {
        if (e.dynamicLight == null) return null;

        if (!e.dynamicLight.isValid()) {
            logger.log(Level.WARNING, "[TrueBackpack] Hat '" + e.itemId + "' has an invalid dynamicLight block (radius must be > 0); ignoring.");
            return null;
        }

        return new ColorLight(e.dynamicLight.radius, e.dynamicLight.red, e.dynamicLight.green, e.dynamicLight.blue);
    }

    private static String entryDescription(HatConfig.Entry e) {
        if (e == null) return "<null>";
        return "itemId=" + e.itemId + ", model=" + e.model + ", texture=" + e.texture;
    }
}