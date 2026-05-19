package com.supremosan.truebackpack.config.hat;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class HatConfigAssets {

    private static final String RESOURCE_FILE = "hats.json";

    private HatConfigAssets() {}

    public static void ensureDefaultConfigCopied(Logger logger) throws Exception {
        Path target = HatConfigPaths.configPath();

        if (Files.exists(target)) {
            logger.log(Level.INFO, "[TrueBackpack] Hat config already exists: " + target.toAbsolutePath());
            return;
        }

        Files.createDirectories(target.getParent());
        copyDefaultConfig(target, logger);
        logger.log(Level.INFO, "[TrueBackpack] Default hat config copied to: " + target.toAbsolutePath());
    }

    private static void copyDefaultConfig(Path target, Logger logger) throws Exception {
        URI uri = HatConfigAssets.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        Path codeSource = Paths.get(uri);

        if (Files.isDirectory(codeSource)) {
            Path found = findInDevTree(codeSource);
            if (found != null) {
                Files.copy(found, target, StandardCopyOption.REPLACE_EXISTING);
                logger.log(Level.INFO, "[TrueBackpack] Copied hat config from dev resources: " + found.toAbsolutePath());
                return;
            }
            throw new IllegalStateException(
                    "Cannot find hats.json in dev resource directories (searched relative to: " + codeSource.toAbsolutePath() + ")");
        }

        try (JarFile jar = new JarFile(codeSource.toFile())) {
            JarEntry entry = jar.getJarEntry(RESOURCE_FILE);
            if (entry == null) {
                throw new IllegalStateException("Cannot find " + RESOURCE_FILE + " inside jar.");
            }
            try (InputStream in = jar.getInputStream(entry)) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            logger.log(Level.INFO, "[TrueBackpack] Copied hat config from jar: " + RESOURCE_FILE);
        }
    }

    private static Path findInDevTree(Path codeSource) {
        Path buildDir = codeSource.getParent().getParent().getParent();

        Path[] candidates = {
                buildDir.resolve("resources").resolve("main").resolve(RESOURCE_FILE),
                codeSource.resolveSibling("resources").resolve("main").resolve(RESOURCE_FILE),
                codeSource.getParent().resolveSibling("resources").resolve("main").resolve(RESOURCE_FILE),
        };

        for (Path candidate : candidates) {
            if (Files.exists(candidate)) return candidate;
        }

        return null;
    }
}