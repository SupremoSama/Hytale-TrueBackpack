package com.supremosan.truebackpack;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
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

public class BackpackArmorListener extends RefSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Map<String, Short> BACKPACK_SIZES = new ConcurrentHashMap<>();

    private static final short CHEST_SLOT   = 1;
    private static final short STORAGE_SLOT = 0;

    private static final Map<String, String>  LAST_KNOWN_EQUIPPED = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> PROCESSING          = new ConcurrentHashMap<>();

    private static final Query<EntityStore> QUERY = Query.any();

    private static BackpackArmorListener INSTANCE;
    private static CommandBuffer<EntityStore> BUFFER;

    public BackpackArmorListener() {
        INSTANCE = this;
    }

    public static void register(@Nonnull TrueBackpack plugin) {
        INSTANCE = new BackpackArmorListener();
        plugin.getEntityStoreRegistry().registerSystem(INSTANCE);
        BackpackConfig.registerDefaults();
        plugin.getEventRegistry().registerGlobal(
                LivingEntityInventoryChangeEvent.class,
                event -> INSTANCE.handle(event));
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

    @Override
    public @Nonnull Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public void onEntityAdded(@Nonnull Ref<EntityStore> ref, @Nonnull AddReason reason,
                              @Nonnull Store<EntityStore> store,
                              @Nonnull CommandBuffer<EntityStore> buffer) {
        BUFFER = buffer;
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<EntityStore> ref, @Nonnull RemoveReason reason,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull CommandBuffer<EntityStore> buffer) {
        UUIDComponent c = store.getComponent(ref, UUIDComponent.getComponentType());
        if (c == null) return;
        String playerUuid = c.getUuid().toString();
        LAST_KNOWN_EQUIPPED.remove(playerUuid);
        PROCESSING.remove(playerUuid);
    }

    private void handle(@Nonnull LivingEntityInventoryChangeEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity == null) return;

        Ref<EntityStore> ref = entity.getReference();
        if (ref == null || !ref.isValid()) return;

        Store<EntityStore> store = ref.getStore();
        UUIDComponent uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComponent == null) return;

        String playerUuid = uuidComponent.getUuid().toString();

        if (Boolean.TRUE.equals(PROCESSING.get(playerUuid))) return;

        Inventory     inv     = entity.getInventory();
        ItemContainer armor   = inv.getArmor();
        ItemContainer storage = inv.getStorage();
        ItemContainer changed = event.getItemContainer();

        if (changed != armor && changed != storage) return;

        ItemStack liveArmor   = armor.getItemStack(CHEST_SLOT);
        ItemStack liveStorage = storage.getItemStack(STORAGE_SLOT);

        ItemStack currentEquipped = resolveEquipped(liveArmor, liveStorage);
        String    currentId       = instanceId(currentEquipped);
        String    lastKnownId     = LAST_KNOWN_EQUIPPED.get(playerUuid);

        if (Objects.equals(currentId, lastKnownId)) return;

        ItemStack previousEquipped = lastKnownId != null
                ? findByInstanceId(inv, lastKnownId) : null;

        short oldBonus = bonus(previousEquipped);
        short newBonus = bonus(currentEquipped);

        if (oldBonus == 0 && newBonus == 0) {
            LAST_KNOWN_EQUIPPED.remove(playerUuid);
            return;
        }

        ItemContainer equipContainer = bonus(liveArmor) > 0 ? armor : storage;
        short         equipSlot      = equipContainer == armor ? CHEST_SLOT : STORAGE_SLOT;

        PROCESSING.put(playerUuid, Boolean.TRUE);
        try {
            processEquipChange(
                    entity, ref, store, inv, playerUuid,
                    previousEquipped, currentEquipped,
                    oldBonus, newBonus,
                    equipContainer, equipSlot);

            if (currentId != null) {
                LAST_KNOWN_EQUIPPED.put(playerUuid, currentId);
            } else {
                LAST_KNOWN_EQUIPPED.remove(playerUuid);
            }
        } finally {
            PROCESSING.remove(playerUuid);
        }
    }

    private void processEquipChange(
            @Nonnull  LivingEntity      entity,
            @Nonnull  Ref<EntityStore>   ref,
            @Nonnull  Store<EntityStore> store,
            @Nonnull  Inventory          inv,
            @Nonnull  String             playerUuid,
            @Nullable ItemStack          oldItem,
            @Nullable ItemStack          newItem,
            short                        oldBonus,
            short                        newBonus,
            @Nullable ItemContainer      equipContainer,
            short                        equipSlot) {

        if (oldBonus > 0 && newBonus > 0 && oldItem != null && newItem != null
                && equipContainer != null) {
            ItemStack outgoing = saveAndUnequip(oldItem, inv.getBackpack());

            if (writeToContainerByInstance(inv.getStorage(), oldItem, outgoing)
                    && writeToContainerByInstance(inv.getBackpack(), oldItem, outgoing)
                    && writeToContainerByInstance(inv.getHotbar(), oldItem, outgoing)
                    && writeToContainerByInstance(inv.getArmor(), oldItem, outgoing)) {
                LOGGER.atWarning().log(
                        "[TrueBackpack] SWAP: could not locate outgoing backpack (instanceId=%s)",
                        BackpackItemFactory.getInstanceId(oldItem));
            }

            newItem = ensureEquipped(newItem, equipContainer, equipSlot);
            resizeAndRestore(entity, ref, store, inv, playerUuid,
                    newItem, newBonus, equipContainer, equipSlot);
            return;
        }

        if (newBonus > 0 && newItem != null && equipContainer != null) {
            newItem = ensureEquipped(newItem, equipContainer, equipSlot);
            resizeAndRestore(entity, ref, store, inv, playerUuid,
                    newItem, newBonus, equipContainer, equipSlot);
            return;
        }

        if (oldBonus > 0 && oldItem != null) {
            ItemStack savedOld = saveAndUnequip(oldItem, inv.getBackpack());

            boolean notFound =
                    writeToContainerByInstance(inv.getStorage(), oldItem, savedOld)
                            && writeToContainerByInstance(inv.getBackpack(), oldItem, savedOld)
                            && writeToContainerByInstance(inv.getHotbar(), oldItem, savedOld)
                            && writeToContainerByInstance(inv.getArmor(), oldItem, savedOld);

            if (notFound) {
                dropSavedBackpack(ref, store, savedOld);
            }
        }

        resizeAndRestore(entity, ref, store, inv, playerUuid,
                null, (short) 0, null, (short) 0);
    }

    private void dropSavedBackpack(@Nonnull Ref<EntityStore>   ref,
                                   @Nonnull Store<EntityStore> store,
                                   @Nonnull ItemStack           savedItem) {
        if (BUFFER == null) {
            LOGGER.atWarning().log("[TrueBackpack] Cannot drop backpack — CommandBuffer not available.");
            return;
        }

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        HeadRotation       rotation  = store.getComponent(ref, HeadRotation.getComponentType());
        if (transform == null || rotation == null) return;

        Vector3d pos = transform.getPosition().clone().add(0, 1, 0);
        Vector3f rot = rotation.getRotation().clone();

        List<ItemStack> toDrop = List.of(savedItem);
        Holder<EntityStore>[] drops = ItemComponent.generateItemDrops(store, toDrop, pos, rot);
        BUFFER.addEntities(drops, AddReason.SPAWN);
    }

    private void resizeAndRestore(
            @Nonnull  LivingEntity      entity,
            @Nonnull  Ref<EntityStore>   ref,
            @Nonnull  Store<EntityStore> store,
            @Nonnull  Inventory          inv,
            @Nonnull  String             playerUuid,
            @Nullable ItemStack          newItem,
            short                        newBonus,
            @Nullable ItemContainer      equipContainer,
            short                        equipSlot) {

        inv.resizeBackpack(newBonus, new ObjectArrayList<>());

        if (newBonus > 0 && equipContainer != null) {
            ItemContainer bp = inv.getBackpack();

            for (short slot = 0; slot < bp.getCapacity(); slot++) {
                bp.setSlotFilter(FilterActionType.ADD, slot,
                        (_, _, _, item) -> item == null
                                || item.isEmpty()
                                || getBackpackSize(item.getItemId()) == 0);
                bp.setItemStackForSlot(slot, ItemStack.EMPTY);
            }

            ItemStack live = resolveLatestItem(inv, newItem);
            if (live != null) newItem = live;

            if (newItem != null && BackpackItemFactory.hasContents(newItem)) {
                restoreContentsToBackpack(bp, BackpackItemFactory.loadContents(newItem));
                ItemStack cleaned = BackpackItemFactory.setEquipped(
                        BackpackItemFactory.clearContents(newItem), true);
                equipContainer.setItemStackForSlot(equipSlot, cleaned);
                newItem = cleaned;
            }

            BackpackDataStorage.setLiveContents(playerUuid, getAllBackpackContents(bp));
            if (equipContainer != inv.getArmor() || newItem == null)
                BackpackDataStorage.clearActiveItem(playerUuid);

        } else {
            BackpackDataStorage.clearActiveItem(playerUuid);
        }

        BackpackUIUpdater.updateBackpackUI(entity, ref, store);
    }

    @Nullable
    private static ItemStack resolveEquipped(@Nullable ItemStack armorChest,
                                             @Nullable ItemStack storageSlot0) {
        if (!ItemStack.isEmpty(armorChest) && bonus(armorChest) > 0)   return armorChest;
        if (!ItemStack.isEmpty(storageSlot0) && bonus(storageSlot0) > 0) return storageSlot0;
        return null;
    }

    @Nullable
    private static ItemStack findByInstanceId(@Nonnull Inventory inv,
                                              @Nonnull String    targetId) {
        for (ItemContainer container : new ItemContainer[]{
                inv.getArmor(), inv.getStorage(), inv.getBackpack(), inv.getHotbar()}) {
            for (short slot = 0; slot < container.getCapacity(); slot++) {
                ItemStack candidate = container.getItemStack(slot);
                if (candidate == null || candidate.isEmpty()) continue;
                if (targetId.equals(BackpackItemFactory.getInstanceId(candidate)))
                    return candidate;
            }
        }
        return null;
    }

    @Nonnull
    private static ItemStack ensureEquipped(@Nonnull ItemStack item,
                                            @Nonnull ItemContainer container,
                                            short slot) {
        boolean needsId   = !BackpackItemFactory.hasInstanceId(item);
        boolean needsFlag = !BackpackItemFactory.isEquipped(item);
        if (!needsId && !needsFlag) return item;
        if (needsId) item = BackpackItemFactory.createBackpackInstance(item);
        item = BackpackItemFactory.setEquipped(item, true);
        container.setItemStackForSlot(slot, item);
        return item;
    }

    @Nonnull
    private static ItemStack saveAndUnequip(@Nonnull ItemStack item,
                                            @Nonnull ItemContainer backpack) {
        List<ItemStack> liveContents = getAllBackpackContents(backpack);
        boolean hasItems = liveContents.stream().anyMatch(i -> i != null && !i.isEmpty());
        ItemStack saved = hasItems
                ? BackpackItemFactory.saveContents(item, liveContents)
                : item;
        return BackpackItemFactory.setEquipped(saved, false);
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

    private static short bonus(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        return getBackpackSize(stack.getItemId());
    }

    @Nullable
    private static String instanceId(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return BackpackItemFactory.getInstanceId(stack);
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