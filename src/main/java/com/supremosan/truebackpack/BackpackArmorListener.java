package com.supremosan.truebackpack;

import com.hypixel.hytale.component.*;
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
import com.supremosan.truebackpack.helpers.BackpackUIUpdater;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BackpackArmorListener {

    private static final ThreadLocal<Boolean> PROCESSING = ThreadLocal.withInitial(() -> false);
    private static final Map<String, Short> BACKPACK_SIZES = new ConcurrentHashMap<>();
    private static final short CHEST_SLOT = 1;

    public static void register(@Nonnull TrueBackpack plugin) {
        BackpackConfig.registerDefaults();
        plugin.getEventRegistry().registerGlobal(
                LivingEntityInventoryChangeEvent.class,
                BackpackArmorListener::handle
        );
    }

    public static void registerBackpack(@Nonnull String itemId, short sizeBonus) {
        BACKPACK_SIZES.put(itemId, sizeBonus);
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
        if (entity == null) {
            return;
        }

        Ref<EntityStore> ref = entity.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }

        Store<EntityStore> store = ref.getStore();
        UUIDComponent uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComponent == null) {
            return;
        }

        ItemContainer changed = event.getItemContainer();
        if (changed == null) {
            return;
        }

        Inventory inv = entity.getInventory();
        ItemContainer armor = inv.getArmor();

        if (changed != armor) {
            return;
        }

        if (!event.getTransaction().wasSlotModified(CHEST_SLOT)) {
            return;
        }

        ItemStack oldBackpackItem = null;
        ItemStack newBackpackItem = null;
        Transaction transaction = event.getTransaction();

        switch (transaction) {
            case SlotTransaction slotTx -> {
                oldBackpackItem = slotTx.getSlotBefore();
                newBackpackItem = slotTx.getSlotAfter();
            }
            case MoveTransaction<?> moveTransaction -> {
                SlotTransaction removeTransaction = moveTransaction.getRemoveTransaction();

                if (removeTransaction.getSlot() == CHEST_SLOT) {
                    oldBackpackItem = removeTransaction.getSlotBefore();
                    newBackpackItem = removeTransaction.getSlotAfter();
                } else {
                    Object addTx = moveTransaction.getAddTransaction();
                    if (addTx instanceof ItemStackTransaction itemStackTx) {
                        for (ItemStackSlotTransaction slotTx : itemStackTx.getSlotTransactions()) {
                            if (slotTx.getSlot() == CHEST_SLOT) {
                                oldBackpackItem = slotTx.getSlotBefore();
                                newBackpackItem = slotTx.getSlotAfter();
                                break;
                            }
                        }
                    } else if (addTx instanceof SlotTransaction slotTx) {
                        if (slotTx.getSlot() == CHEST_SLOT) {
                            oldBackpackItem = slotTx.getSlotBefore();
                            newBackpackItem = slotTx.getSlotAfter();
                        }
                    }
                }
            }
            case ItemStackTransaction itemStackTx -> {
                for (ItemStackSlotTransaction slotTx : itemStackTx.getSlotTransactions()) {
                    if (slotTx.getSlot() == CHEST_SLOT) {
                        oldBackpackItem = slotTx.getSlotBefore();
                        newBackpackItem = slotTx.getSlotAfter();
                        break;
                    }
                }
            }
            default -> {
            }
        }

        short oldBonus = getBonus(oldBackpackItem);
        short newBonus = getBonus(newBackpackItem);

        if (oldBackpackItem == null && newBackpackItem == null) {
            ItemStack currentArmor = armor.getItemStack(CHEST_SLOT);

            if (currentArmor != null && !currentArmor.isEmpty()) {
                newBackpackItem = currentArmor;
                newBonus = getBonus(newBackpackItem);
            }
        }

        if (oldBonus == newBonus) {
            return;
        }

        PROCESSING.set(true);
        try {
            performBackpackResize(entity, ref, store, inv, armor,
                    oldBackpackItem, newBackpackItem,
                    oldBonus, newBonus);
        } finally {
            PROCESSING.set(false);
        }
    }

    /**
     * Performs the actual backpack resize operation.
     * This is separated to allow proper try-finally handling of the PROCESSING flag.
     */
    private static void performBackpackResize(@Nonnull LivingEntity entity,
                                              @Nonnull Ref<EntityStore> ref,
                                              @Nonnull Store<EntityStore> store,
                                              @Nonnull Inventory inv,
                                              @Nonnull ItemContainer armor,
                                              ItemStack oldBackpackItem,
                                              ItemStack newBackpackItem,
                                              short oldBonus,
                                              short newBonus) {

        // Step 1: Save current backpack contents BEFORE resizing
        List<ItemStack> currentBackpackContents = null;
        if (oldBonus > 0) {
            ItemContainer currentBackpack = inv.getBackpack();
            currentBackpackContents = getAllBackpackContents(currentBackpack);
        }

        // Step 2: Resize backpack (overflow will be discarded since we save to metadata)
        inv.resizeBackpack(newBonus, new ObjectArrayList<>());

        // Prevent backpacks inside backpacks
        if (newBonus > 0) {
            ItemContainer backpackContainer = inv.getBackpack();
            for (short slot = 0; slot < backpackContainer.getCapacity(); slot++) {
                backpackContainer.setSlotFilter(FilterActionType.ADD, slot,
                        (_, _, _, itemStack) -> {
                            if (itemStack != null && !itemStack.isEmpty()) {
                                return getBackpackSize(itemStack.getItemId()) == 0;
                            }
                            return true;
                        }
                );
            }
        }

        // Step 3: Save contents to old backpack (if unequipping with items)
        if (oldBackpackItem != null && !oldBackpackItem.isEmpty() && oldBonus > 0 && currentBackpackContents != null) {
            boolean hasItems = false;
            for (ItemStack item : currentBackpackContents) {
                if (item != null && !item.isEmpty()) {
                    hasItems = true;
                }
            }

            if (hasItems) {
                // Save ALL items to metadata
                ItemStack backpackWithContents = BackpackDataStorage.saveContentsToItem(oldBackpackItem, currentBackpackContents);
                ItemContainer mainInv = inv.getCombinedEverything();

                for (short i = 0; i < mainInv.getCapacity(); i++) {
                    ItemStack stackInSlot = mainInv.getItemStack(i);
                    if (stackInSlot != null && !stackInSlot.isEmpty() &&
                            stackInSlot.getItemId().equals(oldBackpackItem.getItemId()) &&
                            !BackpackDataStorage.hasStoredContents(stackInSlot)) {
                        mainInv.setItemStackForSlot(i, backpackWithContents);
                        break;
                    }
                }
            }
        }

        // Step 4: Load contents from new backpack (if equipping with saved contents)
        if (newBackpackItem != null && !newBackpackItem.isEmpty() && newBonus > 0) {
            if (BackpackDataStorage.hasStoredContents(newBackpackItem)) {
                List<ItemStack> storedContents = BackpackDataStorage.loadContentsFromItem(newBackpackItem);

                restoreContentsToBackpack(inv.getBackpack(), storedContents);

                newBackpackItem = BackpackDataStorage.clearContentsFromItem(newBackpackItem);
                armor.setItemStackForSlot(CHEST_SLOT, newBackpackItem);
            }
        }

        // Step 5: Update UI components to reflect backpack state
        BackpackUIUpdater.updateBackpackUI(entity, ref, store);
    }

    private static short getBonus(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        String itemId = stack.getItemId();
        return getBackpackSize(itemId);
    }

    @Nonnull
    private static List<ItemStack> getAllBackpackContents(@Nonnull ItemContainer backpack) {
        List<ItemStack> contents = new ArrayList<>();
        for (short i = 0; i < backpack.getCapacity(); i++) {
            contents.add(backpack.getItemStack(i));
        }
        return contents;
    }

    private static void restoreContentsToBackpack(@Nonnull ItemContainer backpack, @Nonnull List<ItemStack> contents) {
        short capacity = backpack.getCapacity();
        for (int i = 0; i < contents.size() && i < capacity; i++) {
            ItemStack item = contents.get(i);
            if (item != null && !item.isEmpty()) {
                backpack.setItemStackForSlot((short) i, item);
            }
        }
    }
}