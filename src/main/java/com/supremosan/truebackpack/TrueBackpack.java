package com.supremosan.truebackpack;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

public class TrueBackpack extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public TrueBackpack(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        // 1. Armor listener handles equip/unequip/swap and per-instance content persistence.
        BackpackArmorListener.register(this);

        // 2. Tooltip listener handles custom tooltips for backpacks.
        BackpackTooltipListener.register();

        // 3. Clean up per-player caches on disconnect.
        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event ->
                BackpackTooltipListener.onPlayerLeave(event.getPlayerRef().getUuid()));

        LOGGER.atInfo().log("[TrueBackpack] Ready");
    }
}