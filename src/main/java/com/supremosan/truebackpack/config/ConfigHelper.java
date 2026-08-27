package com.supremosan.truebackpack.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ConfigHelper {

    private static final String MOD_DIR = "TrueBackpack";
    public static final Gson GSON = new Gson();
    public static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();

    private ConfigHelper() {
    }

    public static Path getModDirectory() {
        return Paths.get("mods", MOD_DIR);
    }

    public static Path getConfigPath(String fileName) {
        return getModDirectory().resolve(fileName);
    }

    public static <T> T loadOrCreate(String fileName, Class<T> clazz, Logger logger) throws Exception {
        Path target = getConfigPath(fileName);

        if (!Files.exists(target)) {
            Files.createDirectories(target.getParent());
            try (InputStream in = ConfigHelper.class.getClassLoader().getResourceAsStream(fileName)) {
                if (in == null) {
                    throw new FileNotFoundException("Cannot find default resource '" + fileName + "' on classpath.");
                }
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                logger.log(Level.INFO, "[TrueBackpack] Default config created at: " + target.toAbsolutePath());
            }
        }

        try (Reader r = Files.newBufferedReader(target)) {
            T config = GSON.fromJson(r, clazz);
            if (config == null) {
                config = clazz.getDeclaredConstructor().newInstance();
            }
            return config;
        }
    }

    public static <T> void save(String fileName, T config) throws Exception {
        Path target = getConfigPath(fileName);
        Files.createDirectories(target.getParent());
        try (Writer w = Files.newBufferedWriter(target, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            PRETTY_GSON.toJson(config, w);
        }
    }
}
