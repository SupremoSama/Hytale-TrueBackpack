package com.supremosan.truebackpack;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MoveTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.SlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.Transaction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.supremosan.truebackpack.helpers.BackpackConfig;
import com.supremosan.truebackpack.helpers.BackpackDataStorage;
import com.supremosan.truebackpack.helpers.BackpackItemFactory;
import com.supremosan.truebackpack.helpers.BackpackUIUpdater;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class BackpackArmorListener {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final ThreadLocal<Boolean> PROCESSING = ThreadLocal.withInitial(() -> false);

    private static final Map<String, Short> BACKPACK_SIZES = new ConcurrentHashMap<>();
    private static final short CHEST_SLOT = 1;

    public static void register(@Nonnull TrueBackpack plugin) {
        BackpackConfig.registerDefaults();

        plugin.getEventRegistry().registerGlobal(
                LivingEntityInventoryChangeEvent.class,
                BackpackArmorListener::handle
        );

        LOGGER.atInfo().log("[TrueBackpack] BackpackArmorListener registered");
    }

    public static void registerBackpack(@Nonnull String baseItemId, short sizeBonus) {
        BACKPACK_SIZES.put(baseItemId, sizeBonus);
    }

    public static short getBackpackSize(@Nonnull String itemId) {
        Short exact = BACKPACK_SIZES.get(itemId);
        if (exact != null) return exact;

        for (Map.Entry<String, Short> e : BACKPACK_SIZES.entrySet()) {
            if (itemId.contains(e.getKey())) return e.getValue();
        }

        return 0;
    }


    private static void handle(LivingEntityInventoryChangeEvent event) {
        if (PROCESSING.get()) {
            return;
        }

        LivingEntity entity = event.getEntity();
        if (entity == null) return;

        Ref<EntityStore> ref = entity.getReference();
        if (ref == null || !ref.isValid()) return;

        Store<EntityStore> store = ref.getStore();
        UUIDComponent uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComponent == null) return;

        String playerUuid = uuidComponent.getUuid().toString();

        ItemContainer changed = event.getItemContainer();
        if (changed == null) return;

        Inventory inv = entity.getInventory();
        ItemContainer armor = inv.getArmor();

        // Only care about armor container changes
        if (changed != armor) return;

        Transaction tx = event.getTransaction();
        if (!tx.wasSlotModified(CHEST_SLOT)) return;

        // Live chest state AFTER the transaction
        ItemStack chestNow = armor.getItemStack(CHEST_SLOT);
        boolean isEquipping = chestNow != null && !chestNow.isEmpty();

        // Extract before/after ItemStacks from the transaction
        ItemStack oldItem = null;
        ItemStack newItem = null;

        switch (tx) {
            case SlotTransaction slotTx -> {
                if (slotTx.getSlot() == CHEST_SLOT) {
                    oldItem = slotTx.getSlotBefore();
                    newItem = slotTx.getSlotAfter();
                }
            }

            case MoveTransaction<?> moveTx -> {
                SlotTransaction removeTx = moveTx.getRemoveTransaction();
                Object addTx = moveTx.getAddTransaction();

                if (!isEquipping) {
                    // Unequip: chest was the source of the move
                    if (removeTx.getSlot() == CHEST_SLOT) {
                        oldItem = removeTx.getSlotBefore();
                        newItem = removeTx.getSlotAfter();
                    }
                } else {
                    // Equip or swap: chest was the destination
                    if (addTx instanceof ItemStackTransaction itemStackTx) {
                        for (ItemStackSlotTransaction s : itemStackTx.getSlotTransactions()) {
                            if (s.getSlot() == CHEST_SLOT) {
                                oldItem = s.getSlotBefore();
                                newItem = s.getSlotAfter();
                                break;
                            }
                        }
                    } else if (addTx instanceof SlotTransaction slotTx
                            && slotTx.getSlot() == CHEST_SLOT) {
                        oldItem = slotTx.getSlotBefore();
                        newItem = slotTx.getSlotAfter();
                    }
                }
            }

            case ItemStackTransaction itemStackTx -> {
                for (ItemStackSlotTransaction s : itemStackTx.getSlotTransactions()) {
                    if (s.getSlot() == CHEST_SLOT) {
                        oldItem = s.getSlotBefore();
                        newItem = s.getSlotAfter();
                        break;
                    }
                }
            }

            default -> LOGGER.atWarning().log("[TrueBackpack] Unhandled transaction type: %s",
                    tx.getClass().getName());
        }

        // Determine slot bonuses from item IDs (unchanged – size is still on the base ID)
        String oldItemId = itemId(oldItem);
        String newItemId = itemId(newItem);

        // Fallback: if the transaction gave us nothing, read the live chest state
        if (oldItemId == null && newItemId == null && isEquipping) {
            newItem   = chestNow;
            newItemId = itemId(chestNow);
        }

        short oldBonus = oldItemId != null ? getBackpackSize(oldItemId) : 0;
        short newBonus = newItemId != null ? getBackpackSize(newItemId) : 0;

        // Skip if neither side is a backpack
        if (oldBonus == 0 && newBonus == 0) {
            return;
        }

        // Skip spurious events where nothing actually changed
        if (oldBonus == newBonus && Objects.equals(oldItemId, newItemId)) {
            return;
        }

        PROCESSING.set(true);
        try {
            performBackpackResize(entity, ref, store, inv, playerUuid,
                    oldItem, newItem, oldBonus, newBonus);
        } finally {
            PROCESSING.set(false);
        }
    }


    private static void performBackpackResize(@Nonnull LivingEntity entity,
                                              @Nonnull Ref<EntityStore> ref,
                                              @Nonnull Store<EntityStore> store,
                                              @Nonnull Inventory inv,
                                              @Nonnull String playerUuid,
                                              @Nullable ItemStack oldItem,
                                              @Nullable ItemStack newItem,
                                              short oldBonus,
                                              short newBonus) {
        // Step 1: Stamp a fresh instance ID if this backpack has never been equipped.
        if (newBonus > 0 && newItem != null && !BackpackItemFactory.hasInstanceId(newItem)) {
            newItem = BackpackItemFactory.createBackpackInstance(newItem);
            inv.getArmor().setItemStackForSlot(CHEST_SLOT, newItem);
        }

        // Step 2: Snapshot live contents of the OLD backpack and write them into
        if (oldBonus > 0 && oldItem != null) {
            ItemContainer currentBp = inv.getBackpack();
            List<ItemStack> liveContents = getAllBackpackContents(currentBp);
            boolean hasItems = liveContents.stream().anyMatch(i -> i != null && !i.isEmpty());

            ItemStack savedOld = hasItems
                    ? BackpackItemFactory.saveContents(oldItem, liveContents)
                    : BackpackItemFactory.clearContents(oldItem);

            Inventory inv2 = entity.getInventory();
            ItemContainer mainInv = inv2.getCombinedBackpackStorageHotbar();
            for (short slot = 0; slot < mainInv.getCapacity(); slot++) {
                ItemStack candidate = mainInv.getItemStack(slot);
                if (candidate != null && candidate.equals(oldItem)) {
                    mainInv.setItemStackForSlot(slot, savedOld);
                    break;
                }
            }
        }

        // Step 3: Resize.
        inv.resizeBackpack(newBonus, new ObjectArrayList<>());

        // Step 4: Slot filters — no backpacks inside backpacks.
        if (newBonus > 0) {
            ItemContainer bp = inv.getBackpack();
            for (short slot = 0; slot < bp.getCapacity(); slot++) {
                bp.setSlotFilter(FilterActionType.ADD, slot,
                        (_, _, _, item) -> item == null || item.isEmpty()
                                || getBackpackSize(item.getItemId()) == 0);
            }
        }

        // Step 5: Restore contents from the new backpack item's own metadata.
        if (newBonus > 0 && newItem != null) {
            if (BackpackItemFactory.hasContents(newItem)) {
                List<ItemStack> stored = BackpackItemFactory.loadContents(newItem);
                restoreContentsToBackpack(inv.getBackpack(), stored);

                // Clear contents from metadata now that they're live in the container.
                ItemStack cleaned = BackpackItemFactory.clearContents(newItem);
                inv.getArmor().setItemStackForSlot(CHEST_SLOT, cleaned);
            }
            BackpackDataStorage.setActiveItem(playerUuid, newItem);
        } else {
            BackpackDataStorage.clearActiveItem(playerUuid);
        }

        // Step 6: Refresh UI.
        BackpackUIUpdater.updateBackpackUI(entity, ref, store);
    }

    @Nullable
    private static String itemId(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return stack.getItemId();
    }

    @Nonnull
    private static List<ItemStack> getAllBackpackContents(@Nonnull ItemContainer backpack) {
        List<ItemStack> contents = new ArrayList<>();
        for (short i = 0; i < backpack.getCapacity(); i++) {
            contents.add(backpack.getItemStack(i));
        }
        return contents;
    }

    private static void restoreContentsToBackpack(@Nonnull ItemContainer backpack,
                                                  @Nonnull List<ItemStack> contents) {
        short capacity = backpack.getCapacity();
        for (int i = 0; i < contents.size() && i < capacity; i++) {
            ItemStack item = contents.get(i);
            if (item != null && !item.isEmpty()) {
                backpack.setItemStackForSlot((short) i, item);
            }
        }
    }
}