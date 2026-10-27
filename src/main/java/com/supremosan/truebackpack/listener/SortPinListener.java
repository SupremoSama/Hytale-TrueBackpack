package com.supremosan.truebackpack.listener;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.InventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MoveTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.Transaction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.supremosan.truebackpack.TrueBackpack;
import com.supremosan.truebackpack.registries.BackpackRegistry;
import com.supremosan.truebackpack.registries.HatRegistry;
import com.supremosan.truebackpack.util.SortPinUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class SortPinListener extends EntityEventSystem<EntityStore, InventoryChangeEvent> {

    private static final int BACKPACK_SLOT = 0;
    private static final int HAT_SLOT = 1;

    private static volatile Query<EntityStore> QUERY;

    public SortPinListener() {
        super(InventoryChangeEvent.class);
    }

    public static void register(@Nonnull TrueBackpack plugin) {
        plugin.getEntityStoreRegistry().registerSystem(new SortPinListener());
    }

    @Override
    @Nullable
    public Query<EntityStore> getQuery() {
        if (QUERY == null) {
            QUERY = InventoryComponent.Storage.getComponentType();
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

        if (event.getComponentType() != InventoryComponent.Storage.getComponentType()) return;
        ItemContainer container = event.getItemContainer();
        if (isSort(event.getTransaction())) {
            restorePinnedSlots(container);
        } else {
            trackPinSlots(container, event.getTransaction());
        }
    }

    private static void trackPinSlots(@Nonnull ItemContainer container, @Nonnull Transaction transaction) {
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            if (!transaction.wasSlotModified(slot)) continue;

            ItemStack item = container.getItemStack(slot);
            if (item == null || item.isEmpty()) continue;

            boolean shouldPin = slot == pinSlot(item);
            if (shouldPin != SortPinUtil.isPinned(item)) {
                container.setItemStackForSlot(slot, SortPinUtil.setPinned(item, shouldPin), false);
            }
        }
    }

    private static boolean isSort(@Nonnull Transaction transaction) {
        if (!(transaction instanceof ListTransaction<?> list) || !list.succeeded() || list.size() < 2) return false;
        for (Transaction t : list.getList()) {
            if (t instanceof MoveTransaction<?>) return false;
        }
        return true;
    }

    private static int pinSlot(@Nonnull ItemStack item) {
        if (BackpackRegistry.isBackpack(item.getItemId())) return BACKPACK_SLOT;
        if (HatRegistry.isHat(item.getItemId())) return HAT_SLOT;
        return -1;
    }

    private static void restorePinnedSlots(@Nonnull ItemContainer container) {
        short capacity = container.getCapacity();
        ItemStack[] ordered = new ItemStack[capacity];
        List<ItemStack> rest = new ArrayList<>(capacity);
        boolean anyPinned = false;
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack item = container.getItemStack(slot);
            int target = SortPinUtil.isPinned(item) ? pinSlot(item) : -1;
            if (target >= 0 && target < capacity && ordered[target] == null) {
                ordered[target] = item;
                anyPinned = true;
            } else {
                rest.add(item);
            }
        }
        if (!anyPinned) return;

        int next = 0;
        for (short slot = 0; slot < capacity; slot++) {
            if (ordered[slot] == null) ordered[slot] = rest.get(next++);
        }

        for (short slot = 0; slot < capacity; slot++) {
            ItemStack wanted = ordered[slot];
            if (wanted != container.getItemStack(slot)) {
                container.setItemStackForSlot(slot, wanted != null ? wanted : ItemStack.EMPTY, false);
            }
        }
    }
}
