package com.supremosan.truebackpack.config;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class BackpackConfigPaths {

    private static final String MOD_DIR = "TrueBackpack";
    private static final String CONFIG_FILE = "backpacks.json";

    private BackpackConfigPaths() {
    }

    public static Path modRoot() {
        return Paths.get("mods", MOD_DIR);
    }

    public static Path configPath() {
        return modRoot().resolve(CONFIG_FILE);
    }
}