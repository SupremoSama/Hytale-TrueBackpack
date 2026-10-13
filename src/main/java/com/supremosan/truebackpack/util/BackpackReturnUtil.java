package com.supremosan.truebackpack.util;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/** Hands an item back to the player (storage, then hotbar), dropping it at their feet as a last resort. */
public final class BackpackReturnUtil {

    private BackpackReturnUtil() {
    }

    public static void giveBack(@Nonnull Ref<EntityStore> ref,
                                @Nonnull ItemStack item,
                                @Nonnull ComponentAccessor<EntityStore> accessor) {
        if (ItemStack.isEmpty(item)) return;

        ItemStack remaining = tryAdd(accessor.getComponent(ref, InventoryComponent.Storage.getComponentType()), item);
        if (remaining != null) {
            remaining = tryAdd(accessor.getComponent(ref, InventoryComponent.Hotbar.getComponentType()), remaining);
        }
        if (remaining != null) {
            ItemUtils.dropItem(ref, remaining, accessor);
        }
    }

    /** Returns what could not be added, or null when everything fit. */
    private static ItemStack tryAdd(InventoryComponent component, @Nonnull ItemStack item) {
        if (component == null) return item;
        ItemContainer container = component.getInventory();
        var transaction = container.addItemStack(item);
        ItemStack remainder = transaction.getRemainder();
        return ItemStack.isEmpty(remainder) ? null : remainder;
    }
}
