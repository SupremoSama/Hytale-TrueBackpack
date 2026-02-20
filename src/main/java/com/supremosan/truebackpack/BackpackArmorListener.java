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
import com.hypixel.hytale.server.core.inventory.transaction.MoveType;
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
    private static final short CHEST_SLOT   = 1;
    private static final short STORAGE_SLOT = 0;

    private record MoveDestination(ItemContainer container, short slot) {}

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
        if (PROCESSING.get()) return;

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

        Inventory inv        = entity.getInventory();
        ItemContainer armor   = inv.getArmor();
        ItemContainer storage = inv.getStorage();

        boolean isArmorChange   = (changed == armor);
        boolean isStorageChange = (changed == storage);
        if (!isArmorChange && !isStorageChange) return;

        Transaction tx = event.getTransaction();
        short relevantSlot = isArmorChange ? CHEST_SLOT : STORAGE_SLOT;
        if (!tx.wasSlotModified(relevantSlot)) return;

        ItemStack chestNow   = armor.getItemStack(CHEST_SLOT);
        ItemStack storageNow = storage.getItemStack(STORAGE_SLOT);

        String chestNowId   = itemId(chestNow);
        String storageNowId = itemId(storageNow);

        boolean chestHasBackpack = chestNowId != null && getBackpackSize(chestNowId) > 0;

        ItemStack       oldItem  = null;
        ItemStack       newItem  = null;
        MoveDestination moveDest = null;

        switch (tx) {
            case SlotTransaction slotTx -> {
                if (slotTx.getSlot() == relevantSlot) {
                    oldItem = slotTx.getSlotBefore();
                    newItem = slotTx.getSlotAfter();
                }
            }

            case MoveTransaction<?> moveTx -> {
                SlotTransaction removeTx = moveTx.getRemoveTransaction();
                Object          addTx    = moveTx.getAddTransaction();
                MoveType        moveType = moveTx.getMoveType();

                if (moveType == MoveType.MOVE_FROM_SELF) {
                    if (removeTx.getSlot() == relevantSlot) {
                        oldItem = removeTx.getSlotBefore();
                        newItem = removeTx.getSlotAfter();

                        if (isStorageChange) {
                            ItemContainer dest = moveTx.getOtherContainer();
                            short destSlot = extractAddSlot(addTx);
                            if (destSlot >= 0) {
                                moveDest = new MoveDestination(dest, destSlot);
                            }
                        }
                    }
                } else {
                    if (addTx instanceof SlotTransaction addSlotTx
                            && addSlotTx.getSlot() == relevantSlot) {
                        oldItem = addSlotTx.getSlotBefore();
                        newItem = addSlotTx.getSlotAfter();
                    } else if (addTx instanceof ItemStackTransaction addItemStackTx) {
                        for (ItemStackSlotTransaction s : addItemStackTx.getSlotTransactions()) {
                            if (s.getSlot() == relevantSlot) {
                                oldItem = s.getSlotBefore();
                                newItem = s.getSlotAfter();
                                break;
                            }
                        }
                    }
                }
            }

            case ItemStackTransaction itemStackTx -> {
                for (ItemStackSlotTransaction s : itemStackTx.getSlotTransactions()) {
                    if (s.getSlot() == relevantSlot) {
                        oldItem = s.getSlotBefore();
                        newItem = s.getSlotAfter();
                        break;
                    }
                }
            }

            default -> LOGGER.atWarning().log("[TrueBackpack] Unhandled transaction type: %s",
                    tx.getClass().getName());
        }

        String oldItemId = itemId(oldItem);
        String newItemId = itemId(newItem);

        if (oldItemId == null && newItemId == null) {
            if (isArmorChange && chestHasBackpack) {
                newItem   = chestNow;
                newItemId = chestNowId;
            } else if (isStorageChange && storageNowId != null) {
                newItem   = storageNow;
                newItemId = storageNowId;
            }
        }

        short oldBonus = oldItemId != null ? getBackpackSize(oldItemId) : 0;
        short newBonus = newItemId != null ? getBackpackSize(newItemId) : 0;

        if (oldBonus == 0 && newBonus == 0) return;
        if (oldBonus == newBonus && Objects.equals(oldItemId, newItemId)) return;

        if (isStorageChange) {
            if (oldBonus > 0 && newBonus == 0) {
                if (chestHasBackpack && Objects.equals(chestNowId, oldItemId)) return;
                if (chestHasBackpack) return;
            }
            if (newBonus > 0 && chestHasBackpack) return;
        }

        PROCESSING.set(true);
        try {
            performBackpackResize(entity, ref, store, inv, playerUuid,
                    oldItem, newItem, oldBonus, newBonus, isArmorChange, moveDest);
        } finally {
            PROCESSING.set(false);
        }
    }

    private static short extractAddSlot(@Nullable Object addTx) {
        if (addTx instanceof SlotTransaction slotTx) {
            return slotTx.getSlot();
        } else if (addTx instanceof ItemStackTransaction itemStackTx) {
            for (ItemStackSlotTransaction s : itemStackTx.getSlotTransactions()) {
                if (s.getSlotAfter() != null && !s.getSlotAfter().isEmpty()) {
                    return s.getSlot();
                }
            }
        }
        return -1;
    }

    private static void performBackpackResize(@Nonnull LivingEntity entity,
                                              @Nonnull Ref<EntityStore> ref,
                                              @Nonnull Store<EntityStore> store,
                                              @Nonnull Inventory inv,
                                              @Nonnull String playerUuid,
                                              @Nullable ItemStack oldItem,
                                              @Nullable ItemStack newItem,
                                              short oldBonus,
                                              short newBonus,
                                              boolean fromArmor,
                                              @Nullable MoveDestination moveDest) {
        if (newBonus > 0 && newItem != null) {
            if (!BackpackItemFactory.hasInstanceId(newItem)) {
                newItem = BackpackItemFactory.createBackpackInstance(newItem);
            }
            newItem = BackpackItemFactory.setEquipped(newItem, fromArmor);

            if (fromArmor) {
                inv.getArmor().setItemStackForSlot(CHEST_SLOT, newItem);
            } else {
                inv.getStorage().setItemStackForSlot(STORAGE_SLOT, newItem);
            }
        }

        if (oldBonus > 0 && oldItem != null) {
            ItemContainer currentBp      = inv.getBackpack();
            List<ItemStack> liveContents = getAllBackpackContents(currentBp);
            boolean hasItems = liveContents.stream().anyMatch(i -> i != null && !i.isEmpty());

            ItemStack savedOld = hasItems
                    ? BackpackItemFactory.saveContents(oldItem, liveContents)
                    : BackpackItemFactory.clearContents(oldItem);

            savedOld = BackpackItemFactory.setEquipped(savedOld, false);

            if (moveDest != null) {
                moveDest.container().setItemStackForSlot(moveDest.slot(), savedOld);
            } else {
                boolean written = false;
                for (ItemContainer container : new ItemContainer[]{
                        inv.getStorage(), inv.getBackpack(), inv.getHotbar(), inv.getArmor()}) {
                    if (writeToContainerByInstance(container, oldItem, savedOld)) {
                        written = true;
                        break;
                    }
                }
                if (!written) {
                    LOGGER.atWarning().log(
                            "[TrueBackpack] Could not find old backpack to save contents into");
                }
            }
        }

        inv.resizeBackpack(newBonus, new ObjectArrayList<>());

        if (newBonus > 0) {
            ItemContainer bp = inv.getBackpack();
            for (short slot = 0; slot < bp.getCapacity(); slot++) {
                bp.setSlotFilter(FilterActionType.ADD, slot,
                        (_, _, _, item) -> item == null || item.isEmpty()
                                || getBackpackSize(item.getItemId()) == 0);
            }
        }

        if (newBonus > 0 && newItem != null) {
            ItemStack live = resolveLatestItem(inv, newItem);
            if (live != null) newItem = live;

            if (BackpackItemFactory.hasContents(newItem)) {
                List<ItemStack> stored = BackpackItemFactory.loadContents(newItem);
                restoreContentsToBackpack(inv.getBackpack(), stored);

                ItemStack cleaned = BackpackItemFactory.clearContents(newItem);
                cleaned = BackpackItemFactory.setEquipped(cleaned, fromArmor);

                if (fromArmor) {
                    inv.getArmor().setItemStackForSlot(CHEST_SLOT, cleaned);
                } else {
                    inv.getStorage().setItemStackForSlot(STORAGE_SLOT, cleaned);
                }
            }

            if (fromArmor) {
                BackpackDataStorage.setActiveItem(playerUuid, newItem);
            } else {
                BackpackDataStorage.clearActiveItem(playerUuid);
            }
        } else {
            BackpackDataStorage.clearActiveItem(playerUuid);
        }

        BackpackUIUpdater.updateBackpackUI(entity, ref, store);
    }

    private static boolean writeToContainerByInstance(@Nonnull ItemContainer container,
                                                      @Nonnull ItemStack target,
                                                      @Nonnull ItemStack replacement) {
        String targetId = BackpackItemFactory.getInstanceId(target);
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack candidate = container.getItemStack(slot);
            if (candidate == null || candidate.isEmpty()) continue;
            boolean match = targetId != null
                    ? targetId.equals(BackpackItemFactory.getInstanceId(candidate))
                    : candidate.equals(target);
            if (match) {
                container.setItemStackForSlot(slot, replacement);
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static ItemStack resolveLatestItem(@Nonnull Inventory inv,
                                               @Nonnull ItemStack target) {
        String targetId = BackpackItemFactory.getInstanceId(target);
        if (targetId == null) return null;

        ItemStack chestItem = inv.getArmor().getItemStack(CHEST_SLOT);
        if (chestItem != null && !chestItem.isEmpty()
                && targetId.equals(BackpackItemFactory.getInstanceId(chestItem))) {
            return chestItem;
        }

        for (ItemContainer container : new ItemContainer[]{
                inv.getStorage(), inv.getBackpack(), inv.getHotbar()}) {
            for (short slot = 0; slot < container.getCapacity(); slot++) {
                ItemStack candidate = container.getItemStack(slot);
                if (candidate == null || candidate.isEmpty()) continue;
                if (targetId.equals(BackpackItemFactory.getInstanceId(candidate))) {
                    return candidate;
                }
            }
        }
        return null;
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