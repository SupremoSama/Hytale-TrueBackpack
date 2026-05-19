package com.supremosan.truebackpack.config.hat;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class HatConfigPaths {

    private static final String MOD_DIR = "TrueBackpack";
    private static final String CONFIG_FILE = "hats.json";

    private HatConfigPaths() {}

    public static Path modRoot() {
        return Paths.get("mods", MOD_DIR);
    }

    public static Path configPath() {
        return modRoot().resolve(CONFIG_FILE);
    }
}