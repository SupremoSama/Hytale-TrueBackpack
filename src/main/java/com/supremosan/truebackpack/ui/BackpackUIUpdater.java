package com.supremosan.truebackpack.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.inventory.UpdatePlayerInventory;
import com.hypixel.hytale.protocol.packets.player.SetGameMode;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

public class BackpackUIUpdater {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static void updateBackpackUI(@Nonnull LivingEntity entity, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (!(entity instanceof Player player)) {
            return;
        }

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        InventoryComponent.Storage storage = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
        InventoryComponent.Armor armor = store.getComponent(ref, InventoryComponent.Armor.getComponentType());
        InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        InventoryComponent.Utility utility = store.getComponent(ref, InventoryComponent.Utility.getComponentType());
        InventoryComponent.Tool tool = store.getComponent(ref, InventoryComponent.Tool.getComponentType());
        InventoryComponent.Backpack backpack = store.getComponent(ref, InventoryComponent.Backpack.getComponentType());

        UUID uuid = playerRef.getUuid();
        try {
            playerRef.getPacketHandler().writeNoCache(new UpdatePlayerInventory(
                storage != null ? storage.getInventory().toPacket() : null,
                armor != null ? armor.getInventory().toPacket() : null,
                hotbar != null ? hotbar.getInventory().toPacket() : null,
                utility != null ? utility.getInventory().toPacket() : null,
                tool != null ? tool.getInventory().toPacket() : null,
                backpack != null ? backpack.getInventory().toPacket() : null
            ));
            playerRef.getPacketHandler().writeNoCache(new SetGameMode(player.getGameMode()));
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to update backpack UI for player %s: %s", uuid, e.getMessage());
        }
    }
}
