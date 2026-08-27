package com.supremosan.truebackpack.listener;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.InventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.supremosan.truebackpack.TrueBackpack;
import com.supremosan.truebackpack.registries.BackpackRegistry;
import com.supremosan.truebackpack.registries.BackpackRegistry.BackpackEntry;
import com.supremosan.truebackpack.registries.BackpackRegistry.HelipackConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class BackpackNestingListener extends EntityEventSystem<EntityStore, InventoryChangeEvent> {

    private static volatile Query<EntityStore> QUERY;

    public BackpackNestingListener() {
        super(InventoryChangeEvent.class);
    }

    public static void register(@Nonnull TrueBackpack plugin) {
        plugin.getEntityStoreRegistry().registerSystem(new BackpackNestingListener());
    }

    @Override
    @Nullable
    public Query<EntityStore> getQuery() {
        if (QUERY == null) {
            QUERY = InventoryComponent.Backpack.getComponentType();
        }
        return QUERY;
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull InventoryChangeEvent event) {

        if (event.getComponentType() != InventoryComponent.Backpack.getComponentType()) {
            return;
        }

        InventoryComponent.Backpack backpackComp = archetypeChunk.getComponent(index, InventoryComponent.Backpack.getComponentType());
        if (backpackComp == null) return;

        ItemContainer backpackContainer = backpackComp.getInventory();
        if (event.getItemContainer() != backpackContainer) return;

        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        String equippedFuelItemId = resolveEquippedFuelItemId(ref, store);

        for (short slot = 0; slot < backpackContainer.getCapacity(); slot++) {
            if (!event.getTransaction().wasSlotModified(slot)) continue;

            ItemStack item = backpackContainer.getItemStack(slot);
            if (item == null || item.isEmpty()) continue;

            String itemId = item.getItemId();

            if (BackpackRegistry.isBackpack(itemId)) {
                backpackContainer.setItemStackForSlot(slot, ItemStack.EMPTY);
                continue;
            }

            if (equippedFuelItemId != null && !equippedFuelItemId.equals(itemId)) {
                backpackContainer.setItemStackForSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    @Nullable
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

    @Nullable
    private String getFuelItemId(@Nonnull String itemId) {
        BackpackEntry entry = BackpackRegistry.getByItem(itemId);
        if (entry == null || !entry.isHelipack()) return null;
        HelipackConfig config = entry.helipackConfig();
        if (config == null || !config.requiresFuel()) return null;
        return config.fuelItemId();
    }
}