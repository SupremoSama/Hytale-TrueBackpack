package com.supremosan.truebackpack.listener;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.InventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.supremosan.truebackpack.TrueBackpack;
import com.supremosan.truebackpack.registries.BackpackRegistry;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BackpackNestingListener extends RefSystem<EntityStore> {

    private static final Map<InventoryComponent, Ref<EntityStore>> COMPONENT_TO_REF = new ConcurrentHashMap<>();
    private static final Query<EntityStore> QUERY = Query.any();

    private static BackpackNestingListener INSTANCE;

    public BackpackNestingListener() {
        INSTANCE = this;
    }

    public static void register(@Nonnull TrueBackpack plugin) {
        INSTANCE = new BackpackNestingListener();
        plugin.getEntityStoreRegistry().registerSystem(INSTANCE);
        plugin.getEventRegistry().registerGlobal(InventoryChangeEvent.class, INSTANCE::handle);
    }

    @Override
    public @Nonnull Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public void onEntityAdded(@Nonnull Ref<EntityStore> ref,
                              @Nonnull AddReason reason,
                              @Nonnull Store<EntityStore> store,
                              @Nonnull CommandBuffer<EntityStore> buffer) {

        InventoryComponent.Backpack backpack =
                store.getComponent(ref, InventoryComponent.Backpack.getComponentType());

        if (backpack != null) {
            COMPONENT_TO_REF.put(backpack, ref);
        }
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<EntityStore> ref,
                               @Nonnull RemoveReason reason,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull CommandBuffer<EntityStore> buffer) {

        InventoryComponent.Backpack backpack =
                store.getComponent(ref, InventoryComponent.Backpack.getComponentType());

        if (backpack != null) {
            COMPONENT_TO_REF.remove(backpack);
        }
    }

    private void handle(@Nonnull InventoryChangeEvent event) {
        InventoryComponent component = event.getInventory();
        Ref<EntityStore> ref = COMPONENT_TO_REF.get(component);

        if (ref == null || !ref.isValid()) return;

        Store<EntityStore> store = ref.getStore();

        UUIDComponent uuid = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuid == null) return;

        InventoryComponent.Backpack backpackComp =
                store.getComponent(ref, InventoryComponent.Backpack.getComponentType());

        if (backpackComp == null) return;

        ItemContainer changed = event.getItemContainer();
        ItemContainer backpackContainer = backpackComp.getInventory();

        if (!backpackContainer.equals(changed)) return;

        for (short slot = 0; slot < changed.getCapacity(); slot++) {
            if (!event.getTransaction().wasSlotModified(slot)) continue;

            ItemStack item = changed.getItemStack(slot);
            if (item == null || item.isEmpty()) continue;

            String itemId = item.getItemId();

            if (BackpackRegistry.getByItem(itemId) == null) continue;

            changed.setItemStackForSlot(slot, ItemStack.EMPTY);
        }
    }
}