package com.supremosan.truebackpack.listener;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.supremosan.truebackpack.TrueBackpack;
import com.supremosan.truebackpack.cosmetic.CosmeticPreferenceUtils;
import com.supremosan.truebackpack.data.BackpackDataStorage;
import com.supremosan.truebackpack.factory.BackpackItemFactory;
import com.supremosan.truebackpack.ui.BackpackUIUpdater;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class BackpackArmorListener extends EntityEventSystem<EntityStore, InventoryChangeEvent> {

    private static final String ATTACHMENT_SLOT_KEY = "truebackpack:backpack";

    private static final Map<String, Short> BACKPACK_SIZES = new ConcurrentHashMap<>();
    private static final Map<String, String[]> BACKPACK_VISUALS = new LinkedHashMap<>();

    public interface EquipChangeListener {
        void onEquipChange(@Nonnull String playerUuid, @Nonnull Player player, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref);
    }

    private static final List<EquipChangeListener> EQUIP_CHANGE_LISTENERS = new CopyOnWriteArrayList<>();

    private static final short CHEST_SLOT = 1;
    private static final short STORAGE_SLOT = 0;

    private static final Map<String, String> LAST_KNOWN_EQUIPPED = new ConcurrentHashMap<>();
    private static final Map<String, String> LAST_KNOWN_EQUIPPED_ITEM_ID = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> PROCESSING_EQUIP = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> PROCESSING_CONTAINER = new ConcurrentHashMap<>();

    private static volatile Query<EntityStore> QUERY;

    public BackpackArmorListener() {
        super(InventoryChangeEvent.class);
    }

    public static void register(@Nonnull TrueBackpack plugin) {
        plugin.getEntityStoreRegistry().registerSystem(new BackpackArmorListener());
    }

    public static void registerBackpack(@Nonnull String baseItemId, short sizeBonus, @Nonnull String modelPath, @Nonnull String texturePath) {
        BACKPACK_SIZES.put(baseItemId, sizeBonus);
        BACKPACK_VISUALS.put(baseItemId, new String[]{modelPath, texturePath, "", ""});
    }

    public static void addEquipChangeListener(@Nonnull EquipChangeListener listener) {
        EQUIP_CHANGE_LISTENERS.add(listener);
    }

    public static boolean hasEquippedBackpack(@Nonnull String playerUuid) {
        return LAST_KNOWN_EQUIPPED.containsKey(playerUuid);
    }

    @Nullable
    public static String getEquippedItemId(@Nonnull String playerUuid) {
        return LAST_KNOWN_EQUIPPED_ITEM_ID.get(playerUuid);
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
    @Nullable
    public Query<EntityStore> getQuery() {
        if (QUERY == null) {
            QUERY = Query.or(InventoryComponent.Armor.getComponentType(),
                    InventoryComponent.Storage.getComponentType(),
                    InventoryComponent.Backpack.getComponentType());
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
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);

        UUIDComponent uuidComponent = archetypeChunk.getComponent(index, UUIDComponent.getComponentType());
        if (uuidComponent == null) return;

        String playerUuid = uuidComponent.getUuid().toString();

        if (CosmeticListener.isProcessing()) return;

        InventoryComponent.Armor armorComp = archetypeChunk.getComponent(index, InventoryComponent.Armor.getComponentType());
        InventoryComponent.Storage storageComp = archetypeChunk.getComponent(index, InventoryComponent.Storage.getComponentType());
        InventoryComponent.Backpack backpackComp = archetypeChunk.getComponent(index, InventoryComponent.Backpack.getComponentType());
        InventoryComponent.Hotbar hotbarComp = archetypeChunk.getComponent(index, InventoryComponent.Hotbar.getComponentType());

        if (armorComp == null || storageComp == null) return;

        boolean isBackpackEvent = event.getComponentType() == InventoryComponent.Backpack.getComponentType();
        boolean isArmorEvent = event.getComponentType() == InventoryComponent.Armor.getComponentType();
        boolean isStorageEvent = event.getComponentType() == InventoryComponent.Storage.getComponentType();

        if (isBackpackEvent) {
            handleBackpackContainerChange(armorComp, storageComp, backpackComp, hotbarComp, playerUuid);
            return;
        }

        if (!isArmorEvent && !isStorageEvent) return;
        if (isArmorEvent && !event.getTransaction().wasSlotModified(CHEST_SLOT)) return;
        if (isStorageEvent && !event.getTransaction().wasSlotModified(STORAGE_SLOT)) return;

        Player entity = archetypeChunk.getComponent(index, Player.getComponentType());
        if (entity == null) return;

        handleEquipContainerChange(entity, ref, store, armorComp, storageComp, backpackComp, hotbarComp, playerUuid);
    }

    public static void onPlayerRemove(
            @Nonnull String playerUuid,
            @Nullable InventoryComponent.Armor armorComp,
            @Nullable InventoryComponent.Storage storageComp,
            @Nullable InventoryComponent.Backpack backpackComp,
            @Nullable InventoryComponent.Hotbar hotbarComp) {
        if (armorComp != null && storageComp != null) {
            persistContainerToEquippedItem(armorComp, storageComp, backpackComp, hotbarComp, playerUuid);
        }

        LAST_KNOWN_EQUIPPED.remove(playerUuid);
        LAST_KNOWN_EQUIPPED_ITEM_ID.remove(playerUuid);
        PROCESSING_EQUIP.remove(playerUuid);
        PROCESSING_CONTAINER.remove(playerUuid);
        BackpackDataStorage.clearActiveItem(playerUuid);
        CosmeticListener.onPlayerLeave(playerUuid);
    }

    private void handleBackpackContainerChange(
            @Nonnull InventoryComponent.Armor armorComp,
            @Nonnull InventoryComponent.Storage storageComp,
            @Nullable InventoryComponent.Backpack backpackComp,
            @Nullable InventoryComponent.Hotbar hotbarComp,
            @Nonnull String playerUuid) {
        if (Boolean.TRUE.equals(PROCESSING_CONTAINER.get(playerUuid))) return;

        String equippedInstanceId = LAST_KNOWN_EQUIPPED.get(playerUuid);
        if (equippedInstanceId == null) return;

        PROCESSING_CONTAINER.put(playerUuid, Boolean.TRUE);
        try {
            ItemStack equippedItem = findByInstanceId(armorComp, storageComp, backpackComp, hotbarComp, equippedInstanceId);
            if (equippedItem == null) return;

            ItemContainer equippedContainer = resolveEquipContainer(armorComp, storageComp, equippedItem);
            short equippedSlot = resolveEquipSlot(armorComp, storageComp, equippedItem);
            if (equippedContainer == null || equippedSlot < 0) return;

            if (backpackComp == null) return;
            List<ItemStack> liveContents = getAllBackpackContents(backpackComp.getInventory());
            ItemStack updated = BackpackItemFactory.saveContents(equippedItem, liveContents);
            equippedContainer.setItemStackForSlot(equippedSlot, updated);

            BackpackDataStorage.setLiveContents(playerUuid, liveContents);
        } finally {
            PROCESSING_CONTAINER.remove(playerUuid);
        }
    }

    private void handleEquipContainerChange(
            @Nonnull Player entity,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull InventoryComponent.Armor armorComp,
            @Nonnull InventoryComponent.Storage storageComp,
            @Nullable InventoryComponent.Backpack backpackComp,
            @Nullable InventoryComponent.Hotbar hotbarComp,
            @Nonnull String playerUuid) {
        if (Boolean.TRUE.equals(PROCESSING_EQUIP.get(playerUuid))) return;

        ItemStack liveArmor = armorComp.getInventory().getItemStack(CHEST_SLOT);
        ItemStack liveStorage = storageComp.getInventory().getItemStack(STORAGE_SLOT);

        ItemStack currentEquipped = resolveEquipped(liveArmor, liveStorage);
        String lastKnownId = LAST_KNOWN_EQUIPPED.get(playerUuid);
        String currentId = currentEquipped != null ? BackpackItemFactory.getInstanceId(currentEquipped) : null;

        boolean currentIsNew = currentEquipped != null && currentId == null;
        if (!currentIsNew && Objects.equals(currentId, lastKnownId)) return;

        short newBonus = bonus(currentEquipped);
        boolean hadBackpack = lastKnownId != null;
        boolean hasBackpack = newBonus > 0;

        if (!hadBackpack && !hasBackpack) {
            LAST_KNOWN_EQUIPPED.remove(playerUuid);
            LAST_KNOWN_EQUIPPED_ITEM_ID.remove(playerUuid);
            return;
        }

        ItemStack previousItem = lastKnownId != null ? findByInstanceId(armorComp, storageComp, backpackComp, hotbarComp, lastKnownId) : null;
        short oldBonus = previousItem != null ? bonus(previousItem) : (hadBackpack ? (short) 1 : (short) 0);

        if (oldBonus == 0 && newBonus == 0) {
            LAST_KNOWN_EQUIPPED.remove(playerUuid);
            LAST_KNOWN_EQUIPPED_ITEM_ID.remove(playerUuid);
            return;
        }

        PROCESSING_EQUIP.put(playerUuid, Boolean.TRUE);
        try {
            String finalInstanceId = processEquipChange(entity, ref, store, armorComp, storageComp, backpackComp, hotbarComp, playerUuid, currentEquipped, newBonus);

            if (finalInstanceId != null) {
                LAST_KNOWN_EQUIPPED.put(playerUuid, finalInstanceId);
                if (currentEquipped != null) {
                    LAST_KNOWN_EQUIPPED_ITEM_ID.put(playerUuid, currentEquipped.getItemId());
                }
            } else {
                LAST_KNOWN_EQUIPPED.remove(playerUuid);
                LAST_KNOWN_EQUIPPED_ITEM_ID.remove(playerUuid);
            }
        } finally {
            PROCESSING_EQUIP.remove(playerUuid);
        }
    }

    @Nullable
    private String processEquipChange(
            @Nonnull Player entity,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull InventoryComponent.Armor armorComp,
            @Nonnull InventoryComponent.Storage storageComp,
            @Nullable InventoryComponent.Backpack backpackComp,
            @Nullable InventoryComponent.Hotbar hotbarComp,
            @Nonnull String playerUuid,
            @Nullable ItemStack newItem,
            short newBonus) {
        String lastKnownId = LAST_KNOWN_EQUIPPED.get(playerUuid);

        if (newBonus > 0 && newItem != null) {
            ItemContainer equipContainer = resolveEquipContainer(armorComp, storageComp, newItem);
            short equipSlot = resolveEquipSlot(armorComp, storageComp, newItem);

            if (equipContainer == null || equipSlot < 0) {
                equipContainer = bonus(armorComp.getInventory().getItemStack(CHEST_SLOT)) > 0 ? armorComp.getInventory() : storageComp.getInventory();
                equipSlot = equipContainer == armorComp.getInventory() ? CHEST_SLOT : STORAGE_SLOT;
            }

            if (lastKnownId != null) {
                clearEquippedFlagByInstanceId(armorComp, storageComp, backpackComp, hotbarComp, lastKnownId);
            }

            newItem = ensureInstanceId(newItem, equipContainer, equipSlot);

            List<ItemStack> savedContents = BackpackItemFactory.hasContents(newItem) ? BackpackItemFactory.loadContents(newItem) : null;

            applyBackpackResize(backpackComp, playerUuid, newItem, newBonus, equipContainer, savedContents);
            updateVisual(entity, store, ref, playerUuid, newItem);
            return BackpackItemFactory.getInstanceId(newItem);
        }

        if (lastKnownId != null) {
            clearEquippedFlagByInstanceId(armorComp, storageComp, backpackComp, hotbarComp, lastKnownId);
        }

        applyBackpackResize(backpackComp, playerUuid, null, (short) 0, null, null);
        updateVisual(entity, store, ref, playerUuid, null);
        return null;
    }

    private static void clearEquippedFlagByInstanceId(
            @Nonnull InventoryComponent.Armor armorComp,
            @Nonnull InventoryComponent.Storage storageComp,
            @Nullable InventoryComponent.Backpack backpackComp,
            @Nullable InventoryComponent.Hotbar hotbarComp,
            @Nonnull String instanceId) {
        ItemContainer[] containers = {
                armorComp.getInventory(),
                storageComp.getInventory(),
                hotbarComp != null ? hotbarComp.getInventory() : null,
                backpackComp != null ? backpackComp.getInventory() : null
        };
        for (ItemContainer container : containers) {
            if (container == null) continue;
            for (short slot = 0; slot < container.getCapacity(); slot++) {
                ItemStack candidate = container.getItemStack(slot);
                if (candidate == null || candidate.isEmpty()) continue;
                if (instanceId.equals(BackpackItemFactory.getInstanceId(candidate))) {
                    if (BackpackItemFactory.isEquipped(candidate)) {
                        container.setItemStackForSlot(slot, BackpackItemFactory.setEquipped(candidate, false));
                    }
                    return;
                }
            }
        }
    }

    private void applyBackpackResize(
            @Nullable InventoryComponent.Backpack backpackComp,
            @Nonnull String playerUuid,
            @Nullable ItemStack equippedItem,
            short newBonus,
            @Nullable ItemContainer equipContainer,
            @Nullable List<ItemStack> preloadedContents) {
        PROCESSING_CONTAINER.put(playerUuid, Boolean.TRUE);
        try {
            if (backpackComp == null) return;

            backpackComp.resize(newBonus, new ObjectArrayList<>());

            if (newBonus > 0 && equipContainer != null) {
                ItemContainer bp = backpackComp.getInventory();

                for (short slot = 0; slot < bp.getCapacity(); slot++) {
                    bp.setSlotFilter(FilterActionType.ADD, slot, (_, _, _, item) -> item == null || item.isEmpty() || getBackpackSize(item.getItemId()) == 0);
                    bp.setItemStackForSlot(slot, ItemStack.EMPTY);
                }

                List<ItemStack> contentsToRestore = preloadedContents != null
                        ? preloadedContents
                        : (equippedItem != null && BackpackItemFactory.hasContents(equippedItem) ? BackpackItemFactory.loadContents(equippedItem) : null);

                if (contentsToRestore != null) {
                    restoreContentsToBackpack(bp, contentsToRestore);
                }

                BackpackDataStorage.setLiveContents(playerUuid, getAllBackpackContents(bp));
            } else {
                BackpackDataStorage.clearActiveItem(playerUuid);
            }
        } finally {
            PROCESSING_CONTAINER.remove(playerUuid);
        }
    }

    private void updateVisual(
            @Nonnull Player entity,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull String playerUuid,
            @Nullable ItemStack equippedItem) {
        BackpackUIUpdater.updateBackpackUI(entity, ref, store);

        boolean backpackVisible = CosmeticPreferenceUtils.isBackpackVisible(store, ref);
        ModelAttachment visual = (equippedItem != null && backpackVisible) ? resolveVisual(equippedItem.getItemId()) : null;

        if (visual != null) {
            CosmeticListener.putAttachment(playerUuid, ATTACHMENT_SLOT_KEY, visual);
        } else {
            CosmeticListener.removeAttachment(playerUuid, ATTACHMENT_SLOT_KEY);
        }

        for (EquipChangeListener listener : EQUIP_CHANGE_LISTENERS) {
            listener.onEquipChange(playerUuid, entity, store, ref);
        }

        CosmeticListener.scheduleRebuild(entity, store, ref, playerUuid);
    }

    private static void persistContainerToEquippedItem(
            @Nonnull InventoryComponent.Armor armorComp,
            @Nonnull InventoryComponent.Storage storageComp,
            @Nullable InventoryComponent.Backpack backpackComp,
            @Nullable InventoryComponent.Hotbar hotbarComp,
            @Nonnull String playerUuid) {
        String equippedInstanceId = LAST_KNOWN_EQUIPPED.get(playerUuid);
        if (equippedInstanceId == null) return;

        ItemStack equippedItem = findByInstanceId(armorComp, storageComp, backpackComp, hotbarComp, equippedInstanceId);
        if (equippedItem == null) return;

        ItemContainer equippedContainer = resolveEquipContainer(armorComp, storageComp, equippedItem);
        short equippedSlot = resolveEquipSlot(armorComp, storageComp, equippedItem);
        if (equippedContainer == null || equippedSlot < 0) return;

        if (backpackComp == null) return;
        List<ItemStack> liveContents = getAllBackpackContents(backpackComp.getInventory());
        ItemStack updated = BackpackItemFactory.saveContents(equippedItem, liveContents);
        equippedContainer.setItemStackForSlot(equippedSlot, updated);
    }

    public static void syncBackpackAttachment(
            @Nonnull String playerUuid,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref) {
        if (!hasEquippedBackpack(playerUuid)) return;
        if (LAST_KNOWN_EQUIPPED.get(playerUuid) == null) return;

        InventoryComponent.Armor armorComp = store.getComponent(ref, InventoryComponent.Armor.getComponentType());
        InventoryComponent.Storage storageComp = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
        if (armorComp == null || storageComp == null) return;

        String itemId = findEquippedItemId(armorComp, storageComp);
        ModelAttachment visual = resolveVisual(itemId);
        if (visual != null) {
            CosmeticListener.putAttachment(playerUuid, ATTACHMENT_SLOT_KEY, visual);
        }
    }

    @Nullable
    private static ItemContainer resolveEquipContainer(
            @Nonnull InventoryComponent.Armor armorComp,
            @Nonnull InventoryComponent.Storage storageComp,
            @Nonnull ItemStack item) {
        ItemContainer armorContainer = armorComp.getInventory();
        ItemContainer storageContainer = storageComp.getInventory();
        String id = BackpackItemFactory.getInstanceId(item);
        if (id == null) {
            ItemStack armor = armorContainer.getItemStack(CHEST_SLOT);
            if (!ItemStack.isEmpty(armor) && item.getItemId().equals(armor.getItemId())) return armorContainer;
            ItemStack storage = storageContainer.getItemStack(STORAGE_SLOT);
            if (!ItemStack.isEmpty(storage) && item.getItemId().equals(storage.getItemId())) return storageContainer;
            return null;
        }
        ItemStack armor = armorContainer.getItemStack(CHEST_SLOT);
        if (!ItemStack.isEmpty(armor) && id.equals(BackpackItemFactory.getInstanceId(armor))) return armorContainer;
        ItemStack storage = storageContainer.getItemStack(STORAGE_SLOT);
        if (!ItemStack.isEmpty(storage) && id.equals(BackpackItemFactory.getInstanceId(storage))) return storageContainer;
        return null;
    }

    private static short resolveEquipSlot(
            @Nonnull InventoryComponent.Armor armorComp,
            @Nonnull InventoryComponent.Storage storageComp,
            @Nonnull ItemStack item) {
        ItemContainer container = resolveEquipContainer(armorComp, storageComp, item);
        if (container == null) return -1;
        return container == armorComp.getInventory() ? CHEST_SLOT : STORAGE_SLOT;
    }

    @Nullable
    private static ItemStack resolveEquipped(@Nullable ItemStack armorChest, @Nullable ItemStack storageSlot0) {
        if (!ItemStack.isEmpty(armorChest) && bonus(armorChest) > 0) return armorChest;
        if (!ItemStack.isEmpty(storageSlot0) && bonus(storageSlot0) > 0) return storageSlot0;
        return null;
    }

    @Nullable
    private static ItemStack findByInstanceId(
            @Nonnull InventoryComponent.Armor armorComp,
            @Nonnull InventoryComponent.Storage storageComp,
            @Nullable InventoryComponent.Backpack backpackComp,
            @Nullable InventoryComponent.Hotbar hotbarComp,
            @Nonnull String targetId) {
        ItemContainer[] containers = {
                armorComp.getInventory(),
                storageComp.getInventory(),
                backpackComp != null ? backpackComp.getInventory() : null,
                hotbarComp != null ? hotbarComp.getInventory() : null
        };
        for (ItemContainer container : containers) {
            if (container == null) continue;
            for (short slot = 0; slot < container.getCapacity(); slot++) {
                ItemStack candidate = container.getItemStack(slot);
                if (candidate == null || candidate.isEmpty()) continue;
                if (targetId.equals(BackpackItemFactory.getInstanceId(candidate))) return candidate;
            }
        }
        return null;
    }

    @Nonnull
    private static ItemStack ensureInstanceId(@Nonnull ItemStack item, @Nonnull ItemContainer container, short slot) {
        boolean changed = false;
        if (!BackpackItemFactory.hasInstanceId(item)) {
            item = BackpackItemFactory.createBackpackInstance(item);
            changed = true;
        }
        if (!BackpackItemFactory.isEquipped(item)) {
            item = BackpackItemFactory.setEquipped(item, true);
            changed = true;
        }
        if (changed) container.setItemStackForSlot(slot, item);
        return item;
    }

    private static short bonus(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        return getBackpackSize(stack.getItemId());
    }

    @Nonnull
    private static List<ItemStack> getAllBackpackContents(@Nonnull ItemContainer backpack) {
        List<ItemStack> contents = new ArrayList<>(backpack.getCapacity());
        for (short i = 0; i < backpack.getCapacity(); i++)
            contents.add(backpack.getItemStack(i));
        return contents;
    }

    private static void restoreContentsToBackpack(@Nonnull ItemContainer backpack, @Nonnull List<ItemStack> contents) {
        short capacity = backpack.getCapacity();
        for (int i = 0; i < contents.size() && i < capacity; i++) {
            ItemStack item = contents.get(i);
            if (item != null && !item.isEmpty()) backpack.setItemStackForSlot((short) i, item);
        }
    }

    @Nullable
    private static String findEquippedItemId(
            @Nonnull InventoryComponent.Armor armorComp,
            @Nonnull InventoryComponent.Storage storageComp) {
        ItemStack chest = armorComp.getInventory().getItemStack(CHEST_SLOT);
        if (!ItemStack.isEmpty(chest) && bonus(chest) > 0) return chest.getItemId();
        ItemStack storage = storageComp.getInventory().getItemStack(STORAGE_SLOT);
        if (!ItemStack.isEmpty(storage) && bonus(storage) > 0) return storage.getItemId();
        return null;
    }

    @Nullable
    private static ModelAttachment resolveVisual(@Nullable String itemId) {
        if (itemId == null) return null;
        String[] exact = BACKPACK_VISUALS.get(itemId);
        if (exact != null) return toAttachment(exact);
        for (Map.Entry<String, String[]> e : BACKPACK_VISUALS.entrySet()) {
            if (itemId.contains(e.getKey())) return toAttachment(e.getValue());
        }
        return null;
    }

    @Nonnull
    private static ModelAttachment toAttachment(@Nonnull String[] v) {
        return new ModelAttachment(v[0], v[1], v[2], v[3], 1.0);
    }
}