package com.supremosan.truebackpack.config.settings;

import com.supremosan.truebackpack.config.ConfigHelper;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Server-wide toggles stored in mods/TrueBackpack/settings.json. */
public final class TrueBackpackSettings {

    private static final String FILE = "settings.json";
    private static final Logger LOGGER = Logger.getLogger("TrueBackpack");

    /** When true, backpacks stay with the player on death instead of dropping. */
    public boolean keepBackpacksOnDeath = false;

    private static volatile TrueBackpackSettings current = new TrueBackpackSettings();

    public static TrueBackpackSettings get() {
        return current;
    }

    public static void reload() {
        Path path = ConfigHelper.getConfigPath(FILE);
        if (!Files.exists(path)) {
            current = new TrueBackpackSettings();
            save();
            return;
        }
        try (Reader r = Files.newBufferedReader(path)) {
            TrueBackpackSettings loaded = ConfigHelper.GSON.fromJson(r, TrueBackpackSettings.class);
            current = loaded != null ? loaded : new TrueBackpackSettings();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[TrueBackpack] Failed to load " + FILE + ", using defaults", e);
            current = new TrueBackpackSettings();
        }
    }

    public static void setKeepBackpacksOnDeath(boolean value) {
        current.keepBackpacksOnDeath = value;
        save();
    }

    private static void save() {
        try {
            ConfigHelper.save(FILE, current);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[TrueBackpack] Failed to save " + FILE, e);
        }
    }
}
