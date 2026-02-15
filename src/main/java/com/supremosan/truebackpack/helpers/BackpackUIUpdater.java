package com.supremosan.truebackpack.helpers;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.ComponentUpdate;
import com.hypixel.hytale.protocol.ComponentUpdateType;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.modules.entityui.UIComponentList;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Utility class for updating player backpack UI components.
 * This ensures the backpack button and related UI elements properly reflect
 * the current backpack state when capacity changes.
 */
public class BackpackUIUpdater {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Update the player's UI to reflect the current backpack state.
     * Call this after any operation that changes backpack capacity or state.
     *
     * @param entity The living entity (must be a Player)
     * @param ref The entity reference
     * @param store The entity store
     */
    public static void updateBackpackUI(@Nonnull LivingEntity entity,
                                        @Nonnull Ref<EntityStore> ref,
                                        @Nonnull Store<EntityStore> store) {
        if (!(entity instanceof Player player)) {
            return;
        }

        try {
            // Get the PlayerRef component from the store (not the deprecated field)
            PlayerRef playerRefComponent = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRefComponent == null) {
                return;
            }

            // Method 1: Send updated inventory packet to refresh the client's UI
            player.sendInventory();

            // Method 2: Update EntityUIComponents if needed for entity overhead displays
            UIComponentList uiComponentList = store.getComponent(ref, UIComponentList.getComponentType());
            if (uiComponentList != null) {
                uiComponentList.update();

                // Queue UI component update to all viewers
                ComponentUpdate update = new ComponentUpdate();
                update.type = ComponentUpdateType.UIComponents;
                update.entityUIComponents = uiComponentList.getComponentIds();

                // Get the Visible component to access viewers
                EntityTrackerSystems.Visible visible = store.getComponent(ref,
                        EntityTrackerSystems.Visible.getComponentType());

                if (visible != null) {
                    // Queue the update to all viewers (including the player themselves)
                    for (EntityTrackerSystems.EntityViewer viewer : visible.visibleTo.values()) {
                        viewer.queueUpdate(ref, update);
                    }
                }
            }

            // Method 3: Force HUD refresh using the correct PlayerRef component
            player.getHudManager().resetHud(playerRefComponent);

        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to update backpack UI: %s", e.getMessage());
        }
    }
}