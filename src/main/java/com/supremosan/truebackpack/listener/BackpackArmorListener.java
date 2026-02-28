package com.supremosan.truebackpack.listener;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.supremosan.truebackpack.TrueBackpack;
import com.supremosan.truebackpack.config.BackpackConfig;
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

public class BackpackArmorListener extends RefSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final String ATTACHMENT_SLOT_KEY = "truebackpack:backpack";

    private static final Map<String, Short> BACKPACK_SIZES = new ConcurrentHashMap<>();
    private static final Map<String, String[]> BACKPACK_VISUALS = new LinkedHashMap<>();

    public interface EquipChangeListener {
        void onEquipChange(@Nonnull String playerUuid, @Nonnull Player player,
                           @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref);
    }

    private static final List<EquipChangeListener> EQUIP_CHANGE_LISTENERS = new CopyOnWriteArrayList<>();

    private static final short CHEST_SLOT = 1;
    private static final short STORAGE_SLOT = 0;

    private static final Map<String, String> LAST_KNOWN_EQUIPPED = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> PROCESSING_EQUIP = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> PROCESSING_CONTAINER = new ConcurrentHashMap<>();
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

    public static void registerBackpack(@Nonnull String baseItemId,
                                        short sizeBonus,
                                        @Nonnull String modelPath,
                                        @Nonnull String texturePath) {
        BACKPACK_SIZES.put(baseItemId, sizeBonus);
        BACKPACK_VISUALS.put(baseItemId, new String[]{modelPath, texturePath, "", ""});
    }

    public static void addEquipChangeListener(@Nonnull EquipChangeListener listener) {
        EQUIP_CHANGE_LISTENERS.add(listener);
    }

    public static boolean hasEquippedBackpack(@Nonnull String playerUuid) {
        return LAST_KNOWN_EQUIPPED.containsKey(playerUuid);
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
        UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComp == null) return;

        String playerUuid = uuidComp.getUuid().toString();

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            persistContainerToEquippedItem(player.getInventory(), playerUuid);
        }

        LAST_KNOWN_EQUIPPED.remove(playerUuid);
        PROCESSING_EQUIP.remove(playerUuid);
        PROCESSING_CONTAINER.remove(playerUuid);
        BackpackDataStorage.clearActiveItem(playerUuid);
        CosmeticListener.onPlayerLeave(playerUuid);
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

        if (CosmeticListener.isProcessing()) return;

        Inventory inv = entity.getInventory();
        ItemContainer changed = event.getItemContainer();
        ItemContainer armor = inv.getArmor();
        ItemContainer storage = inv.getStorage();
        ItemContainer backpack = inv.getBackpack();

        boolean isEquipContainer = changed == armor || changed == storage;
        boolean isBackpackContainer = changed == backpack;

        if (!isEquipContainer && !isBackpackContainer) return;

        if (isBackpackContainer) {
            handleBackpackContainerChange(inv, playerUuid);
            return;
        }

        handleEquipContainerChange(entity, ref, store, inv, playerUuid);
    }



    private void handleBackpackContainerChange(@Nonnull Inventory inv,
                                               @Nonnull String playerUuid) {
        if (Boolean.TRUE.equals(PROCESSING_CONTAINER.get(playerUuid))) return;

        String equippedInstanceId = LAST_KNOWN_EQUIPPED.get(playerUuid);
        if (equippedInstanceId == null) return;

        PROCESSING_CONTAINER.put(playerUuid, Boolean.TRUE);
        try {
            ItemStack equippedItem = findByInstanceId(inv, equippedInstanceId);
            if (equippedItem == null) return;

            ItemContainer equippedContainer = resolveEquipContainer(inv, equippedItem);
            short equippedSlot = resolveEquipSlot(inv, equippedItem);
            if (equippedContainer == null || equippedSlot < 0) return;

            List<ItemStack> liveContents = getAllBackpackContents(inv.getBackpack());
            ItemStack updated = BackpackItemFactory.saveContents(equippedItem, liveContents);
            equippedContainer.setItemStackForSlot(equippedSlot, updated);

            BackpackDataStorage.setLiveContents(playerUuid, liveContents);
        } finally {
            PROCESSING_CONTAINER.remove(playerUuid);
        }
    }

    private void handleEquipContainerChange(@Nonnull LivingEntity entity,
                                            @Nonnull Ref<EntityStore> ref,
                                            @Nonnull Store<EntityStore> store,
                                            @Nonnull Inventory inv,
                                            @Nonnull String playerUuid) {
        if (Boolean.TRUE.equals(PROCESSING_EQUIP.get(playerUuid))) return;

        ItemStack liveArmor = inv.getArmor().getItemStack(CHEST_SLOT);
        ItemStack liveStorage = inv.getStorage().getItemStack(STORAGE_SLOT);

        ItemStack currentEquipped = resolveEquipped(liveArmor, liveStorage);
        String lastKnownId = LAST_KNOWN_EQUIPPED.get(playerUuid);
        String currentId = currentEquipped != null
                ? BackpackItemFactory.getInstanceId(currentEquipped) : null;

        boolean currentIsNew = currentEquipped != null && currentId == null;
        if (!currentIsNew && Objects.equals(currentId, lastKnownId)) return;

        ItemStack previousItem = lastKnownId != null ? findByInstanceId(inv, lastKnownId) : null;
        boolean wasDropped = lastKnownId != null && previousItem == null && currentEquipped == null;
        short oldBonus = previousItem != null ? bonus(previousItem) : (wasDropped ? (short) 1 : (short) 0);
        short newBonus = bonus(currentEquipped);

        if (oldBonus == 0 && newBonus == 0) {
            LAST_KNOWN_EQUIPPED.remove(playerUuid);
            return;
        }

        PROCESSING_EQUIP.put(playerUuid, Boolean.TRUE);
        try {
            String finalInstanceId = processEquipChange(
                    entity, ref, store, inv, playerUuid,
                    currentEquipped, newBonus);

            if (finalInstanceId != null) {
                LAST_KNOWN_EQUIPPED.put(playerUuid, finalInstanceId);
            } else {
                LAST_KNOWN_EQUIPPED.remove(playerUuid);
            }
        } finally {
            PROCESSING_EQUIP.remove(playerUuid);
        }
    }

    @Nullable
    private String processEquipChange(@Nonnull LivingEntity entity,
                                      @Nonnull Ref<EntityStore> ref,
                                      @Nonnull Store<EntityStore> store,
                                      @Nonnull Inventory inv,
                                      @Nonnull String playerUuid,
                                      @Nullable ItemStack newItem,
                                      short newBonus) {
        String lastKnownId = LAST_KNOWN_EQUIPPED.get(playerUuid);

        if (newBonus > 0 && newItem != null) {
            ItemContainer equipContainer = resolveEquipContainer(inv, newItem);
            short equipSlot = resolveEquipSlot(inv, newItem);

            if (equipContainer == null || equipSlot < 0) {
                equipContainer = bonus(inv.getArmor().getItemStack(CHEST_SLOT)) > 0
                        ? inv.getArmor() : inv.getStorage();
                equipSlot = equipContainer == inv.getArmor() ? CHEST_SLOT : STORAGE_SLOT;
            }

            if (lastKnownId != null) {
                clearEquippedFlagByInstanceId(inv, lastKnownId);
            }

            newItem = ensureInstanceId(newItem, equipContainer, equipSlot);

            List<ItemStack> savedContents = BackpackItemFactory.hasContents(newItem)
                    ? BackpackItemFactory.loadContents(newItem)
                    : null;

            applyBackpackResize(entity, ref, store, inv, playerUuid,
                    newItem, newBonus, equipContainer, equipSlot, savedContents);
            updateVisual(entity, store, ref, playerUuid, newItem);
            return BackpackItemFactory.getInstanceId(newItem);
        }

        if (lastKnownId != null) {
            clearEquippedFlagByInstanceId(inv, lastKnownId);
        }

        applyBackpackResize(entity, ref, store, inv, playerUuid,
                null, (short) 0, null, (short) 0, null);
        updateVisual(entity, store, ref, playerUuid, null);
        return null;
    }

    private static void clearEquippedFlagByInstanceId(@Nonnull Inventory inv,
                                                      @Nonnull String instanceId) {
        ItemContainer[] containers = {
                inv.getArmor(), inv.getStorage(), inv.getHotbar(), inv.getBackpack()
        };
        for (ItemContainer container : containers) {
            if (container == null) continue;
            for (short slot = 0; slot < container.getCapacity(); slot++) {
                ItemStack candidate = container.getItemStack(slot);
                if (candidate == null || candidate.isEmpty()) continue;
                if (instanceId.equals(BackpackItemFactory.getInstanceId(candidate))) {
                    if (BackpackItemFactory.isEquipped(candidate)) {
                        container.setItemStackForSlot(slot,
                                BackpackItemFactory.setEquipped(candidate, false));
                    }
                    return;
                }
            }
        }
    }

    private void applyBackpackResize(@Nonnull LivingEntity entity,
                                     @Nonnull Ref<EntityStore> ref,
                                     @Nonnull Store<EntityStore> store,
                                     @Nonnull Inventory inv,
                                     @Nonnull String playerUuid,
                                     @Nullable ItemStack equippedItem,
                                     short newBonus,
                                     @Nullable ItemContainer equipContainer,
                                     short equipSlot,
                                     @Nullable List<ItemStack> preloadedContents) {
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

            List<ItemStack> contentsToRestore = preloadedContents != null
                    ? preloadedContents
                    : (equippedItem != null && BackpackItemFactory.hasContents(equippedItem)
                    ? BackpackItemFactory.loadContents(equippedItem)
                    : null);

            if (contentsToRestore != null) {
                restoreContentsToBackpack(bp, contentsToRestore);
            }

            BackpackDataStorage.setLiveContents(playerUuid, getAllBackpackContents(bp));
        } else {
            BackpackDataStorage.clearActiveItem(playerUuid);
        }

        BackpackUIUpdater.updateBackpackUI(entity, ref, store);
    }

    private void updateVisual(@Nonnull LivingEntity entity,
                              @Nonnull Store<EntityStore> store,
                              @Nonnull Ref<EntityStore> ref,
                              @Nonnull String playerUuid,
                              @Nullable ItemStack equippedItem) {
        if (!(entity instanceof Player player)) return;

        boolean backpackVisible = CosmeticPreferenceUtils.isBackpackVisible(store, ref);
        ModelAttachment visual = (equippedItem != null && backpackVisible)
                ? resolveVisual(equippedItem.getItemId()) : null;

        if (visual != null) {
            CosmeticListener.putAttachment(playerUuid, ATTACHMENT_SLOT_KEY, visual);
        } else {
            CosmeticListener.removeAttachment(playerUuid, ATTACHMENT_SLOT_KEY);
        }

        for (EquipChangeListener listener : EQUIP_CHANGE_LISTENERS) {
            listener.onEquipChange(playerUuid, player, store, ref);
        }

        CosmeticListener.scheduleRebuild(player, store, ref, playerUuid);
    }

    private void persistContainerToEquippedItem(@Nonnull Inventory inv,
                                                @Nonnull String playerUuid) {
        String equippedInstanceId = LAST_KNOWN_EQUIPPED.get(playerUuid);
        if (equippedInstanceId == null) return;

        ItemStack equippedItem = findByInstanceId(inv, equippedInstanceId);
        if (equippedItem == null) return;

        ItemContainer equippedContainer = resolveEquipContainer(inv, equippedItem);
        short equippedSlot = resolveEquipSlot(inv, equippedItem);
        if (equippedContainer == null || equippedSlot < 0) return;

        List<ItemStack> liveContents = getAllBackpackContents(inv.getBackpack());
        ItemStack updated = BackpackItemFactory.saveContents(equippedItem, liveContents);
        equippedContainer.setItemStackForSlot(equippedSlot, updated);
    }

    public static void syncBackpackAttachment(@Nonnull String playerUuid,
                                              @Nonnull Player player,
                                              @Nonnull Store<EntityStore> store,
                                              @Nonnull Ref<EntityStore> ref) {
        if (!hasEquippedBackpack(playerUuid)) return;
        String equippedId = LAST_KNOWN_EQUIPPED.get(playerUuid);
        if (equippedId == null) return;
        ModelAttachment visual = resolveVisual(findEquippedItemId(player));
        if (visual != null) {
            CosmeticListener.putAttachment(playerUuid, ATTACHMENT_SLOT_KEY, visual);
        }
    }

    private static void dropBackpack(@Nonnull Ref<EntityStore> ref,
                                     @Nonnull Store<EntityStore> store,
                                     @Nonnull ItemStack item) {
        if (BUFFER == null) {
            LOGGER.atWarning().log("[TrueBackpack] Cannot drop backpack — CommandBuffer not available.");
            return;
        }

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        HeadRotation rotation = store.getComponent(ref, HeadRotation.getComponentType());
        if (transform == null || rotation == null) return;

        Vector3d pos = transform.getPosition().clone().add(0, 1, 0);
        Vector3f rot = rotation.getRotation().clone();

        List<ItemStack> toDrop = List.of(item);
        Holder<EntityStore>[] drops = ItemComponent.generateItemDrops(store, toDrop, pos, rot);
        BUFFER.addEntities(drops, AddReason.SPAWN);
    }

    @Nullable
    private static ItemContainer resolveEquipContainer(@Nonnull Inventory inv,
                                                       @Nonnull ItemStack item) {
        String id = BackpackItemFactory.getInstanceId(item);
        if (id == null) {
            ItemStack armor = inv.getArmor().getItemStack(CHEST_SLOT);
            if (!ItemStack.isEmpty(armor) && item.getItemId().equals(armor.getItemId())) return inv.getArmor();
            ItemStack storage = inv.getStorage().getItemStack(STORAGE_SLOT);
            if (!ItemStack.isEmpty(storage) && item.getItemId().equals(storage.getItemId())) return inv.getStorage();
            return null;
        }
        ItemStack armor = inv.getArmor().getItemStack(CHEST_SLOT);
        if (!ItemStack.isEmpty(armor) && id.equals(BackpackItemFactory.getInstanceId(armor))) return inv.getArmor();
        ItemStack storage = inv.getStorage().getItemStack(STORAGE_SLOT);
        if (!ItemStack.isEmpty(storage) && id.equals(BackpackItemFactory.getInstanceId(storage))) return inv.getStorage();
        return null;
    }

    private static short resolveEquipSlot(@Nonnull Inventory inv,
                                          @Nonnull ItemStack item) {
        ItemContainer container = resolveEquipContainer(inv, item);
        if (container == null) return -1;
        return container == inv.getArmor() ? CHEST_SLOT : STORAGE_SLOT;
    }

    @Nullable
    private static ItemStack resolveEquipped(@Nullable ItemStack armorChest,
                                             @Nullable ItemStack storageSlot0) {
        if (!ItemStack.isEmpty(armorChest) && bonus(armorChest) > 0) return armorChest;
        if (!ItemStack.isEmpty(storageSlot0) && bonus(storageSlot0) > 0) return storageSlot0;
        return null;
    }

    @Nullable
    private static ItemStack findByInstanceId(@Nonnull Inventory inv,
                                              @Nonnull String targetId) {
        ItemContainer[] containers = {
                inv.getArmor(), inv.getStorage(), inv.getBackpack(), inv.getHotbar()
        };
        for (ItemContainer container : containers) {
            if (container == null) continue;
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
    private static ItemStack ensureInstanceId(@Nonnull ItemStack item,
                                              @Nonnull ItemContainer container,
                                              short slot) {
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

    private static void restoreContentsToBackpack(@Nonnull ItemContainer backpack,
                                                  @Nonnull List<ItemStack> contents) {
        short capacity = backpack.getCapacity();
        for (int i = 0; i < contents.size() && i < capacity; i++) {
            ItemStack item = contents.get(i);
            if (item != null && !item.isEmpty())
                backpack.setItemStackForSlot((short) i, item);
        }
    }

    private static String findEquippedItemId(@Nonnull Player player) {
        Inventory inv = player.getInventory();
        ItemStack chest = inv.getArmor().getItemStack(CHEST_SLOT);
        if (!ItemStack.isEmpty(chest) && bonus(chest) > 0) return chest.getItemId();
        ItemStack storage = inv.getStorage().getItemStack(STORAGE_SLOT);
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