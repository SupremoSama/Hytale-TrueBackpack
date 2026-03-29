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
import com.supremosan.truebackpack.registries.BackpackRegistry.BackpackEntry;
import com.supremosan.truebackpack.registries.BackpackRegistry.HelipackConfig;

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

            String equippedFuelItemId = resolveEquippedFuelItemId(ref, store);
            handleModifiedSlots(event, changed, equippedFuelItemId);
            return;
        }
    }

    private String resolveEquippedFuelItemId(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        InventoryComponent.Armor armorComp = store.getComponent(ref, InventoryComponent.Armor.getComponentType());
        if (armorComp != null) {
            ItemStack chestStack = armorComp.getInventory().getItemStack((short) 1);
            if (chestStack != null && !chestStack.isEmpty()) {
                String fuelId = getFuelItemId(chestStack.getItemId());
                if (fuelId != null) return fuelId;
            }
        }

        InventoryComponent.Storage storageComp = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
        if (storageComp != null) {
            ItemStack storageStack = storageComp.getInventory().getItemStack((short) 0);
            if (storageStack != null && !storageStack.isEmpty()) {
                return getFuelItemId(storageStack.getItemId());
            }
        }

        return null;
    }

    private String getFuelItemId(@Nonnull String itemId) {
        BackpackEntry entry = BackpackRegistry.getByItem(itemId);
        if (entry == null || !entry.isHelipack()) return null;
        HelipackConfig config = entry.helipackConfig();
        if (config == null || !config.requiresFuel()) return null;
        return config.fuelItemId();
    }

    private void handleModifiedSlots(@Nonnull InventoryChangeEvent event,
                                     @Nonnull ItemContainer container,
                                     String requiredFuelItemId) {
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            if (!event.getTransaction().wasSlotModified(slot)) continue;

            ItemStack item = container.getItemStack(slot);
            if (item == null || item.isEmpty()) continue;

            String itemId = item.getItemId();

            if (BackpackRegistry.getByItem(itemId) != null) {
                container.setItemStackForSlot(slot, ItemStack.EMPTY);
                continue;
            }

            if (requiredFuelItemId != null && !requiredFuelItemId.equals(itemId)) {
                container.setItemStackForSlot(slot, ItemStack.EMPTY);
            }
        }
    }
}