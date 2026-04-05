package com.supremosan.truebackpack.config;

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

public final class BackpackConfigAssets {

    private static final String RESOURCE_FILE = "backpacks.json";

    private BackpackConfigAssets() {
    }

    public static void ensureDefaultConfigCopied(Logger logger) throws Exception {
        Path target = BackpackConfigPaths.configPath();

        if (Files.exists(target)) {
            logger.log(Level.INFO, "[TrueBackpack] Config already exists: " + target.toAbsolutePath());
            return;
        }

        Files.createDirectories(target.getParent());
        copyDefaultConfig(target, logger);
        logger.log(Level.INFO, "[TrueBackpack] Default config copied to: " + target.toAbsolutePath());
    }

    private static void copyDefaultConfig(Path target, Logger logger) throws Exception {
        URI uri = BackpackConfigAssets.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        Path codeSource = Paths.get(uri);

        if (Files.isDirectory(codeSource)) {
            Path found = findInDevTree(codeSource);
            if (found != null) {
                Files.copy(found, target, StandardCopyOption.REPLACE_EXISTING);
                logger.log(Level.INFO, "[TrueBackpack] Copied from dev resources: " + found.toAbsolutePath());
                return;
            }
            throw new IllegalStateException(
                    "Cannot find backpacks.json in dev resource directories (searched relative to: " + codeSource.toAbsolutePath() + ")");
        }

        try (JarFile jar = new JarFile(codeSource.toFile())) {
            JarEntry entry = jar.getJarEntry(RESOURCE_FILE);
            if (entry == null) {
                throw new IllegalStateException("Cannot find " + RESOURCE_FILE + " inside jar.");
            }
            try (InputStream in = jar.getInputStream(entry)) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            logger.log(Level.INFO, "[TrueBackpack] Copied from jar: " + RESOURCE_FILE);
        }
    }

    private static Path findInDevTree(Path codeSource) {
        // codeSource = build/classes/java/main
        // resources are at build/resources/main/
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