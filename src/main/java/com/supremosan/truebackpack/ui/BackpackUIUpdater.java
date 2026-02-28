package com.supremosan.truebackpack.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.player.SetGameMode;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class BackpackUIUpdater {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static void updateBackpackUI(@Nonnull LivingEntity entity,
                                        @Nonnull Ref<EntityStore> ref,
                                        @Nonnull Store<EntityStore> store) {
        if (!(entity instanceof Player player)) {
            return;
        }

        try {
            PlayerRef playerRefComponent = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRefComponent == null) {
                return;
            }

            player.sendInventory();

            playerRefComponent.getPacketHandler().writeNoCache(
                    new SetGameMode(player.getGameMode())
            );
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to update backpack UI for player %s: %s",
                    player.getDisplayName(),
                    e.getMessage());
        }
    }
}