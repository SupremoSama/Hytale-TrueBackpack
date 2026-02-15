package com.supremosan.truebackpack;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;

public class TrueBackpack extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static TrueBackpack instance;

    @Nonnull
    public static TrueBackpack get() {
        return instance;
    }

    public TrueBackpack(JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        BackpackArmorListener.register(this);

        LOGGER.atInfo().log("TrueBackpack ready");
    }
}