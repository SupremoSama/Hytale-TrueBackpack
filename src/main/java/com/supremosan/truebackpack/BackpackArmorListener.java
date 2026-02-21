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

    private record SlotDelta(
            @Nullable ItemStack       oldItem,
            @Nullable ItemStack       newItem,
            @Nullable MoveDestination moveDest
    ) {
        static final SlotDelta EMPTY = new SlotDelta(null, null, null);

        boolean hasChange() {
            return oldItem != null || newItem != null;
        }

        boolean isNoOp() {
            if (ItemStack.isEmpty(oldItem) && ItemStack.isEmpty(newItem)) return true;

            if (ItemStack.isEmpty(oldItem) || ItemStack.isEmpty(newItem)) return false;

            if (!Objects.equals(oldItem.getItemId(), newItem.getItemId())) return false;

            String oldId = BackpackItemFactory.getInstanceId(oldItem);
            String newId = BackpackItemFactory.getInstanceId(newItem);
            if (oldId != null && newId != null) return Objects.equals(oldId, newId);

            return oldItem.equals(newItem);
        }
    }

    public static void register(@Nonnull TrueBackpack plugin) {
        BackpackConfig.registerDefaults();
        plugin.getEventRegistry().registerGlobal(
                LivingEntityInventoryChangeEvent.class,
                BackpackArmorListener::handle);
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

    private static void handle(@Nonnull LivingEntityInventoryChangeEvent event) {
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

        Inventory     inv     = entity.getInventory();
        ItemContainer armor   = inv.getArmor();
        ItemContainer storage = inv.getStorage();

        boolean isArmorChange   = (changed == armor);
        boolean isStorageChange = (changed == storage);
        if (!isArmorChange && !isStorageChange) return;

        Transaction tx           = event.getTransaction();
        short       relevantSlot = isArmorChange ? CHEST_SLOT : STORAGE_SLOT;
        if (!tx.wasSlotModified(relevantSlot)) return;

        SlotDelta delta = extractDelta(tx, relevantSlot, isStorageChange);
        if (!delta.hasChange()) {
            return;
        }

        String oldItemId = itemId(delta.oldItem());
        String newItemId = itemId(delta.newItem());

        short oldBonus = oldItemId != null ? getBackpackSize(oldItemId) : 0;
        short newBonus = newItemId != null ? getBackpackSize(newItemId) : 0;

        if (oldBonus == 0 && newBonus == 0) return;

        if (delta.isNoOp()) return;

        {
            ItemStack liveSlot = isArmorChange
                    ? armor.getItemStack(CHEST_SLOT)
                    : storage.getItemStack(STORAGE_SLOT);

            String liveId   = liveSlot != null ? BackpackItemFactory.getInstanceId(liveSlot) : null;
            short  liveSz   = liveSlot != null && !liveSlot.isEmpty()
                    ? getBackpackSize(liveSlot.getItemId()) : 0;

            if (liveSz > 0 && liveId != null && BackpackItemFactory.isEquipped(liveSlot)) {
                String incomingId;
                if (newBonus > 0) {
                    incomingId = BackpackItemFactory.getInstanceId(delta.newItem());
                } else {
                    assert delta.oldItem() != null;
                    incomingId = BackpackItemFactory.getInstanceId(delta.oldItem());
                }
                if (Objects.equals(liveId, incomingId)) return;
            }
        }

        if (isStorageChange) {
            ItemStack chestNow   = armor.getItemStack(CHEST_SLOT);
            String    chestNowId = itemId(chestNow);
            boolean   chestHasBp = chestNowId != null && getBackpackSize(chestNowId) > 0;

            if (oldBonus > 0 && newBonus == 0) {
                if (chestHasBp && Objects.equals(chestNowId, oldItemId)) return;
                if (chestHasBp) return;
            }

            if (newBonus > 0 && chestHasBp) return;
        }

        PROCESSING.set(true);
        try {
            performBackpackResize(
                    entity, ref, store, inv, playerUuid,
                    delta.oldItem(), delta.newItem(),
                    oldBonus, newBonus,
                    isArmorChange, delta.moveDest());
        } finally {
            PROCESSING.set(false);
        }
    }

    @Nonnull
    private static SlotDelta extractDelta(@Nonnull Transaction tx,
                                          short               relevantSlot,
                                          boolean             trackMoveDest) {
        return switch (tx) {
            case SlotTransaction slotTx -> {
                if (!slotTx.succeeded()) yield SlotDelta.EMPTY;
                if (slotTx.getSlot() != relevantSlot) yield SlotDelta.EMPTY;
                yield new SlotDelta(slotTx.getSlotBefore(), slotTx.getSlotAfter(), null);
            }

            case ItemStackTransaction itemStackTx -> {
                if (!itemStackTx.succeeded()) yield SlotDelta.EMPTY;
                yield findSucceededSlotTx(itemStackTx.getSlotTransactions(), relevantSlot);
            }

            case MoveTransaction<?> moveTx -> {
                if (!moveTx.succeeded()) yield SlotDelta.EMPTY;

                MoveType        moveType = moveTx.getMoveType();
                SlotTransaction removeTx = moveTx.getRemoveTransaction();
                Object          addTx    = moveTx.getAddTransaction();

                if (moveType == MoveType.MOVE_FROM_SELF) {
                    if (removeTx.getSlot() == relevantSlot && removeTx.succeeded()) {
                        MoveDestination dest = null;
                        if (trackMoveDest) {
                            short destSlot = extractAddSlot(addTx);
                            if (destSlot >= 0)
                                dest = new MoveDestination(moveTx.getOtherContainer(), destSlot);
                        }
                        yield new SlotDelta(
                                removeTx.getSlotBefore(),
                                removeTx.getSlotAfter(),
                                dest);
                    }
                }

                yield extractFromAddSide(addTx, relevantSlot);
            }

            default -> SlotDelta.EMPTY;
        };
    }

    @Nonnull
    private static SlotDelta findSucceededSlotTx(
            @Nonnull List<ItemStackSlotTransaction> list, short slot) {
        for (ItemStackSlotTransaction s : list) {
            if (s.succeeded() && s.getSlot() == slot)
                return new SlotDelta(s.getSlotBefore(), s.getSlotAfter(), null);
        }
        return SlotDelta.EMPTY;
    }

    @Nonnull
    private static SlotDelta extractFromAddSide(@Nullable Object addTx, short slot) {
        if (addTx instanceof SlotTransaction slotTx
                && slotTx.succeeded()
                && slotTx.getSlot() == slot)
            return new SlotDelta(slotTx.getSlotBefore(), slotTx.getSlotAfter(), null);

        if (addTx instanceof ItemStackTransaction itemStackTx && itemStackTx.succeeded())
            return findSucceededSlotTx(itemStackTx.getSlotTransactions(), slot);

        return SlotDelta.EMPTY;
    }

    private static short extractAddSlot(@Nullable Object addTx) {
        if (addTx instanceof SlotTransaction slotTx && slotTx.succeeded())
            return slotTx.getSlot();

        if (addTx instanceof ItemStackTransaction itemStackTx) {
            for (ItemStackSlotTransaction s : itemStackTx.getSlotTransactions()) {
                if (s.succeeded()
                        && s.getSlotAfter() != null
                        && !s.getSlotAfter().isEmpty())
                    return s.getSlot();
            }
        }
        return -1;
    }

    private static void performBackpackResize(
            @Nonnull  LivingEntity      entity,
            @Nonnull  Ref<EntityStore>   ref,
            @Nonnull  Store<EntityStore> store,
            @Nonnull  Inventory          inv,
            @Nonnull  String             playerUuid,
            @Nullable ItemStack          oldItem,
            @Nullable ItemStack          newItem,
            short                        oldBonus,
            short                        newBonus,
            boolean                      fromArmor,
            @Nullable MoveDestination    moveDest) {

        if (oldBonus > 0 && newBonus > 0 && oldItem != null && newItem != null) {
            ItemContainer currentBp     = inv.getBackpack();
            List<ItemStack> liveContents = getAllBackpackContents(currentBp);
            boolean         hasItems     = liveContents.stream()
                    .anyMatch(i -> i != null && !i.isEmpty());

            ItemStack outgoing = hasItems
                    ? BackpackItemFactory.saveContents(oldItem, liveContents)
                    : oldItem;

            outgoing = BackpackItemFactory.setEquipped(outgoing, false);

            if (writeToContainerByInstance(inv.getStorage(), oldItem, outgoing)
                    && writeToContainerByInstance(inv.getBackpack(), oldItem, outgoing)
                    && writeToContainerByInstance(inv.getHotbar(), oldItem, outgoing)
                    && writeToContainerByInstance(inv.getArmor(), oldItem, outgoing)) {
                LOGGER.atWarning().log(
                        "[TrueBackpack] SWAP: could not locate outgoing backpack (instanceId=%s)",
                        BackpackItemFactory.getInstanceId(oldItem));
            }

            boolean needsId   = !BackpackItemFactory.hasInstanceId(newItem);
            boolean needsFlag = !BackpackItemFactory.isEquipped(newItem);
            if (needsId || needsFlag) {
                if (needsId) newItem = BackpackItemFactory.createBackpackInstance(newItem);
                newItem = BackpackItemFactory.setEquipped(newItem, true);
                if (fromArmor) inv.getArmor().setItemStackForSlot(CHEST_SLOT,    newItem);
                else           inv.getStorage().setItemStackForSlot(STORAGE_SLOT, newItem);
            }

            resizeAndRestore(entity, ref, store, inv, playerUuid, newItem, newBonus, fromArmor);
            return;
        }

        if (newBonus > 0 && newItem != null) {
            boolean needsId   = !BackpackItemFactory.hasInstanceId(newItem);
            boolean needsFlag = !BackpackItemFactory.isEquipped(newItem);

            if (needsId || needsFlag) {
                if (needsId) newItem = BackpackItemFactory.createBackpackInstance(newItem);
                newItem = BackpackItemFactory.setEquipped(newItem, true);
                if (fromArmor) inv.getArmor().setItemStackForSlot(CHEST_SLOT,    newItem);
                else           inv.getStorage().setItemStackForSlot(STORAGE_SLOT, newItem);
            }
        }

        if (oldBonus > 0 && oldItem != null) {
            ItemContainer currentBp = inv.getBackpack();

            final ItemStack savedOld;
            if (moveDest != null) {
                List<ItemStack> liveContents = getAllBackpackContents(currentBp);
                boolean hasItems = liveContents.stream().anyMatch(i -> i != null && !i.isEmpty());
                ItemStack withContents = hasItems
                        ? BackpackItemFactory.saveContents(oldItem, liveContents)
                        : oldItem;
                savedOld = BackpackItemFactory.setEquipped(withContents, false);
            } else {
                savedOld = BackpackItemFactory.setEquipped(oldItem, false);
            }

            if (moveDest != null) {
                moveDest.container().setItemStackForSlot(moveDest.slot(), savedOld);
            } else {
                if (writeToContainerByInstance(inv.getStorage(), oldItem, savedOld)
                        && writeToContainerByInstance(inv.getBackpack(), oldItem, savedOld)
                        && writeToContainerByInstance(inv.getHotbar(), oldItem, savedOld)
                        && writeToContainerByInstance(inv.getArmor(), oldItem, savedOld)) {
                    LOGGER.atWarning().log(
                            "[TrueBackpack] Could not locate old backpack (instanceId=%s) to unequip",
                            BackpackItemFactory.getInstanceId(oldItem));
                }
            }
        }

        resizeAndRestore(entity, ref, store, inv, playerUuid, newItem, newBonus, fromArmor);
    }

    private static void resizeAndRestore(
            @Nonnull  LivingEntity      entity,
            @Nonnull  Ref<EntityStore>   ref,
            @Nonnull  Store<EntityStore> store,
            @Nonnull  Inventory          inv,
            @Nonnull  String             playerUuid,
            @Nullable ItemStack          newItem,
            short                        newBonus,
            boolean                      fromArmor) {

        inv.resizeBackpack(newBonus, new ObjectArrayList<>());

        if (newBonus > 0) {
            ItemContainer bp = inv.getBackpack();
            for (short slot = 0; slot < bp.getCapacity(); slot++) {
                bp.setSlotFilter(FilterActionType.ADD, slot,
                        (_, _, _, item) -> item == null
                                || item.isEmpty()
                                || getBackpackSize(item.getItemId()) == 0);
            }
        }

        if (newBonus > 0) {
            ItemContainer bp = inv.getBackpack();

            for (short slot = 0; slot < bp.getCapacity(); slot++)
                bp.setItemStackForSlot(slot, ItemStack.EMPTY);

            ItemStack live = resolveLatestItem(inv, newItem);
            if (live != null) newItem = live;

            if (newItem != null && BackpackItemFactory.hasContents(newItem)) {
                restoreContentsToBackpack(bp, BackpackItemFactory.loadContents(newItem));

                ItemStack cleaned = BackpackItemFactory.clearContents(newItem);
                cleaned = BackpackItemFactory.setEquipped(cleaned, true);
                if (fromArmor) inv.getArmor().setItemStackForSlot(CHEST_SLOT,    cleaned);
                else           inv.getStorage().setItemStackForSlot(STORAGE_SLOT, cleaned);
                newItem = cleaned;
            }

            BackpackDataStorage.setLiveContents(playerUuid, getAllBackpackContents(bp));
            if (!fromArmor || newItem == null)
                BackpackDataStorage.clearActiveItem(playerUuid);

        } else {
            BackpackDataStorage.clearActiveItem(playerUuid);
        }

        BackpackUIUpdater.updateBackpackUI(entity, ref, store);
    }

    private static boolean writeToContainerByInstance(@Nonnull ItemContainer container,
                                                      @Nonnull ItemStack     target,
                                                      @Nonnull ItemStack     replacement) {
        String targetId = BackpackItemFactory.getInstanceId(target);
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack candidate = container.getItemStack(slot);
            if (candidate == null || candidate.isEmpty()) continue;

            boolean match = targetId != null
                    ? targetId.equals(BackpackItemFactory.getInstanceId(candidate))
                    : candidate.equals(target);

            if (match) {
                container.setItemStackForSlot(slot, replacement);
                return false;
            }
        }
        return true;
    }

    @Nullable
    private static ItemStack resolveLatestItem(@Nonnull Inventory  inv,
                                               @Nullable ItemStack target) {
        if (target == null) return null;
        String targetId = BackpackItemFactory.getInstanceId(target);
        if (targetId == null) return null;

        ItemStack chestItem = inv.getArmor().getItemStack(CHEST_SLOT);
        if (chestItem != null && !chestItem.isEmpty()
                && targetId.equals(BackpackItemFactory.getInstanceId(chestItem)))
            return chestItem;

        for (ItemContainer container : new ItemContainer[]{
                inv.getStorage(), inv.getBackpack(), inv.getHotbar()}) {
            for (short slot = 0; slot < container.getCapacity(); slot++) {
                ItemStack candidate = container.getItemStack(slot);
                if (candidate == null || candidate.isEmpty()) continue;
                if (targetId.equals(BackpackItemFactory.getInstanceId(candidate)))
                    return candidate;
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
        List<ItemStack> contents = new ArrayList<>(backpack.getCapacity());
        for (short i = 0; i < backpack.getCapacity(); i++)
            contents.add(backpack.getItemStack(i));
        return contents;
    }

    private static void restoreContentsToBackpack(@Nonnull ItemContainer   backpack,
                                                  @Nonnull List<ItemStack> contents) {
        short capacity = backpack.getCapacity();
        for (int i = 0; i < contents.size() && i < capacity; i++) {
            ItemStack item = contents.get(i);
            if (item != null && !item.isEmpty())
                backpack.setItemStackForSlot((short) i, item);
        }
    }
}