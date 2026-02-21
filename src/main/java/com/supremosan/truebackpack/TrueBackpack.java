package com.supremosan.truebackpack;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.player.ClientReady;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.Universe;

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

        // 3. Cosmetic listener handles visual 3D model attachment on storage slot 0.
        BackpackCosmeticListener.register(this);

        // 4. Clean up per-player caches on disconnect.
        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            java.util.UUID uuid = event.getPlayerRef().getUuid();
            BackpackTooltipListener.onPlayerLeave(uuid);
            BackpackCosmeticListener.onPlayerLeave(uuid.toString());
        });

        // 5. Setup listener event for player change the visibility.
        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class,
                _ -> {
                    this.getEntityStoreRegistry()
                            .registerSystem(new BackpackCosmeticListener.OnPlayerSettingsChange());
                });

        LOGGER.atInfo().log("[TrueBackpack] Ready");
    }
}