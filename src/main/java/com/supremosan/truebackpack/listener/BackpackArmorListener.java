package com.supremosan.truebackpack;

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
import com.supremosan.truebackpack.cosmetic.CosmeticListener;
import com.supremosan.truebackpack.helpers.*;
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
    private static final Map<String, Boolean> PROCESSING = new ConcurrentHashMap<>();

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

    public static void flushLiveContentsToItem(@Nonnull Ref<EntityStore> ref,
                                               @Nonnull Store<EntityStore> store,
                                               @Nonnull String playerUuid) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            LOGGER.atInfo().log("[TrueBackpack] Player component missing for %s", playerUuid);
            return;
        }

        Inventory inv = player.getInventory();

        ItemStack chest = inv.getArmor().getItemStack(CHEST_SLOT);
        ItemStack storage0 = inv.getStorage().getItemStack(STORAGE_SLOT);

        ItemStack equipped = null;
        ItemContainer equippedContainer = null;
        short equippedSlot = -1;

        if (!ItemStack.isEmpty(chest) && bonus(chest) > 0 && BackpackItemFactory.isEquipped(chest)) {
            equipped = chest;
            equippedContainer = inv.getArmor();
            equippedSlot = CHEST_SLOT;
        } else if (!ItemStack.isEmpty(storage0) && bonus(storage0) > 0 && BackpackItemFactory.isEquipped(storage0)) {
            equipped = storage0;
            equippedContainer = inv.getStorage();
            equippedSlot = STORAGE_SLOT;
        }

        if (equipped == null) {
            LOGGER.atWarning().log("[TrueBackpack] No equipped backpack found for player %s", playerUuid);
            return;
        }

        ItemContainer backpackContainer = inv.getBackpack();
        List<ItemStack> liveContents = new ArrayList<>(backpackContainer.getCapacity());
        for (short i = 0; i < backpackContainer.getCapacity(); i++) {
            liveContents.add(backpackContainer.getItemStack(i));
        }

        boolean hasItems = liveContents.stream().anyMatch(i -> i != null && !i.isEmpty());
        if (!hasItems) {
            LOGGER.atInfo().log("[TrueBackpack] Backpack container is empty for player %s", playerUuid);
            return;
        }

        ItemStack withContents = BackpackItemFactory.saveContents(equipped, liveContents);
        ItemStack unequipped = BackpackItemFactory.setEquipped(withContents, false);
        equippedContainer.setItemStackForSlot(equippedSlot, unequipped);
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
            flushLiveContentsToItem(ref, store, playerUuid);
        }

        LAST_KNOWN_EQUIPPED.remove(playerUuid);
        PROCESSING.remove(playerUuid);
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

        if (Boolean.TRUE.equals(PROCESSING.get(playerUuid))) return;
        if (CosmeticListener.isProcessing()) return;

        Inventory inv = entity.getInventory();
        ItemContainer armor = inv.getArmor();
        ItemContainer storage = inv.getStorage();
        ItemContainer changed = event.getItemContainer();

        if (changed != armor && changed != storage) return;

        ItemStack liveArmor = armor.getItemStack(CHEST_SLOT);
        ItemStack liveStorage = storage.getItemStack(STORAGE_SLOT);

        ItemStack currentEquipped = resolveEquipped(liveArmor, liveStorage);
        String lastKnownId = LAST_KNOWN_EQUIPPED.get(playerUuid);
        String currentId = instanceId(currentEquipped);

        boolean currentIsNew = currentEquipped != null && currentId == null;
        if (!currentIsNew && Objects.equals(currentId, lastKnownId)) return;

        ItemStack previousEquipped = lastKnownId != null
                ? findByInstanceId(inv, lastKnownId) : null;

        short oldBonus = bonus(previousEquipped);
        short newBonus = bonus(currentEquipped);

        if (oldBonus == 0 && newBonus == 0) {
            LAST_KNOWN_EQUIPPED.remove(playerUuid);
            return;
        }

        ItemContainer equipContainer = bonus(liveArmor) > 0 ? armor : storage;
        short equipSlot = equipContainer == armor ? CHEST_SLOT : STORAGE_SLOT;

        PROCESSING.put(playerUuid, Boolean.TRUE);
        try {
            String finalInstanceId = processEquipChange(
                    entity, ref, store, inv, playerUuid,
                    previousEquipped, currentEquipped,
                    oldBonus, newBonus,
                    equipContainer, equipSlot);

            if (finalInstanceId != null) {
                LAST_KNOWN_EQUIPPED.put(playerUuid, finalInstanceId);
            } else {
                LAST_KNOWN_EQUIPPED.remove(playerUuid);
            }
        } finally {
            PROCESSING.remove(playerUuid);
        }
    }

    @Nullable
    private String processEquipChange(
            @Nonnull LivingEntity entity,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull Inventory inv,
            @Nonnull String playerUuid,
            @Nullable ItemStack oldItem,
            @Nullable ItemStack newItem,
            short oldBonus,
            short newBonus,
            @Nullable ItemContainer equipContainer,
            short equipSlot) {

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
            List<ItemStack> savedContents = BackpackItemFactory.hasContents(newItem)
                    ? BackpackItemFactory.loadContents(newItem)
                    : null;

            newItem = resizeAndRestore(entity, ref, store, inv, playerUuid,
                    newItem, newBonus, equipContainer, equipSlot, savedContents);
            updateVisual(entity, store, ref, playerUuid, newItem);
            return instanceId(newItem);
        }

        if (newBonus > 0 && newItem != null && equipContainer != null) {
            newItem = ensureEquipped(newItem, equipContainer, equipSlot);
            List<ItemStack> savedContents = BackpackItemFactory.hasContents(newItem)
                    ? BackpackItemFactory.loadContents(newItem)
                    : null;

            newItem = resizeAndRestore(entity, ref, store, inv, playerUuid,
                    newItem, newBonus, equipContainer, equipSlot, savedContents);
            updateVisual(entity, store, ref, playerUuid, newItem);
            return instanceId(newItem);
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
                null, (short) 0, null, (short) 0, null);
        updateVisual(entity, store, ref, playerUuid, null);
        return null;
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

    private void dropSavedBackpack(@Nonnull Ref<EntityStore> ref,
                                   @Nonnull Store<EntityStore> store,
                                   @Nonnull ItemStack savedItem) {
        if (BUFFER == null) {
            LOGGER.atWarning().log("[TrueBackpack] Cannot drop backpack — CommandBuffer not available.");
            return;
        }

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        HeadRotation rotation = store.getComponent(ref, HeadRotation.getComponentType());
        if (transform == null || rotation == null) return;

        Vector3d pos = transform.getPosition().clone().add(0, 1, 0);
        Vector3f rot = rotation.getRotation().clone();

        List<ItemStack> toDrop = List.of(savedItem);
        Holder<EntityStore>[] drops = ItemComponent.generateItemDrops(store, toDrop, pos, rot);
        BUFFER.addEntities(drops, AddReason.SPAWN);
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
            CosmeticListener.putAttachment(playerUuid, "truebackpack:backpack", visual);
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
    private ItemStack resizeAndRestore(
            @Nonnull LivingEntity entity,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull Inventory inv,
            @Nonnull String playerUuid,
            @Nullable ItemStack newItem,
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

            ItemStack fromContainer = equipContainer.getItemStack(equipSlot);
            if (fromContainer != null && !fromContainer.isEmpty()) {
                newItem = fromContainer;
            }

            List<ItemStack> contentsToRestore = null;
            boolean containerHasItems =
                    getAllBackpackContents(bp).stream()
                            .anyMatch(i -> i != null && !i.isEmpty());

            if (!containerHasItems) {
                if (preloadedContents != null) {
                    contentsToRestore = preloadedContents;
                } else if (newItem != null && BackpackItemFactory.hasContents(newItem)) {
                    contentsToRestore = BackpackItemFactory.loadContents(newItem);
                }
            }

            if (newItem != null && contentsToRestore != null) {
                restoreContentsToBackpack(bp, contentsToRestore);

                ItemStack cleaned = BackpackItemFactory.setEquipped(
                        BackpackItemFactory.clearContents(newItem), true);
                equipContainer.setItemStackForSlot(equipSlot, cleaned);
                newItem = cleaned;
            }

            BackpackDataStorage.setLiveContents(playerUuid, getAllBackpackContents(bp));
            if (equipContainer != inv.getArmor() || newItem == null) {
                BackpackDataStorage.clearActiveItem(playerUuid);
            }

        } else {
            BackpackDataStorage.clearActiveItem(playerUuid);
        }

        BackpackUIUpdater.updateBackpackUI(entity, ref, store);
        return newItem;
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
    private static ItemStack ensureEquipped(@Nonnull ItemStack item,
                                            @Nonnull ItemContainer container,
                                            short slot) {
        boolean needsId = !BackpackItemFactory.hasInstanceId(item);
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
                return false;
            }
        }
        return true;
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

    private static void restoreContentsToBackpack(@Nonnull ItemContainer backpack,
                                                  @Nonnull List<ItemStack> contents) {
        short capacity = backpack.getCapacity();
        for (int i = 0; i < contents.size() && i < capacity; i++) {
            ItemStack item = contents.get(i);
            if (item != null && !item.isEmpty())
                backpack.setItemStackForSlot((short) i, item);
        }
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