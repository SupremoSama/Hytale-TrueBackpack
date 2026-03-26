package com.supremosan.truebackpack.listener;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.inventory.InventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.supremosan.truebackpack.TrueBackpack;
import com.supremosan.truebackpack.registries.BackpackRegistry;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class BackpackNestingListener extends RefSystem<EntityStore> {

    private static final Set<Ref<EntityStore>> BACKPACK_ENTITIES = ConcurrentHashMap.newKeySet();

    public static void register(@Nonnull TrueBackpack plugin) {
        BackpackNestingListener listener = new BackpackNestingListener();
        plugin.getEntityStoreRegistry().registerSystem(listener);
        plugin.getEventRegistry().registerGlobal(InventoryChangeEvent.class, listener::handle);
    }

    @Override
    public @Nonnull Query<EntityStore> getQuery() {
        return InventoryComponent.Backpack.getComponentType();
    }

    @Override
    public void onEntityAdded(@Nonnull Ref<EntityStore> ref,
                              @Nonnull AddReason reason,
                              @Nonnull Store<EntityStore> store,
                              @Nonnull CommandBuffer<EntityStore> buffer) {

        BACKPACK_ENTITIES.add(ref);
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<EntityStore> ref,
                               @Nonnull RemoveReason reason,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull CommandBuffer<EntityStore> buffer) {

        BACKPACK_ENTITIES.remove(ref);
    }

    private void handle(@Nonnull InventoryChangeEvent event) {
        InventoryComponent component = event.getInventory();

        for (Ref<EntityStore> ref : BACKPACK_ENTITIES) {
            if (!ref.isValid()) continue;

            Store<EntityStore> store = ref.getStore();

            InventoryComponent.Backpack backpackComp =
                    store.getComponent(ref, InventoryComponent.Backpack.getComponentType());

            if (backpackComp == null) continue;

            if (component != backpackComp) continue;

            ItemContainer changed = event.getItemContainer();
            ItemContainer backpackContainer = backpackComp.getInventory();

            if (changed != backpackContainer) return;

            handleModifiedSlots(event, changed);
            return;
        }
    }

    private void handleModifiedSlots(@Nonnull InventoryChangeEvent event,
                                     @Nonnull ItemContainer container) {

        for (short slot = 0; slot < container.getCapacity(); slot++) {
            if (!event.getTransaction().wasSlotModified(slot)) continue;

            ItemStack item = container.getItemStack(slot);
            if (item == null || item.isEmpty()) continue;

            String itemId = item.getItemId();

            if (BackpackRegistry.getByItem(itemId) == null) continue;

            container.setItemStackForSlot(slot, ItemStack.EMPTY);
        }
    }
}